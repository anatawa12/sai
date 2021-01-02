package com.anatawa12.sai.stia

import com.anatawa12.sai.Node
import com.anatawa12.sai.Token
import com.anatawa12.sai.ast.*
import com.anatawa12.sai.stia.JumpingInfo.Companion.jumpingInfo

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
    private val localBlocks = mutableListOf<Node>()
    private val reachStatus = ReachableRef(Reachable.alwaysReach())

    fun process(node: ScriptNode) {
        // TODO: global scope
        var scope: VariablesScope = GlobalVariablesScope()
        scope = node.symbolTable
            ?.let { BlockVariablesScope(scope, it.values, node) }
            ?: scope

        statementsBlock(node, scope)
        markUnreachable()
        replaceSSAGenInfoToNormalInfo()
        removeInternalInfos()
        optimizeSSAPhis() // Nameのidを統合
    }

    //region processNode

    private fun processNode(
        node: Node,
        scope: VariablesScope,
    ) {
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
            Token.IN,
            Token.INSTANCEOF,
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
            Token.COMMA, // expr, expr, expr....
            Token.OR, // ||
            Token.AND, // &&
            Token.INC, // ++ expr or expr ++
            Token.DEC, // -- expr or expr --
            -> {
                // simple expr default
                for (child in node) {
                    processNode(child, scope)
                    if (reachStatus.neverReach)
                        return
                }
            }

            Token.INC_DEC_NAME,
            -> {
                val (old, new) = node.asPair()
                old as Name
                new as Name
                val variable = scope.variable(old.identifier)
                old.varId = variable.getCurrent()
                new.varId = variable.makeNext(node)
            }

            Token.RETURN, // void
            -> {
                val returnValue = node.firstChild.nullable()
                if (returnValue != null)
                    processNode(returnValue, scope)
                reachStatus.nextNever()
            }

            // jumping instructions
            Token.GOTO,
            -> {
                node as Jump
                processJump(node.target, scope, node)
                reachStatus.makeJump(node)
                reachStatus.reachable.never()
            }
            Token.JSR, // TODO: return from finally
            -> {
                node as Jump
                val target = node.target
                val finally = target.next
                if (finally.type != Token.FINALLY)
                    unsupported("jsr to not just before finally block")
                val finallyInfo = finally.finallyInternalInfo

                finallies.add(finally)

                // jsr -> node: as a jump insn
                reachStatus.makeJump(node)
                reachStatus.reachable.never()
                processJump(target, scope, node)

                // finally -> jsr: as a target insn
                targets.add(node)
                reachStatus.makeTarget(node)
                val newSnapshot = scope.updateAll(producer = node)
                node.ssaGenTargetInfo.atTargetSnapshot = newSnapshot

                // do something for jumping of finally
                if (finallyInfo.snapshot != null) {
                    processJump(target = node, snapshot = finallyInfo.snapshot!!, finally)
                } else {
                    finallyInfo.returningTo?.add(node)
                }
            }
            Token.IFEQ,
            Token.IFNE,
            -> {
                node as Jump
                val condition = node.single()

                processNode(condition, scope)
                if (reachStatus.neverReach)
                    return

                reachStatus.makeJump(node)
                processJump(node.target, scope, node)
                // ScriptRuntime.newErrorForThrowable
            }
            Token.BREAK,
            Token.CONTINUE,
            -> {
                node as Jump
                val loop = node.jumpStatement
                val target = if (node.type == Token.BREAK) loop.target else loop.`continue`
                reachStatus.makeJump(node)
                reachStatus.reachable.never()
                processJump(target, scope, node)
            }

            // switch statement
            Token.SWITCH,
            -> {
                node as Jump
                val expr = node.first()
                val cases = node.drop(1)

                processNode(expr, scope)
                if (reachStatus.neverReach)
                    return

                reachStatus.makeJump(node)
                for (case in cases) {
                    check(case.type == Token.CASE)
                    case as Jump
                    val value = case.single()
                    processNode(value, scope)
                    processJump(case.target, scope, node)
                }
            }

            Token.TARGET, // TODO: CHECK
            -> {
                targets.add(node)
                val curSnapshot = scope.createSnapshot()
                val newSnapshot = scope.updateAll(producer = node)
                if (!reachStatus.neverReach)
                    node.ssaGenTargetInfo.addJumpingFrom(curSnapshot, null)
                node.ssaGenTargetInfo.atTargetSnapshot = newSnapshot
                reachStatus.makeTarget(node)
            }

            // statements or expressions block
            Token.LABEL, // block
                //Token.WITH, // unsuppoted
            -> {
                statementsBlock(node, scope)
            }

            // contextual non-visible local variable
            Token.LOCAL_LOAD,
            -> {
                node.realLocalVarId = localBlockVariableId(node)
            }

            Token.LOCAL_BLOCK, // contextual local variable (hidden) // TODO
            -> {
                localBlocks.add(node)
                node.putIntProp(Node.LOCAL_PROP, localBlocks.size)
                statementsBlock(node, scope)
            }

            // scope block
            Token.BLOCK, //Token.ARRAYCOMP, unsupported
            Token.LOOP,
            -> processScope(node, scope)

            Token.CASE, // case label(?)
            -> unsupported("case must be in switch")

            Token.HOOK, // condition ? then : else
            -> {
                val (condition, ifTrue, ifFalse) = node.asTriple()

                processNode(condition, scope)
                if (reachStatus.neverReach)
                    return

                val afterCond = reachStatus.reachable
                val afterProcess = Reachable()

                reachStatus.startWith(afterCond)
                processNode(ifTrue, scope)
                afterProcess.addFrom(reachStatus.reachable)

                reachStatus.startWith(afterCond)
                processNode(ifFalse, scope)
                afterProcess.addFrom(reachStatus.reachable)

                reachStatus.reachable = afterProcess
            }

            Token.TRY, // try {} catch {} finally {}
            -> {
                node as Jump
                val snapshot = scope.createSnapshot()
                val catchTarget = node.target.nullable()
                val finallyTarget = node.finally.nullable()

                val tryInfo = node.tryInfo
                val localBlock = getLocalBlock(node)
                var variableId: VariableId.Internal? = null
                reachStatus.makeJump(node)

                statementsBlockWithBlock(node, scope) { child ->
                    if (child === catchTarget || child === finallyTarget) {
                        child.targetInfo.reachable.addFrom(node.jumpingInfo.mayJump)
                        child.ssaGenTargetInfo.addJumpingFroms(scope.allVariableVersions(since = snapshot), node)
                        check(localBlock.realLocalVarIdOrNull == variableId) { "local block conflict" }
                        variableId = makeNextLocalBlockVariableId(node, variableId)
                        variableId!!.producer = node
                        localBlock.realLocalVarId = variableId!!
                        if (child === catchTarget)
                            tryInfo.catchVariable = variableId
                        else
                            tryInfo.finallyVariable = variableId
                    }
                }

                localBlock.deleteRealLocalVarId()
            }
            Token.FINALLY,
            -> {
                val info = node.finallyInternalInfo
                node.realLocalVarId = localBlockVariableId(node)

                statementsBlock(node, scope)
                if (reachStatus.neverReach) {
                    info.returningTo = null
                    return
                }

                val snapshot = scope.createSnapshot()
                info.snapshot = snapshot
                reachStatus.makeJump(node)
                for (returningTo in info.returningTo!!) {
                    processJump(returningTo, snapshot, node)
                }
                info.returningTo = null
            }

            Token.RETHROW,
            -> {
                node.realLocalVarId = localBlockVariableId(node)
                reachStatus.nextNever()
            }

            Token.SETNAME,
            -> {
                // TODO: global scopee
                val (variable, expr) = node.asPair()
                variable as Name
                variable.varId = scope.variable(variable.identifier).makeNext(node)
                processNode(expr, scope)
            }
            Token.NAME, // TODO
            -> {
                node as Name
                node.varId = scope.variable(node.identifier).getCurrent()
            }
            Token.BINDNAME,
            -> unsupported("BINDNAME")

            Token.VAR, // TODO: CHECK
            Token.LET, // TODO: CHECK
            Token.CONST, // TODO: CHECK
            -> {
                for (name in node) {
                    name as Name
                    val initializer = name.singleOrNull()
                    if (initializer != null)
                        processNode(initializer, scope)
                    val variable = scope.variableExactly(name.identifier)
                    name.varId = variable.makeNext(name)
                    if (node.type != Token.VAR)
                        variable.enabledSinceHere()
                }
            }

            Token.THISFN, // reference to this function
            -> {
                // nop
            }

            Token.FUNCTION, // literal or root definition
            -> {
                unsupported("function literal")
            }

            Token.CONVERT_EXCEPTION,
            -> {
                val convertFrom = node.single()
                processNode(convertFrom, scope)
            }

            Token.ENTERWITH,
            Token.LEAVEWITH,
            Token.WITH,
            Token.WITHEXPR,
            -> unsupported("with")
            Token.DEBUGGER,
            -> unsupported("debugger")
            Token.METHOD,
            -> unsupported("method")
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
            Token.STRICT_SETNAME,
            Token.GETVAR,
            Token.SETVAR,
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

    fun makeNextLocalBlockVariableId(node: Node, prev: VariableId.Internal?): VariableId.Internal {
        val localId = getLocalBlock(node).getExistingIntProp(Node.LOCAL_PROP)
        return VariableId.Internal(
            id = localId,
            version = prev?.version?.plus(1) ?: 0,
            previousVersion = prev
        )
    }

    private fun getLocalBlock(node: Node): Node {
        return if (node.type == Token.LOCAL_BLOCK) node
        else node.getProp(Node.LOCAL_BLOCK_PROP) as Node? ?: error("LOCAL_BLOCK_PROP not found")
    }

    private fun localBlockVariableId(node: Node): VariableId {
        return getLocalBlock(node).realLocalVarId
    }

    private fun processScope(
        node: Node,
        scope: VariablesScope,
    ) {
        val scopeNode = node as? Scope
        val newScope = scopeNode
            ?.symbolTable
            ?.let { BlockVariablesScope(scope, it.values, scopeNode) }
            ?: scope

        statementsBlock(node, newScope)
    }

    private fun processJump(target: Node, scope: VariablesScope, jump: Node) {
        processJump(target, scope.createSnapshot(), jump)
    }

    private fun processJump(target: Node, snapshot: ScopeSnapshot, jump: Node) {
        check(target.isJumpTarget)
        target.ssaGenTargetInfo.addJumpingFrom(snapshot, jump)
        target.targetInfo.reachable.addFrom(jump.jumpingInfo.mayJump)
    }

    private fun statementsBlock(
        node: Node,
        scope: VariablesScope,
    ) {
        statementsBlockWithBlock(node, scope) {}
    }

    private inline fun statementsBlockWithBlock(
        node: Node,
        scope: VariablesScope,
        customPreProcess: (child: Node) -> Unit,
    ) {
        for (child in node) {
            if (reachStatus.neverReach && child.type != Token.TARGET)
                continue
            customPreProcess(child)
            processNode(child, scope)
        }
    }

    private class ReachableRef(var reachable: Reachable) {
        fun nextNever() {
            reachable = Reachable.neverReach()
        }

        fun makeTarget(node: Node) {
            node.targetInfo.setPrev(reachable)
            reachable = node.targetInfo.reachable
        }

        fun startWith(pre: Reachable) {
            reachable = Reachable.withBefore(pre)
        }

        fun makeJump(jump: Node) {
            jump.jumpingInfo.addFrom(reachable)
            reachable = jump.jumpingInfo.mayContinue
        }

        val neverReach get() = reachable.neverReach
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
        val names = target.toList().castAsElementsAre<Name>()
        val nameById = names.groupBy { it.varId }
        var modified = nameById.size != names.size

        target.removeChildren()
        nameById.values
            .asSequence()
            .forEach { namesById ->
                val first = namesById.first()
                val values = namesById.asSequence()
                    .flatten()
                    .castAsElementsAre<Name>()
                    .filter { it.jumpFrom?.jumpingInfo?.mayJump?.reachable ?: true }
                    .associateBy { it.varId }
                    .values
                first.removeChildren()
                if (values.size <= 2) {
                    val excludedValues = values.filter { it.varId != first.varId }
                    if (excludedValues.isEmpty()) {
                        modified = true
                        return@forEach
                    } else if (excludedValues.size == 1) {
                        modified = true
                        excludedValues.single().varId.asLocal().replacedBy(first.varId.asLocal())
                        return@forEach
                    }
                }
                values.forEach { first.addChildToBack(it) }
                target.addChildToBack(first)
            }

        return modified
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
                    .map { it.varId.asLocal() }
                    .forEach { it.markUnreachable() }
            }
        }
    }

    //endregion

    //region replaceSSAGenInfoToNormalInfo

    private fun replaceSSAGenInfoToNormalInfo() {
        for (target in targets) {
            val ssaGenInfo = target.ssaGenTargetInfo
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
        }
    }

    //endregion

    //region removeFinallyInfos

    fun removeInternalInfos() {
        for (finally in finallies) {
            finally.internalProps.remove(finallyInternalInfoKey)
        }
        for (localBlock in localBlocks) {
            localBlock.removeProp(Node.LOCAL_PROP)
        }
    }

    //endregion

    private abstract class VariablesScope() {
        abstract fun variable(name: String): Variable
        abstract fun variableExactly(name: String): Variable.Local
        abstract fun createSnapshot(): ScopeSnapshot
        abstract fun updateAll(producer: Node): ScopeSnapshot
        abstract fun allVariableVersions(since: ScopeSnapshot? = null): List<Iterable<VariableId.Local>>
    }

    private class GlobalVariablesScope() : VariablesScope() {
        private val variables = mutableMapOf<String, Variable.Global>()

        override fun variable(name: String): Variable {
            return variables.getOrPut(name) { Variable.Global(name) }
        }

        override fun variableExactly(name: String): Variable.Local {
            error("undefined variable")
        }

        override fun createSnapshot(): ScopeSnapshot {
            return ScopeSnapshot(emptyList())
        }

        override fun updateAll(producer: Node): ScopeSnapshot {
            return ScopeSnapshot(emptyList())
        }

        override fun allVariableVersions(since: ScopeSnapshot?): List<Iterable<VariableId.Local>> {
            return emptyList()
        }
    }

    private class BlockVariablesScope(
        val parent: VariablesScope,
        private val variables: Map<String, Variable.Local>,
    ) : VariablesScope() {
        constructor(
            parent: VariablesScope,
            names: Collection<Symbol>,
            declaringScope: Scope,
        ) : this(
            parent = parent,
            variables = kotlin.run {
                val variables = names.map { symbol ->
                    val variable = Variable.Local(symbol.name)
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
                }.toMap()
                if (declaringScope is FunctionNode) {
                    for (param in declaringScope.params) {
                        param as Name
                        val variable = checkNotNull(variables[param.identifier])
                        declaringScope.scopeInfo.addParameter(createName(variable.getCurrent()))
                    }
                }
                variables
            }
        )

        override fun variable(name: String): Variable {
            var cur: VariablesScope = this
            while (cur is BlockVariablesScope) {
                cur.variables[name]?.let { return it }
                cur = cur.parent
            }
            return cur.variable(name)
        }

        override fun variableExactly(name: String): Variable.Local {
            return this.variables[name] ?: error("$name is not variable")
        }

        override fun createSnapshot(): ScopeSnapshot {
            val scopes = mutableListOf<List<Name>>()
            var cur: VariablesScope = this
            while (cur is BlockVariablesScope) {
                scopes += cur
                    .variables
                    .values
                    .map { createName(it.getCurrent()) }
                    .toList()
                cur = cur.parent
            }
            return ScopeSnapshot(scopes.asReversed())
        }

        override fun updateAll(producer: Node): ScopeSnapshot {
            var cur: VariablesScope = this
            while (cur is BlockVariablesScope) {
                cur.variables.values.forEach { it.makeNext(producer = producer) }
                cur = cur.parent
            }
            return createSnapshot()
        }

        /**
         * @return result[scopeIndex\] = list of versioned variable of the scope
         */
        override fun allVariableVersions(since: ScopeSnapshot?): List<Iterable<VariableId.Local>> {
            @Suppress("NAME_SHADOWING")
            val since = since
                ?.scopes
                ?.map { it.map { it.varId.asLocal() }.map { it.name to it }.toMap() }
            val scopes = mutableListOf<List<VariableId.Local>>()

            val sinceItr = since?.listIterator(since.size)
            var cur: VariablesScope = this
            while (cur is BlockVariablesScope) {
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

    private sealed class Variable(val name: String) {
        class Global(name: String): Variable(name) {
            val variableId = VariableId.Global(name)
            override fun getCurrent(): VariableId = variableId

            override fun makeNext(producer: Node): VariableId {
                variableId.producers.add(producer)
                return variableId
            }
        }

        class Local(name: String): Variable(name) {
            private val versions = mutableListOf<VariableId.Local>()
            private var enabledSince: VariableId? = null

            init {
                versions += VariableId.Local(name, 0, null)
            }

            override fun getCurrent(): VariableId.Local = versions.last()

            override fun makeNext(producer: Node): VariableId {
                val result = VariableId.Local(name, versions.size, versions.last())
                versions += result
                return result
            }

            fun enabledSinceHere() {
                check(enabledSince == null) { "already enabled" }
                enabledSince = getCurrent()
            }

            fun getVersions(since: VariableId.Local?): List<VariableId.Local> {
                if (since == null)
                    return versions

                val result = arrayListOf<VariableId.Local>()
                for (variableId in versions.asReversed()) {
                    result += variableId
                    if (variableId == since)
                        return result
                }

                throw IllegalArgumentException("since is not a version of this variable: $since")
            }
        }

        abstract fun getCurrent(): VariableId
        abstract fun makeNext(producer: Node): VariableId
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
        fun addJumpingFroms(adding: List<Iterable<VariableId.Local>>, jump: Jump?) {
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
        private fun createName(id: VariableId.Local): Name {
            return Name().apply {
                string = id.name
                varId = id
            }
        }

        private const val localBlockVariableNamePrefix = "local block variable#"
    }
}
