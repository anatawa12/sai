package com.anatawa12.sai.stia

import com.anatawa12.sai.Node
import com.anatawa12.sai.Token
import com.anatawa12.sai.ast.*

/**
 * Phi function is now expressed as shown below:
 *
 * ```
 * TARGET
 *  `--Name(new variable id)
 *      +--Name (old variable id 1)
 *      |   `-jumpFrom: GOTO (if jumped from this instruction, this variable is used)
 *      `--Name (old variable id 2...)
 *          `-jumpFrom: GOTO (if jumped from this instruction, this variable is used)
 * ```
 */
class StaticSingleAssignGenerators {
    private val targets = mutableListOf<Node>()
    private val finallies = mutableListOf<Node>()

    fun process(node: ScriptNode) {
        // TODO: global scope
        var scope = VariablesScope(null, emptyMap())
        scope = node.symbolTable
            ?.let { VariablesScope(scope, it.values, node) }
            ?: scope

        statementsBlock(node, scope)
        markUnreachable()
        replaceSSAGenInfoToNormalInfo()
        removeFinallyInfos()
        optimizeSSAPhis() // Nameのidを統合
    }

    //region processNode

    private sealed class ProcessResult {
        object Continue : ProcessResult()
        object Jumped : ProcessResult()
    }

    private fun processNode(node: Node, scope: VariablesScope, afterJump: Boolean = false): ProcessResult {
        when (node.type) {
            Token.BITOR,
            Token.BITXOR,
            Token.BITAND,
            Token.EQ,
            Token.NE,
            Token.LT,
            Token.LE,
            Token.GT,
            Token.GE,
            Token.LSH,
            Token.RSH,
            Token.URSH,
            Token.ADD,
            Token.SUB,
            Token.MUL,
            Token.DIV,
            Token.MOD,
            Token.NOT,
            Token.BITNOT,
            Token.POS, // + expr
            Token.NEG, // - expr
            Token.NEW,
            Token.DELPROP, // delete expr
            Token.TYPEOF, // typeof expr
            Token.GETPROP,
            Token.GETPROPNOWARN,
            Token.SETPROP,
            Token.GETELEM,
            Token.SETELEM,
            Token.CALL,
            Token.NUMBER, // literal
            Token.STRING,
            Token.NULL,
            Token.THIS,
            Token.FALSE,
            Token.TRUE,
            Token.SHEQ, // shallow ===
            Token.SHNE, // shallow !==
            Token.REGEXP, // regexp literal
            Token.THROW,
            Token.RETHROW,
            Token.IN,
            Token.INSTANCEOF,
            Token.LOCAL_LOAD,
            Token.GETVAR,
            Token.SETVAR,
            Token.ARRAYLIT, // array literal
            Token.OBJECTLIT, // object literal
            Token.VOID, // void expr;
            Token.EMPTY, // ; // empty statement
            Token.EXPR_VOID, // expression statement
            Token.EXPR_RESULT, // expression statement for root script // TODO maybe unsupported because for script
            Token.TYPEOFNAME, // typeof expr
            Token.USE_STACK, // use the value on the stack,
            Token.SETPROP_OP, // a.b op= c
            Token.SETELEM_OP, // a.[b] op= c
            Token.LOCAL_BLOCK, // contextual local variable (hidden) // TODO
            Token.COMMA, // expr, expr, expr....
            Token.OR, // ||
            Token.AND, // &&
            Token.INC, // ++ expr or expr ++
            Token.DEC, // -- expr or expr --
            -> {
                // simple expr default
                for (child in node) {
                    when (processNode(child, scope)) {
                        ProcessResult.Continue -> {
                        }
                        ProcessResult.Jumped -> return ProcessResult.Jumped
                    }
                }
                return ProcessResult.Continue
            }

            Token.RETURN, // void
            -> {
                val returnValue = node.firstChild.nullable()
                if (returnValue != null)
                    processNode(returnValue, scope)
                return ProcessResult.Jumped
            }

            // jumping instructions
            Token.GOTO,
            -> {
                node as Jump
                processJump(node.target, scope, node)
                return ProcessResult.Jumped
            }
            Token.JSR, // TODO: return from finally
            -> {
                node as Jump
                val target = node.target
                val finally = target.next
                if (finally.type != Token.FINALLY)
                    unsupported("jsr to not just before finally block")
                val finallyInfo = finally.finallyInternalInfo

                finallies.add(node)

                // jsr -> node: as a jump insn
                processJump(target, scope, node)

                // finally -> jsr: as a target insn
                targets.add(node)
                val newSnapshot = scope.updateAll(producer = node)
                node.ssaGenTargetInfo.atTargetSnapshot = newSnapshot

                // do something for jumping of finally
                if (finallyInfo.snapshot != null) {
                    processJump(target = node, snapshot = finallyInfo.snapshot!!, finally)
                } else {
                    finallyInfo.returningTo?.add(node)
                }

                return ProcessResult.Continue
            }
            Token.IFEQ,
            Token.IFNE,
            -> {
                node as Jump
                val condition = node.single()
                when (processNode(condition, scope)) {
                    ProcessResult.Continue -> {
                    }
                    ProcessResult.Jumped -> return ProcessResult.Jumped
                }
                processJump(node.target, scope, node)
                // ScriptRuntime.newErrorForThrowable
                return ProcessResult.Continue
            }
            Token.BREAK,
            Token.CONTINUE,
            -> {
                node as Jump
                val loop = node.jumpStatement
                val target = if (node.type == Token.BREAK) loop.target else loop.`continue`
                processJump(target, scope, node)
                return ProcessResult.Jumped
            }

            // switch statement
            Token.SWITCH,
            -> {
                node as Jump
                val expr = node.first()
                val cases = node.drop(1)
                when (processNode(expr, scope)) {
                    ProcessResult.Continue -> {
                    }
                    ProcessResult.Jumped -> return ProcessResult.Jumped
                }
                for (case in cases) {
                    check(case.type == Token.CASE)
                    case as Jump
                    processJump(case.target, scope, node)
                }
                return ProcessResult.Continue
            }

            Token.TARGET, // TODO: CHECK
            -> {
                targets.add(node)
                val curSnapshot = scope.createSnapshot()
                val newSnapshot = scope.updateAll(producer = node)
                if (!afterJump) node.ssaGenTargetInfo.addJumpingFrom(curSnapshot, null)
                node.ssaGenTargetInfo.atTargetSnapshot = newSnapshot
                return ProcessResult.Continue
            }

            // statements or expressions block
            Token.LABEL, // block
                //Token.WITH, // unsuppoted
            -> {
                return statementsBlock(node, scope)
            }

            // scope block
            Token.BLOCK, //Token.ARRAYCOMP, unsupported
            Token.LOOP,
            -> return processScope(node, scope)

            Token.CASE, // case label(?)
            -> unsupported("case must be in switch")

            Token.HOOK, // condition ? then : else
            -> {
                val (condition, ifTrue, ifFalse) = node.asTriple()
                when (processNode(condition, scope)) {
                    ProcessResult.Continue -> {
                    }
                    ProcessResult.Jumped -> return ProcessResult.Jumped
                }
                val ifTrueResult = processNode(ifTrue, scope)
                val ifFalseResult = processNode(ifFalse, scope)
                if (ifTrueResult == ProcessResult.Jumped
                    && ifFalseResult == ProcessResult.Jumped
                )
                    return ProcessResult.Jumped
                return ProcessResult.Continue
            }

            Token.TRY, // try {} catch {} finally {}
            -> {
                node as Jump
                val snapshot = scope.createSnapshot()
                val catchTarget = node.target.nullable()
                val finallyTarget = node.finally.nullable()

                var isReturned = false
                for (child in node) {
                    if (isReturned && child.type != Token.TARGET)
                        continue
                    val isReturnedOld = isReturned
                    isReturned = false
                    if (child === catchTarget || child === finallyTarget)
                        child.ssaGenTargetInfo.addJumpingFroms(scope.allVariableVersions(since = snapshot), node)
                    when (processNode(child, scope, isReturnedOld)) {
                        ProcessResult.Continue -> {
                        }
                        ProcessResult.Jumped -> {
                            isReturned = true
                        }
                    }
                }

                if (isReturned) return ProcessResult.Jumped
                return ProcessResult.Continue
            }
            Token.FINALLY, // TODO
            -> {
                // TODO: LOCAL_BLOCK
                val info = node.finallyInternalInfo

                when (statementsBlock(node, scope)) {
                    ProcessResult.Jumped -> {
                        // never reached
                        info.returningTo = null
                        return ProcessResult.Jumped
                    }
                    ProcessResult.Continue -> {
                    }
                }

                val snapshot = scope.createSnapshot()
                info.snapshot = snapshot
                for (returningTo in info.returningTo!!) {
                    processJump(returningTo, snapshot, node)
                }
                info.returningTo = null
                return ProcessResult.Jumped
            }

            Token.SETNAME,
            -> {
                // TODO: global scopee
                val (variable, expr) = node.asPair()
                variable as Name
                scope.variable(variable.identifier)?.makeNext(node)
                    ?.let { variable.varId = it }
                return processNode(expr, scope)
            }
            Token.NAME, // TODO
            -> {
                node as Name
                scope.variable(node.identifier)?.getCurrent()
                    ?.let { node.varId = it }
                // nop
                return ProcessResult.Continue
            }
            Token.BINDNAME,
            -> unsupported("BINDNAME")

            Token.VAR, // TODO: CHECK
            Token.LET, // TODO: CHECK
            Token.CONST, // TODO: CHECK
            -> {
                for (name in node) {
                    name as Name
                    val variable = scope.variableExactly(name.identifier)
                    name.varId = variable.makeNext(name)
                    if (node.type != Token.VAR)
                        variable.enabledSinceHere()
                }
                return ProcessResult.Continue
            }

            Token.THISFN, // reference to this function
            -> {
                // nop
                return ProcessResult.Continue
            }

            Token.FUNCTION, // literal or root definition
            -> {
                //TODO("reference maybe")
                return ProcessResult.Continue
            }

            Token.ENTERWITH,
            Token.LEAVEWITH,
            Token.WITH,
            Token.WITHEXPR,
            -> unsupported("with")
            Token.STRICT_SETNAME,
            -> unsupported("no difference between")
            Token.DEBUGGER,
            -> unsupported("debugger")
            Token.METHOD,
            -> unsupported("method")
            Token.CONVERT_EXCEPTION,
            -> unsupported("debugger")
            Token.SCRIPT, // TODO: BLOCK
            -> unsupported("root script is not supported")
            Token.CATCH_SCOPE, // TODO
            -> unsupported("catch scope")
            Token.ENUM_INIT_KEYS,
            Token.ENUM_INIT_VALUES,
            Token.ENUM_INIT_ARRAY,
            Token.ENUM_INIT_VALUES_IN_ORDER,
            Token.ENUM_NEXT,
            Token.ENUM_ID,
            -> unsupported("enumeration loops")
            Token.GET_REF,
            Token.SET_REF,
            Token.DEL_REF,
            Token.REF_CALL,
            Token.REF_SPECIAL,
            -> unsupported("references")
            Token.YIELD,
            Token.YIELD_STAR,
            -> unsupported("generators")
            Token.DEFAULTNAMESPACE,
            Token.ESCXMLATTR,
            Token.ESCXMLTEXT,
            Token.REF_MEMBER,
            Token.REF_NS_MEMBER,
            Token.REF_NAME,
            Token.REF_NS_NAME,
            -> unsupported("xml")
            Token.SET_REF_OP, // TODO: not supported
            -> unsupported("reference")
            Token.DOTQUERY,
            -> unsupported("xml")
            Token.GET,
            Token.SET,
            -> unsupported("get and set in object literal")
            Token.SETCONST,
            Token.SETCONSTVAR,
            -> unsupported("deconstructing const assign")
            Token.ARRAYCOMP,
                // see https://developer.mozilla.org/docs/Web/JavaScript/Reference/Operators/Array_comprehensions
            -> unsupported("array comprehension expression")
            Token.LETEXPR,//TODO:CHECK
            -> unsupported("deconstructing const assign")
            Token.RETURN_RESULT,
            Token.TO_OBJECT, // double to object wrapping
            Token.TO_DOUBLE, // similar to Number(expr)
            -> unsupported("transformed insn")
            //Token.SEMI,
            //Token.LB,
            //Token.RB,
            //Token.LC,
            //Token.RC,
            //Token.LP,
            //Token.RP,
            //Token.ASSIGN,
            //Token.ASSIGN_BITOR,
            //Token.ASSIGN_BITXOR,
            //Token.ASSIGN_BITAND,
            //Token.ASSIGN_LSH,
            //Token.ASSIGN_RSH,
            //Token.ASSIGN_URSH,
            //Token.ASSIGN_ADD,
            //Token.ASSIGN_SUB,
            //Token.ASSIGN_MUL,
            //Token.ASSIGN_DIV,
            //Token.ASSIGN_MOD,
            //Token.COLON,
            //Token.DOT,
            //Token.EXPORT,
            //Token.IMPORT,
            //Token.IF,
            //Token.ELSE,
            //Token.DEFAULT,
            //Token.WHILE,
            //Token.DO,
            //Token.FOR,
            //Token.CATCH,
            //Token.RESERVED,
            //Token.DOTDOT,
            //Token.COLONCOLON,
            //Token.XML,
            //Token.XMLATTR,
            //Token.XMLEND,
            //Token.COMMENT,
            //Token.GENEXPR,
            //Token.ARROW,
            else
            -> unsupported("unsupported token: ${node.type}")
        }
    }

    private fun processScope(node: Node, scope: VariablesScope): ProcessResult {
        val scopeNode = node as? Scope
        val newScope = scopeNode
            ?.symbolTable
            ?.let { VariablesScope(scope, it.values, scopeNode) }
            ?: scope

        return statementsBlock(node, newScope)
    }

    private fun processJump(target: Node, scope: VariablesScope, jump: Node?) {
        processJump(target, scope.createSnapshot(), jump)
    }

    private fun processJump(target: Node, snapshot: ScopeSnapshot, jump: Node?) {
        check(target.isJumpTarget)
        target.ssaGenTargetInfo.addJumpingFrom(snapshot, jump)
    }

    private fun statementsBlock(node: Node, scope: VariablesScope): ProcessResult {
        var isReturned = false
        for (child in node) {
            if (isReturned && child.type != Token.TARGET)
                continue
            val isReturnedOld = isReturned
            isReturned = false
            when (processNode(child, scope, isReturnedOld)) {
                ProcessResult.Continue -> {
                }
                ProcessResult.Jumped -> {
                    isReturned = true
                }
            }
        }

        if (isReturned) return ProcessResult.Jumped
        return ProcessResult.Continue
    }

    //endregion processNode

    //region optimizeSSAPhis

    private fun optimizeSSAPhis() {
        while (true) {
            val modified = runOptimizeSSAPhis()
            if (!modified)
                return
        }
    }

    /**
     * @return modified
     */
    private fun runOptimizeSSAPhis(): Boolean {
        var modified = false
        for (target in targets) {
            modified = modified or runOptimizeSSAPhis1(target)
        }
        return modified
    }

    /**
     * merge `NAME`s which has same varId to one `NAME`
     */
    private fun runOptimizeSSAPhis1(target: Node): Boolean {
        check(target.isJumpTarget)
        val names = target.toList() as List<Name>
        val nameById = names.groupBy { it.varId }
        // if same size, this mean there isn't `NAME` to be merged
        if (nameById.size == names.size)
            return false

        nameById.values
            .asSequence()
            .filterNot { it.size == 1 }
            .forEach { names ->
                val first = names.first()
                names.asSequence()
                    .drop(1)
                    .flatten()
                    .castAsElementsAre<Name>()
                    .forEach { first.addChildToBack(it) }
            }

        return true
    }

    //endregion

    //region markUnreachable

    private fun markUnreachable() {
        for (target in targets) {
            val info = target.ssaGenTargetInfo
            if (!info.isReachable) {
                info.atTargetSnapshot!!
                    .scopes
                    .asSequence()
                    .flatMap { it }
                    .map { it.varId }
                    .forEach { it.markUnreachable() }
            }
        }
    }

    //endregion

    //region replaceSSAGenInfoToNormalInfo

    private fun replaceSSAGenInfoToNormalInfo() {
        for (target in targets) {
            val ssaGenInfo = target.ssaGenTargetInfo
            val targetInfo = TargetInfo(
                reachable = ssaGenInfo.isReachable
            )
            for ((scope, versions) in ssaGenInfo.atTargetSnapshot!!.scopes.zip(ssaGenInfo.versions)) {
                for (name in scope) {
                    val newVersions = versions[name.identifier] ?: continue
                    if (newVersions.isEmpty()) continue
                    for (newVersion in newVersions) {
                        name.addChildToBack(newVersion)
                    }
                    target.addChildToBack(name)
                }
            }

            target.internalProps.remove(ssaGenTargetInfoKey)
            target.targetInfo = targetInfo
        }
    }

    //endregion

    //region removeFinallyInfos

    fun removeFinallyInfos() {
        for (finally in finallies) {
            finally.internalProps.remove(finallyInternalInfoKey)
        }
    }

    //endregion

    private class VariablesScope(
        val parent: VariablesScope? = null,
        private val variables: Map<String, Variable>,
    ) {
        constructor(
            parent: VariablesScope? = null,
            names: Collection<Symbol>,
            declaringScope: Scope,
        ) : this(
            parent = parent,
            variables = names
                .map { symbol ->
                    val variable = Variable(symbol.name)
                    variable.getCurrent().producer = declaringScope
                    when (symbol.declType) {
                        Token.FUNCTION -> {
                            variable.enabledSinceHere()
                            // TODO: set type if possible
                        }
                        Token.LP -> {
                            if (declaringScope !is FunctionNode)
                                unsupported("function arguments since block")
                            variable.enabledSinceHere()
                        }
                        Token.VAR -> variable.enabledSinceHere()
                        Token.LET -> {
                        }
                        Token.CONST -> {
                        }
                    }
                    symbol.name to variable
                }
                .toMap()
        )

        /**
         * null means not a variable
         */
        fun variable(name: String): Variable? {
            var cur = this.nullable()
            while (cur != null) {
                cur.variables[name]?.let { return it }
                cur = cur.parent
            }
            return null
        }

        fun variableExactly(name: String): Variable {
            return this.variables[name] ?: error("$name is not variable")
        }

        fun createSnapshot(): ScopeSnapshot {
            val scopes = mutableListOf<List<Name>>()
            var cur = this.nullable()
            while (cur != null) {
                scopes += cur
                    .variables
                    .values
                    .map { createName(it.getCurrent()) }
                    .toList()
                cur = cur.parent
            }
            return ScopeSnapshot(scopes.asReversed())
        }

        fun updateAll(producer: Node): ScopeSnapshot {
            var cur = this.nullable()
            while (cur != null) {
                cur.variables.values.forEach { it.makeNext(producer = producer) }
                cur = cur.parent
            }
            return createSnapshot()
        }

        /**
         * @return result[scopeIndex\] = list of versioned variable of the scope
         */
        fun allVariableVersions(since: ScopeSnapshot? = null): List<Iterable<VariableId>> {
            @Suppress("NAME_SHADOWING")
            val since = since
                ?.scopes
                ?.map { it.map { it.varId }.map { it.name to it }.toMap() }
            val scopes = mutableListOf<List<VariableId>>()

            val sinceItr = since?.listIterator(since.size)
            var cur = this.nullable()
            while (cur != null) {
                val snapshot = sinceItr?.previous()
                for (variable in cur.variables.values) {
                    val snapshotVar = snapshot?.get(variable.name)
                    scopes += variable.getVersions(since = snapshotVar)
                }
                cur = cur.parent
            }

            return scopes
        }
    }

    private class Variable(val name: String) {
        private val versions = mutableListOf<VariableId>()
        private var enabledSince: VariableId? = null

        init {
            makeNext(producer = null)
        }

        fun getCurrent() = versions.last()

        fun makeNext(producer: Node?): VariableId {
            val result = VariableId(name, versions.size, versions.lastOrNull())
            if (producer != null)
                result.producer = producer
            versions += result
            return result
        }

        fun enabledSinceHere() {
            check(enabledSince == null) { "already enabled" }
            enabledSince = getCurrent()
        }

        fun getVersions(since: VariableId?): List<VariableId> {
            if (since == null)
                return versions

            val result = arrayListOf<VariableId>()
            for (variableId in versions.asReversed()) {
                result += variableId
                if (variableId == since)
                    return result
            }

            throw IllegalArgumentException("since is not a version of this variable: $since")
        }
    }

    private class ScopeSnapshot(
        /**
         * - scopes\[0]: global scope
         * - scopes\[1]: function scope
         * - scopes\[N]: block scope
         */
        val scopes: List<List<Name>>,
    ) {
        override fun toString(): String {
            return "S(${scopes.joinToString { it.joinToString(prefix = "[", postfix = "]") { "${it.varId}" } }})"
        }
    }

    private class SSAGenTargetInfo {
        var atTargetSnapshot: ScopeSnapshot? = null

        /**
         * scopes\[scopeIndex\]\[variableName\] = variableVersions
         *
         * - scopes\[0]: global scope
         * - scopes\[1]: function scope
         * - scopes\[N]: block scope
         */
        private val _versions = arrayListOf<MutableMap<String, MutableList<Name>>>()

        val versions: List<Map<String, MutableList<Name>>> get() = _versions

        fun markAsUnreachable() {
            _versions.clear()
        }

        private fun resizeVersionsTo(size: Int) {
            while (_versions.size < size)
                _versions.add(hashMapOf())
        }

        fun addJumpingFrom(snapshot: ScopeSnapshot, jump: Node?) {
            resizeVersionsTo(snapshot.scopes.size)
            for ((versionScope, snapScope) in _versions.zip(snapshot.scopes)) {
                for (name in snapScope) {
                    val versions = versionScope.computeIfAbsent(name.identifier) { arrayListOf() }
                    if (versions.none { it.varId == name.varId }) {
                        versions.add(name.copy().apply { jumpFrom = jump })
                    }
                }
            }
            return
        }

        /**
         * @param adding adding[scopeIndex\] = list of versioned variable to add to the scope
         */
        fun addJumpingFroms(adding: List<Iterable<VariableId>>, jump: Jump?) {
            resizeVersionsTo(adding.size)
            for ((versionScope, variables) in _versions.zip(adding)) {
                for (variable in variables) {
                    val versions = versionScope.computeIfAbsent(variable.name) { arrayListOf() }
                    if (versions.none { it.varId == variable }) {
                        versions.add(createName(variable).apply { jumpFrom = jump })
                    }
                }
            }
        }

        val isReachable: Boolean get() = _versions.isNotEmpty()

        override fun toString(): String {
            return buildString {
                append("SSAGenTargetInfo(")
                if (!isReachable)
                    append("unreachable, ")
                if (atTargetSnapshot != null) {
                    val atTargetSnapshot = atTargetSnapshot!!
                    val versionScopes = _versions.asResizedWithDefault(atTargetSnapshot.scopes.size, emptyMap())
                    atTargetSnapshot.scopes.zip(versionScopes).joinTo(this) { (shot, variables) ->
                        shot.joinTo(this, prefix = "[", postfix = "]") { name ->
                            val versions = variables[name.identifier]
                            append(name.varId)
                            if (versions == null) {
                                append("<:none")
                            } else {
                                append("<:[")
                                versions.joinTo(this) { it.varId.shortHash() + "by" + it.joinToString { it.shortHash() } }
                                append("]")
                            }
                            ""
                        }
                        ""
                    }
                } else {
                    append("none, ")
                    _versions.joinTo(this) { variables ->
                        variables.entries.joinTo(this, prefix = "[", postfix = "]") { (name, versions) ->
                            append(name)
                            append(":[")
                            versions.joinTo(this) { it.shortHash() }
                            append("]")
                            ""
                        }
                        ""
                    }
                }
                append(")")
            }
        }
    }

    private val ssaGenTargetInfoKey = InternalPropMap.Key<SSAGenTargetInfo>("ssaGenTargetInfo")
    private val Node.ssaGenTargetInfo: SSAGenTargetInfo
            by ssaGenTargetInfoKey.computing(::SSAGenTargetInfo) { require(it.isJumpTarget) }

    /**
     * If [returningTo] is not null and [snapshot] is null, the finally block is not proceed and will be proceed.
     *
     * If [returningTo] is null and [snapshot] is not null, the finally block has been proceed
     *
     * If both [returningTo] and [snapshot] are null, the finally has been proceed
     * and (end of) finally is not reachable code
     *
     * Both [returningTo] and [snapshot] cannot be null at once.
     */
    private class FinallyInfo {
        var returningTo: MutableList<Node>? = mutableListOf()
        var snapshot: ScopeSnapshot? = null
    }

    private val finallyInternalInfoKey = InternalPropMap.Key<FinallyInfo>("finallyInternalInfo")
    private val Node.finallyInternalInfo: FinallyInfo
            by finallyInternalInfoKey.computing(::FinallyInfo) { require(it.type == Token.FINALLY) }

    companion object {
        private fun unsupported(message: String): Nothing {
            error("unsupported: $message")
        }

        private fun createName(id: VariableId): Name {
            return Name().apply {
                string = id.name
                varId = id
            }
        }

    }
}
