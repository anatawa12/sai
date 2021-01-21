package com.anatawa12.sai.stia

import com.anatawa12.sai.Node
import com.anatawa12.sai.Token
import com.anatawa12.sai.ast.*
import com.anatawa12.sai.stia.JumpingInfo.Companion.jumpingInfo
import com.anatawa12.sai.stia.types.AbstractVisitor
import com.anatawa12.sai.stia.types.CompileTimeConstantType

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

    fun process(node: ScriptNode): Map<String, VariableId.Global> {
        // TODO: global scope
        val globalScope = GlobalVariablesScope()
        val scope = node.symbolTable
            ?.let { BlockVariablesScope(globalScope, it.values, node) }
            ?: globalScope

        Visitor(scope).statementsBlock1(node)
        markUnreachable()
        replaceSSAGenInfoToNormalInfo()
        removeInternalInfos()
        optimizeSSAPhis() // Nameのidを統合

        return globalScope.variables.mapValues { it.value.variableId }
    }

    //region processNode

    private inner class Visitor(var scope: VariablesScope) : AbstractVisitor() {

        //region visitExprs

        private fun visitExprs(n1: Node) {
            visitExpr(n1)
        }

        private fun visitExprs(n1: Node, n2: Node) {
            visitExpr(n1)
            visitExpr(n2)
        }

        private fun visitExprs(n1: Node, n2: Node, n3: Node) {
            visitExpr(n1)
            visitExpr(n2)
            visitExpr(n3)
        }

        override fun visitBitOr(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitBitXor(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitBitAnd(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitLsh(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitRsh(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitURsh(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitEq(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitNe(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitLt(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitLe(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitGt(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitGe(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitIn(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitShallowEq(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitShallowNe(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitInstanceOf(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitSub(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitMul(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitDiv(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitMod(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitAdd(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitNot(node: Node, operand: Node) = visitExprs(operand)
        override fun visitBitNot(node: Node, operand: Node) = visitExprs(operand)
        override fun visitPositive(node: Node, operand: Node) = visitExprs(operand)
        override fun visitNegative(node: Node, operand: Node) = visitExprs(operand)
        override fun visitTypeOf(node: Node, operand: Node) = visitExprs(operand)
        override fun visitVoid(node: Node, operand: Node) = visitExprs(operand)
        override fun visitGetProperty(node: Node, owner: Node, name: Node, isProperty: Boolean) = visitExprs(owner, name)
        override fun visitSetProperty(node: Node, owner: Node, name: Node, value: Node, isProperty: Boolean) = visitExprs(owner, name, value)
        override fun visitThrow(node: Node, throws: Node) = visitExprs(throws)
        override fun visitEmptyStatement(node: Node) = Unit
        override fun visitExprStatement(node: Node, expr: Node, isForReturn: Boolean) = visitExprs(expr)
        override fun visitConvertException(node: Node, throwable: Node) = visitExprs(throwable)
        override fun visitNewOrCall(node: Node, function: Node, args: List<Node>, isNewInstance: Boolean) {
            visitExprs(function)
            args.forEach(::visitExpr)
        }

        override fun <T> visitLiteral(node: Node, type: CompileTimeConstantType<T>, value: T) = Unit
        override fun visitCommaExpr(node: Node, exprs: List<Node>) = exprs.forEach(::visitExpr)
        override fun visitOr(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitAnd(node: Node, left: Node, right: Node) = visitExprs(left, right)
        override fun visitPrefixIncrement(node: Node, operand: Node) = visitExprs(operand)
        override fun visitPrefixDecrement(node: Node, operand: Node) = visitExprs(operand)
        override fun visitPostfixIncrement(node: Node, operand: Node) = visitExprs(operand)
        override fun visitPostfixDecrement(node: Node, operand: Node) = visitExprs(operand)

        //endregion

        override fun visitPostfixIncrementName(node: Node, getting: Name, setting: Name)  = visitInDecrementName(node, getting, setting)
        override fun visitPrefixIncrementName(node: Node, getting: Name, setting: Name)  = visitInDecrementName(node, getting, setting)
        override fun visitPostfixDecrementName(node: Node, getting: Name, setting: Name)  = visitInDecrementName(node, getting, setting)
        override fun visitPrefixDecrementName(node: Node, getting: Name, setting: Name)  = visitInDecrementName(node, getting, setting)

        private fun visitInDecrementName(node: Node, getting: Name, setting: Name) {
            val variable = scope.variable(getting.identifier)
            getting.varId = variable.getCurrent()
            setting.varId = variable.makeNext(node)
        }

        override fun visitReturn(node: Node, returns: Node?) {
            if (returns != null)
                visitExprs(returns)
            reachStatus.nextNever()
        }

        override fun visitGoto(node: Jump) {
            processJump(node.target, scope, node)
            reachStatus.makeJump(node)
            reachStatus.reachable.never()
        }

        override fun visitJSR(node: Jump) {
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

        override fun visitIfEQ(node: Jump, condition: Node) = visitIfJump(node, condition)
        override fun visitIfNE(node: Jump, condition: Node) = visitIfJump(node, condition)

        private fun visitIfJump(node: Jump, condition: Node) {
            visitExprs(condition)
            if (reachStatus.neverReach)
                return

            reachStatus.makeJump(node)
            processJump(node.target, scope, node)
            // ScriptRuntime.newErrorForThrowable
        }

        override fun visitInLoopJump(node: Jump, isContinue: Boolean) {
            val loop = node.jumpStatement
            val target = if (isContinue) loop.`continue` else loop.target
            reachStatus.makeJump(node)
            reachStatus.reachable.never()
            processJump(target, scope, node)
        }

        override fun visitSwitch(node: Jump, value: Node, cases: List<Pair<Jump, Node>>) {
            visitExprs(value)
            if (reachStatus.neverReach)
                return

            reachStatus.makeJump(node)
            for ((case, condition) in cases) {
                check(case.type == Token.CASE)
                visitExprs(condition)
                processJump(case.target, scope, node)
            }
        }

        override fun visitTarget(node: Node) {
            targets.add(node)
            val curSnapshot = scope.createSnapshot()
            val newSnapshot = scope.updateAll(producer = node)
            if (!reachStatus.neverReach)
                node.ssaGenTargetInfo.addJumpingFrom(curSnapshot, null)
            node.ssaGenTargetInfo.atTargetSnapshot = newSnapshot
            reachStatus.makeTarget(node)
            curSnapshot.close()
        }

        override fun visitLabel(node: Node): ProcessResult {
            return statementsBlock1(node)
        }

        override fun visitLocalLoad(node: Node) {
            node.realLocalVarId = localBlockVariableId(node)
        }

        override fun visitInternalLocalBlock(node: Node): ProcessResult {
            localBlocks.add(node)
            node.putIntProp(Node.LOCAL_PROP, localBlocks.size)
            return statementsBlock1(node)
        }

        override fun visitBlock(node: Node): ProcessResult {
            return processScope(node)
        }

        override fun visitLoop(node: Node): ProcessResult {
            return processScope(node)
        }

        override fun visitConditionalOperator(node: Node, condition: Node, ifTrue: Node, ifFalse: Node) {
            visitExprs(condition)
            if (reachStatus.neverReach)
                return

            val afterCond = reachStatus.reachable
            val afterProcess = Reachable()

            reachStatus.startWith(afterCond)
            visitExprs(ifTrue)
            afterProcess.addFrom(reachStatus.reachable)

            reachStatus.startWith(afterCond)
            visitExprs(ifFalse)
            afterProcess.addFrom(reachStatus.reachable)

            reachStatus.reachable = afterProcess
        }

        override fun visitTryBlock(node: Jump): ProcessResult {
            val snapshot = scope.createSnapshot()
            val catchTarget = node.target.nullable()
            val finallyTarget = node.finally.nullable()

            val tryInfo = node.tryInfo
            val localBlock = getLocalBlock(node)
            var variableId: VariableId.Internal? = null
            reachStatus.makeJump(node)

            statementsBlockWithBlock(node) { child ->
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

            snapshot.close()
            localBlock.deleteRealLocalVarId()

            return ProcessResult.Continue
        }

        override fun visitFinally(node: Node) {
            val info = node.finallyInternalInfo
            node.realLocalVarId = localBlockVariableId(node)

            statementsBlock1(node)
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
            snapshot.close()
        }

        override fun visitRethrow(node: Node) {
            node.realLocalVarId = localBlockVariableId(node)
            reachStatus.nextNever()
        }

        override fun visitSetVariable(node: Node, variable: Name, value: Node) {
            // TODO: global scopee
            variable.varId = scope.variable(variable.identifier).makeNext(node)
            visitExprs(value)
        }

        override fun visitGetVariable(node: Name) {
            node.varId = scope.variable(node.identifier).getCurrent()
        }

        override fun visitVariableDefinition(node: Node, variables: List<Pair<Name, Node?>>, kind: Int) {
            for ((name, initializer) in variables) {
                if (initializer != null)
                    visitExprs(initializer)
                val variable = scope.variableExactly(name.identifier)
                name.varId = variable.makeNext(name)
                variable.getCurrent().producer = node
                if (node.type != Token.VAR)
                    variable.enabledSinceHere()
            }
        }

        override fun visitFunctionStatement(node: Node) {
            unsupported("function literal")
        }

        private fun processScope(
            node: Node,
        ): ProcessResult.Continue {
            val scopeNode = node as? Scope
            val old = scope
            scope = scopeNode
                ?.symbolTable
                ?.let { BlockVariablesScope(scope, it.values, scopeNode) }
                ?: scope
            try {
                return statementsBlock1(node)
            } finally {
                scope = old
            }
        }

        private fun processJump(target: Node, scope: VariablesScope, jump: Node) {
            val snapshot = scope.createSnapshot()
            processJump(target, snapshot, jump)
            snapshot.close()
        }

        private fun processJump(target: Node, snapshot: ScopeSnapshot, jump: Node) {
            check(target.isJumpTarget)
            target.ssaGenTargetInfo.addJumpingFrom(snapshot, jump)
            target.targetInfo.reachable.addFrom(jump.jumpingInfo.mayJump)
        }

        fun statementsBlock1(
            node: Node,
        ): ProcessResult.Continue {
            return statementsBlockWithBlock(node) {}
        }

        private inline fun statementsBlockWithBlock(
            node: Node,
            customPreProcess: (child: Node) -> Unit,
        ): ProcessResult.Continue {
            for (child in node) {
                if (reachStatus.neverReach && child.type != Token.TARGET)
                    continue
                customPreProcess(child)
                visitStatement(child)
            }
            return ProcessResult.Continue
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
                namesById.asSequence().drop(1).forEach(Name::deleteVarId)
                val values = namesById.asSequence()
                    .flatten()
                    .castAsElementsAre<Name>()
                    .mapNotNull {
                        if (it.jumpFrom?.jumpingInfo?.mayJump?.reachable != false) {
                            it
                        } else {
                            it.deleteVarId()
                            null
                        }
                    }
                    .groupBy { it.varId }
                    .values
                    .map {
                        it.asSequence().drop(1).forEach(Name::deleteVarId)
                        it.first()
                    }

                first.removeChildren()
                if (values.size <= 2) {
                    val excludedValues = values.filter { it.varId != first.varId }
                    if (excludedValues.isEmpty()) {
                        first.deleteVarId()
                        values.asSequence().forEach(Name::deleteVarId)
                        modified = true
                        return@forEach
                    } else if (excludedValues.size == 1) {
                        modified = true
                        val replaceTo = excludedValues.single()
                        replaceTo.varId.asLocal().replacedBy(first.varId.asLocal(), replaceAt = target)
                        first.deleteVarId()
                        values.asSequence().forEach(Name::deleteVarId)
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
                    @Suppress("NAME_SHADOWING")
                    val name = createName(name.varId.asLocal())
                    for (newVersion in newVersions) {
                        name.addChildToBack(createName(newVersion.varId.asLocal()))
                    }
                    target.addChildToBack(name)
                }
            }
            ssaGenInfo.close()

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
        val variables = mutableMapOf<String, Variable.Global>()

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
                result.producer = producer
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
    ) : AutoCloseable {
        override fun toString(): String {
            return "S(${scopes.joinToString { it.joinToString(prefix = "[", postfix = "]") { "${it.varId}" } }})"
        }

        override fun close() {
            scopes.asSequence()
                .flatten()
                .forEach(Name::deleteVarId)
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

        fun close() {
            atTargetSnapshot!!.close()
            versions.asSequence()
                .map { it.values }
                .flatten()
                .flatten()
                .forEach(Name::deleteVarId)
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
