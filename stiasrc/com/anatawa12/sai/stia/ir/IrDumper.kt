package com.anatawa12.sai.stia.ir

import com.anatawa12.autoVisitor.autoVisitor
import com.anatawa12.sai.stia.shortHash

object IrDumper {
    fun <A : Appendable> dumpTo(node: IrNode, target: A): A = target.also {
        when (node) {
            is IrExpression -> target.append(node, "")
            is IrStatement -> target.append(node, "")
        }
    }

    @Suppress("RemoveCurlyBracesFromTemplate")
    private fun Appendable.append(expression: IrExpression, indent: String): Unit = autoVisitor(expression) { expr ->
        when (expr) {
            is IrSetName -> {
                appendLine("${indent}SetName ${expr.variableForSet ?: expr.name}")
                append(expr.value, "$indent  ")
            }
            is IrGetName -> appendLine("${indent}GetName ${expr.variableForGet ?: expr.name}")
            is IrNameIncDec -> {
                val preSuf = if (expr.isSuffix) "Suf" else "Pre"
                if (expr.isDecrement) {
                    appendLine("${indent}${preSuf}DecName ${expr.name}")
                } else {
                    appendLine("${indent}${preSuf}IncName ${expr.name}")
                }
                expr.variableForGet?.let { appendLine("${indent}  gets: $it") }
                expr.variableForSet?.let { appendLine("${indent}  sets: $it") }
            }
            is IrPropertyIncDec -> {
                val preSuf = if (expr.isSuffix) "Suf" else "Pre"
                val propElem = if (expr.isProp) "Prop" else "Elem"
                if (expr.isDecrement) {
                    appendLine("${indent}${preSuf}Dec$propElem")
                } else {
                    appendLine("${indent}${preSuf}Inc$propElem")
                }
                append(expr.owner, "$indent  ")
                append(expr.name, "$indent  ")
            }
            is IrPropertyOperatorAssign -> {
                val propElem = if (expr.isProp) "Prop" else "Elem"
                appendLine("${indent}OperatorAssign$propElem ${expr.operator} ${expr.name}")
                append(expr.owner, "$indent  ")
                append(expr.name, "$indent  ")
                append(expr.operand, "$indent  ")
            }
            is IrNumberLiteral -> appendLine("${indent}NumberLiteral ${expr.value}")
            is IrStringLiteral -> appendLine("${indent}StringLiteral '${expr.value}'")
            is IrNullLiteral -> appendLine("${indent}NullLiteral ${expr.value}")
            is IrBooleanLiteral -> appendLine("${indent}BooleanLiteral ${expr.value}")
            is IrRegexpLiteral -> appendLine("${indent}RegexpLiteral /${expr.value.value}/${expr.value.flags}")
            is IrBinaryOperator -> {
                appendLine("${indent}BinaryOperator ${expr.type}")
                append(expr.left, "$indent  ")
                append(expr.right, "$indent  ")
            }
            is IrUnaryOperator -> {
                appendLine("${indent}UnaryOperator ${expr.type}")
                append(expr.expr, "$indent  ")
            }
            is IrGetProperty -> {
                appendLine("${indent}GetProperty")
                append(expr.owner, "$indent  ")
                append(expr.name, "$indent  ")
            }
            is IrSetProperty -> {
                appendLine("${indent}GetProperty")
                append(expr.owner, "$indent  ")
                append(expr.name, "$indent  ")
                append(expr.value, "$indent  ")
            }
            is IrNewOrCall -> {
                appendLine("${indent}NewOrCall isNew: ${expr.isNewInstance}")
                append(expr.function, "$indent  ")
                for (arg in expr.args) {
                    append(arg, "$indent  ")
                }
            }
            is IrCommaExpr -> {
                appendLine("${indent}CommaExpr")
                for (elem in expr.exprs) {
                    append(elem, "$indent  ")
                }
            }
            is IrConditional -> {
                appendLine("${indent}Conditional")
                append(expr.condition, "$indent  ")
                append(expr.ifTrue, "$indent  ")
                append(expr.ifFalse, "$indent  ")
            }
            is IrConvertException -> appendLine("${indent}ConvertException from ${expr.internalVar}")
        }
    }

    @Suppress("RemoveCurlyBracesFromTemplate")
    private fun Appendable.append(statement: IrStatement, indent: String): Unit = autoVisitor(statement) { stat ->
        when (stat) {
            is IrJumpTarget -> {
                appendLine("${indent}Target#${stat.shortHash()}")
                appendSSA(stat.ssaPhi, "$indent  ")
            }
            is IrReturn -> {
                appendLine("${indent}Return")
                stat.value?.let { append(it, "${indent}  ") }
            }
            is IrGoto -> appendLine("${indent}Goto #${stat.target.shortHash()}")
            is IrJsr -> appendLine("${indent}Jsr #${stat.target.shortHash()}")
            is IrIfFalse -> {
                appendLine("${indent}IrIfFalse #${stat.target.shortHash()}")
                append(stat.condition, "${indent}  ")
            }
            is IrIfTrue -> {
                appendLine("${indent}IrIfTrue #${stat.target.shortHash()}")
                append(stat.condition, "${indent}  ")
            }
            is IrSwitch -> {
                appendLine("${indent}Switch")
                val exprIndent = "$indent    "
                for ((cond, target) in stat.cases) {
                    appendLine("${indent}  Case #${target.shortHash()}")
                    append(cond, exprIndent)
                }
            }
            is IrVariableDecl -> {
                appendLine("${indent}VariableDecl ${stat.kind}")
                val exprIndent = "$indent    "
                for (variable in stat.variables) {
                    appendLine("${indent}  Variable ${variable.variableForSet ?: variable.name}")
                    variable.value?.let { append(it, exprIndent) }
                }
            }
            is IrThrow -> {
                appendLine("${indent}Throw")
                append(stat.exception, "${indent}  ")
            }
            is IrRethrow -> appendLine("${indent}Rethrow ${stat.internalVar}")
            is IrEmptyStatement -> appendLine("${indent}EmptyStatement")
            is IrExpressionStatement -> {
                appendLine("${indent}ExpressionStatement")
                append(stat.expr, "$indent  ")
            }
            is IrFunctionStatement -> {
                appendLine("${indent}FunctionStatement")
            }
            is IrSetThisFn -> {
                appendLine("${indent}SetThisFn to ${stat.variableForSet ?: stat.name}")
            }
            is IrInternalScope -> {
                appendLine("${indent}InternalScope of ${stat.internalVar}")
                appendBlock(stat, "$indent  ")
            }
            is IrBlock -> {
                appendLine("${indent}Block")
                appendBlock(stat, "$indent  ")
            }
            is IrScope -> {
                appendLine("${indent}Scope")
                for (symbol in stat.table.values) {
                    appendLine("${indent}   Of ${symbol.kind} ${symbol.variableForSet ?: symbol.name}")
                }
                appendBlock(stat, "$indent  ")
            }
            is IrTryCatch -> {
                appendLine("${indent}TryCatch")
                append(stat.tryBlock, "$indent  ")
                appendSSA(stat.postTry, "$indent  ")
                val catchFinallyIndent = "$indent    "

                for (conditional in stat.conditionals) {
                    appendLine("${indent}  Catch to ${conditional.variableForSet ?: conditional.variableName}")
                    appendSSA(conditional.ssaPhi, "$indent    ")
                    appendLine("${indent}    If")
                    append(conditional.condition, "$catchFinallyIndent  ")
                    append(conditional.block, catchFinallyIndent)
                }

                stat.simple?.let { simple ->
                    appendLine("${indent}  Catch to ${simple.variableForSet ?: simple.variableName}")
                    appendSSA(simple.ssaPhi, "$indent    ")
                    append(simple.block, catchFinallyIndent)
                }

                stat.finally?.let { finally ->
                    appendLine("${indent}  Finally")
                    appendSSA(finally.ssaPhi, "$indent    ")
                    append(finally.block, catchFinallyIndent)
                }
                appendSSA(stat.postTryCatchFinally, "$indent  ")
            }
        }
    }

    @Suppress("RemoveCurlyBracesFromTemplate")
    private fun Appendable.appendSSA(statement: IrStaticSingleAssignPhi, indent: String) {
        for ((index, scope) in statement.scopes?.withIndex() ?: emptyList()) {
            if (scope.isEmpty()) continue
            appendLine("${indent}Scope#$index")
            for (phi in scope) {
                appendLine("${indent}  ${phi.setTo}")
                for (setFrom in phi.setFrom.mapTo(mutableSetOf()) { it.value }) {
                    appendLine("${indent}    ${setFrom}")
                }
            }
        }
    }

    private fun Appendable.appendBlock(stat: IrBlockStatement, indent: String) {
        for (statement in stat.statements) append(statement, indent)
    }
}
