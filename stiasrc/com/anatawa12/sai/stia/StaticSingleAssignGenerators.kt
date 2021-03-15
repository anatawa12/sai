package com.anatawa12.sai.stia

import com.anatawa12.autoVisitor.autoVisitor
import com.anatawa12.sai.stia.ir.*

class StaticSingleAssignGenerators {
    private val expressionFunction: (IrExpression) -> Unit = ::expression

    private val variables = VariableManager()

    fun generateSSA(node: IrScope) {
        statement(node, false)
        modified = true
        while (modified) {
            modified = false
            optimize(node)
        }
    }

    @Suppress("NAME_SHADOWING")
    private fun expression(expr: IrExpression) {
        if (expr is IrGettingNameExpression) {
            expr.variableForGet = variables.getByName(expr.variableName)
        }
        if (expr is IrSettingNameExpression) {
            expr.variableForSet = variables.setValue(expr.variableName)
        }
        expr.runWithChildExpressions(expressionFunction)
    }

    @Suppress("NAME_SHADOWING")
    private fun statement(stat: IrStatement, jumped: Boolean): Boolean {
        if (jumped && stat !is IrJumpTarget) return true

        val jumped = autoVisitor(stat) { stat ->
            when (stat) {
                is IrScope -> {
                    variables.startScope(stat)
                    val jumped = runBlock(stat, jumped)
                    variables.exitScope()
                    jumped
                }
                is IrBlockStatement -> {
                    runBlock(stat, jumped)
                }
                is IrJumpTarget -> {
                    if (!jumped) stat.ssaPhi.onJumpFrom(variables.shotSnapshot())
                    stat.ssaPhi.onRealScope(variables.newAndSnapshot())
                    false
                }
                is IrSimpleJumping -> {
                    stat.target.ssaPhi.onJumpFrom(variables.shotSnapshot())
                    stat is IrJsr || stat is IrGoto
                }
                is IrSwitch -> {
                    for (case in stat.cases) {
                        case.second.ssaPhi.onJumpFrom(variables.shotSnapshot())
                    }
                    false
                }
                is IrSetThisFn -> {
                    stat.variableForSet = variables.setValue(stat.name)
                    false
                }
                is IrVariableDecl -> {
                    for (variable in stat.variables) {
                        if (variable.value != null)
                            variable.variableForSet = variables.setValue(variable.name)
                    }
                    false
                }
                else -> {
                    // NOP
                    false
                }
            }
        }
        stat.runWithChildExpressions(expressionFunction)
        return jumped
    }

    private fun runBlock(stat: IrBlockStatement, jumpedIn: Boolean): Boolean {
        var jumped = jumpedIn
        for (statement in stat.statements) {
            jumped = statement(statement, jumped)
        }
        return jumped
    }

    private class VariableManager {
        private var scopeId = 0
        private val scopes = mutableListOf<VariableScope>()
        private val variables = mutableMapOf<String, IrOuterVariable>()

        fun getByName(name: String): IrVariableInfo {
            for (scope in scopes) scope.getByName(name)?.let { return it }
            return getOuterScope(name)
        }

        fun setValue(name: String): IrVariableInfo {
            for (scope in scopes) scope.setValue(name)?.let { return it }
            return getOuterScope(name)
        }

        private fun getOuterScope(name: String): IrVariableInfo {
            variables[name]?.let { return it }
            val outer = IrOuterVariable(name)
            variables[name] = outer
            return outer
        }

        fun startScope(scope: IrScope) {
            scopes += VariableScope(scope.table
                .mapValues { entry ->
                    mutableListOf(IrInFunctionVariable(entry.key, 0)
                        .also { entry.value.variableForSet = it })
                },
                scopeId++)
        }

        fun exitScope() {
            scopes.removeLast()
        }

        fun newAndSnapshot() = IrAllScopeSnapshot(
            scopes.map { scope -> IrScopeSnapshot(scope.scopeId, scope.revisions.map { scope.setValue(it.key)!! }) }
        )

        fun shotSnapshot() = IrAllScopeSnapshot(
            scopes.map { scope -> IrScopeSnapshot(scope.scopeId, scope.revisions.map { scope.getByName(it.key)!! }) }
        )
    }

    private class VariableScope(
        val revisions: Map<String, MutableList<IrInFunctionVariable>>,
        val scopeId: Int,
    ) {
        fun getByName(name: String): IrInFunctionVariable? {
            return revisions[name]?.last()
        }

        fun setValue(name: String): IrInFunctionVariable? {
            val revisionList = revisions[name] ?: return null
            val new = IrInFunctionVariable(name, revisionList.size)
            revisionList.add(new)
            return new
        }
    }

    var modified = false

    @Suppress("NAME_SHADOWING")
    private fun optimize(stat: IrStatement): Unit = autoVisitor(stat) { stat ->
        when (stat) {
            is IrBlockStatement -> for (statement in stat.statements) optimize(statement)
            is IrJumpTarget -> optimize(stat.ssaPhi)
            else -> {
                // NOP
            }
        }
    }

    private fun optimize(ssaPhi: IrStaticSingleAssignPhi) {
        val scopes = ssaPhi.scopes ?: return
        for (scope in scopes) {
            val iter = scope.iterator()
            for (phi in iter) {
                if (phi.setFrom.isEmpty()) continue
                val values = phi.setFrom.mapTo(mutableSetOf()) { it.value }
                values.remove(phi.setTo)
                // this means, phi is A = phi(A, A, A...)
                if (values.isEmpty()) {
                    iter.remove()
                    modified = true
                }
                if (values.size != 1) continue
                // in this case, A = phi(A, A, A..., B, B, B...) so replace A to B
                val value = values.single()
                phi.setTo.replaceTo(value)
                iter.remove()
                modified = true
            }
        }
    }
}
