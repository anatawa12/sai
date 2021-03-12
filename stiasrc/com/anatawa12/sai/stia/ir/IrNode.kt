package com.anatawa12.sai.stia.ir

import com.anatawa12.autoVisitor.HasAccept
import com.anatawa12.sai.Token
import com.anatawa12.sai.ast.RegExpLiteral

sealed class IrNode {
    var parent: IrNode? = null
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

    POSTFIX_INCREMENT,
    PREFIX_INCREMENT,
    POSTFIX_DECREMENT,
    PREFIX_DECREMENT,
}

// hand written
sealed class IrLiteral<T>(val value: T) : IrExpression()

@HasAccept("visitNumberLiteral", IrExpression::class)
class IrNumberLiteral(value: Double) : IrLiteral<Double>(value) {
    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitNumberLiteral(this, arg)
}

@HasAccept("visitStringLiteral", IrExpression::class)
class IrStringLiteral(value: String) : IrLiteral<String>(value) {
    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitStringLiteral(this, arg)
}

@HasAccept("visitNullLiteral", IrExpression::class)
class IrNullLiteral : IrLiteral<Nothing?>(null) {
    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitNullLiteral(this, arg)
}

@HasAccept("visitBooleanLiteral", IrExpression::class)
class IrBooleanLiteral(value: Boolean) : IrLiteral<Boolean>(value) {
    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitBooleanLiteral(this, arg)
}

@HasAccept("visitRegexpLiteral", IrExpression::class)
class IrRegexpLiteral(value: RegExpLiteral) : IrLiteral<RegExpLiteral>(value) {
    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitRegexpLiteral(this, arg)
}

// convert java exception to js object

enum class VariableKind {
    VAR,
    LET,
    CONST,
    ;

    companion object {
        fun getByNodeType(type: Int) = when (type) {
            Token.VAR -> VAR
            Token.LET -> LET
            Token.CONST -> CONST
            else -> error("not a valid variable decl type: $type")
        }
    }
}

// hand written
sealed class IrBlockStatement(val statements: List<IrStatement>) : IrStatement()

@HasAccept("visitInternalScope", IrStatement::class)
class IrInternalScope(statements: List<IrStatement>, val internalVar: IrInternalVariableId) : IrBlockStatement(statements) {
    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitInternalScope(this, arg)
}

@HasAccept("visitBlock", IrStatement::class)
class IrBlock(statements: List<IrStatement>) : IrBlockStatement(statements) {
    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitBlock(this, arg)
}

@HasAccept("visitScope", IrStatement::class)
class IrScope(statements: List<IrStatement>, val table: Map<String, IrSymbol>) : IrBlockStatement(statements) {
    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitScope(this, arg)
}

class IrSymbol(val name: String, val kind: VariableKind)
class IrInternalVariableId()
