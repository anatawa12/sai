package com.anatawa12.sai.stia.ir

import com.anatawa12.autoVisitor.HasAccept
import com.anatawa12.sai.Token
import com.anatawa12.sai.ast.RegExpLiteral

sealed class IrNode {
    var parent: IrNode? = null

    abstract override fun toString(): String
}

enum class IrBinaryOperatorType {
    BITOR,
    BITXOR,
    BITAND,
    LSH,
    RSH,
    URSH,
    EQ,
    LT,
    LE,
    GT,
    GE,
    IN,
    INSTANCEOF,
    SHEQ,
    SHNE,
    SUB,
    MUL,
    DIV,
    MOD,
    ADD,
    OR,
    AND,
}

enum class IrUnaryOperatorType {
    NOT,
    BITNOT,
    POSITIVE,
    NEGATIVE,
    TYPEOF,
    VOID,
}

@HasAccept("visitIncDec", IrExpression::class)
sealed class IrIncDec(
    val isDecrement: Boolean,
    val isSuffix: Boolean,
) : IrExpression() {
    protected fun incDecInfo() = if (isDecrement) {
        if (isSuffix) "suf-dec"
        else "pre-dec"
    } else {
        if (isSuffix) "suf-inc"
        else "pre-inc"
    }
}

@HasAccept("visitNameIncDec", IrExpression::class)
class IrNameIncDec(
    val name: String,
    isDecrement: Boolean,
    isSuffix: Boolean,
) : IrIncDec(isDecrement, isSuffix) {

    init {
    }

    override fun toString() = "IrNameIncDec(" +
            "name=$name" +
            "kind=${incDecInfo()}" +
            ")"

    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitNameIncDec(this, arg)
}

@HasAccept("visitPropertyIncDec", IrExpression::class)
class IrPropertyIncDec(
    owner: IrExpression,
    name: IrExpression,
    val isProp: Boolean,
    isDecrement: Boolean,
    isSuffix: Boolean,
) : IrIncDec(isDecrement, isSuffix) {
    var owner: IrExpression by nodeDelegateOfIrExpression()
    var name: IrExpression by nodeDelegateOfIrExpression()

    init {
        this.owner = owner
        this.name = name
    }

    override fun toString() = "IrPropertyIncDec(" +
            "owner=$owner" +
            "name=$name" +
            "isProp=$isProp" +
            "kind=${incDecInfo()}" +
            ")"

    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitPropertyIncDec(this, arg)
}

// hand written
sealed class IrLiteral<T>(val value: T) : IrExpression()

@HasAccept("visitNumberLiteral", IrExpression::class)
class IrNumberLiteral(value: Double) : IrLiteral<Double>(value) {
    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitNumberLiteral(this, arg)
    override fun toString(): String = "IrNumberLiteral($value)"
}

@HasAccept("visitStringLiteral", IrExpression::class)
class IrStringLiteral(value: String) : IrLiteral<String>(value) {
    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitStringLiteral(this, arg)
    override fun toString(): String = "IrStringLiteral($value)"
}

@HasAccept("visitNullLiteral", IrExpression::class)
class IrNullLiteral : IrLiteral<Nothing?>(null) {
    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitNullLiteral(this, arg)
    override fun toString(): String = "IrNullLiteral($value)"
}

@HasAccept("visitBooleanLiteral", IrExpression::class)
class IrBooleanLiteral(value: Boolean) : IrLiteral<Boolean>(value) {
    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitBooleanLiteral(this, arg)
    override fun toString(): String = "IrBooleanLiteral($value)"
}

@HasAccept("visitRegexpLiteral", IrExpression::class)
class IrRegexpLiteral(value: RegExpLiteral) : IrLiteral<RegExpLiteral>(value) {
    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitRegexpLiteral(this, arg)
    override fun toString(): String = "IrRegexpLiteral(/${value.value}/${value.flags})"
}

// convert java exception to js object

enum class VariableKind {
    VAR,
    LET,
    CONST,
    FUNCTION,
    ARGUMENT,
    ;

    companion object {
        fun getByNodeType(type: Int) = when (type) {
            Token.VAR -> VAR
            Token.LET -> LET
            Token.CONST -> CONST
            Token.FUNCTION -> FUNCTION
            Token.LP -> ARGUMENT
            else -> error("not a valid variable decl type: $type")
        }
    }
}

// hand written
@HasAccept("visitBlockStatement", IrStatement::class)
sealed class IrBlockStatement(val statements: List<IrStatement>) : IrStatement() {
    override fun toString(): String = buildString { appendToString("") }.removeSuffix("\n")

    @Suppress("RemoveCurlyBracesFromTemplate")
    fun Appendable.appendToString(indent: String) {
        appendLine("${indent}${kindName()}(")
        val newIndent = "$indent  "
        appendAboutThis(newIndent)
        for (statement in statements) {
            if (statement is IrBlockStatement)
                statement.apply { appendToString(newIndent) }
            else
                appendLine("$newIndent$statement")
        }
        appendLine("${indent})")
    }

    protected abstract fun kindName(): String
    protected abstract fun Appendable.appendAboutThis(indent: String)
}

@HasAccept("visitInternalScope", IrStatement::class)
class IrInternalScope(statements: List<IrStatement>, val internalVar: IrInternalVariableId) : IrBlockStatement(statements) {
    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitInternalScope(this, arg)

    override fun kindName(): String = "IrInternalScope"
    override fun Appendable.appendAboutThis(indent: String) {
        appendLine("${indent}defines $internalVar")
    }
}

@HasAccept("visitBlock", IrStatement::class)
class IrBlock(statements: List<IrStatement>) : IrBlockStatement(statements) {
    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitBlock(this, arg)

    override fun kindName(): String = "IrBlock"
    override fun Appendable.appendAboutThis(indent: String) {
    }
}

@HasAccept("visitScope", IrStatement::class)
class IrScope(statements: List<IrStatement>, val table: Map<String, IrSymbol>) : IrBlockStatement(statements) {
    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitScope(this, arg)

    override fun kindName(): String = "IrScope"
    override fun Appendable.appendAboutThis(indent: String) {
        for ((_, value) in table) {
            appendLine("${indent}defines $value")
        }
    }
}

data class IrSymbol(val name: String, val kind: VariableKind)
class IrInternalVariableId()
