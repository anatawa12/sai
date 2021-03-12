package com.anatawa12.sai.stia.ir

import com.anatawa12.autoVisitor.GenerateAccept
import com.anatawa12.autoVisitor.HasVisitor
import com.anatawa12.sai.Token
import com.anatawa12.sai.ast.RegExpLiteral
// TODO: use code generator and add parent property
//*
@GenerateAccept
@HasVisitor(
    visitorType = IrVisitor::class,
    hasCustomDataParam = true,
    acceptName = "accept",
    subclasses = [
        IrNode::class,
        IrExpression::class,
        IrBinaryOperator::class,
        IrUnaryOperator::class,
        IrGetProperty::class,
        IrSetProperty::class,
        IrNewOrCall::class,
        IrLiteral::class,
        IrNumberLiteral::class,
        IrStringLiteral::class,
        IrNullLiteral::class,
        IrBooleanLiteral::class,
        IrRegexpLiteral::class,
        IrLocalLoad::class,
        IrCommaExpr::class,
        IrConditional::class,
        IrSetName::class,
        IrGetName::class,
        IrStatement::class,
        IrJumpTarget::class,
        IrReturn::class,
        IrGoto::class,
        IrJsr::class,
        IrIfFalse::class,
        IrIfTrue::class,
        IrBreak::class,
        IrContinue::class,
        IrSwitch::class,
        IrVariableDecl::class,
        IrThrow::class,
        IrRethrow::class,
        IrEmptyStatement::class,
        IrExpressionStatement::class,
        IrFunctionStatement::class,
        IrBlockStatement::class,
        IrBlock::class,
        IrScope::class,
    ],
)
// */
sealed class IrNode {

}

sealed class IrExpression : IrNode()
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
data class IrBinaryOperator(val type: IrBinaryOperatorType, val left: IrExpression, val right: IrExpression) : IrExpression()

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
data class IrUnaryOperator(val type: IrUnaryOperatorType, val expr: IrExpression) : IrExpression()

data class IrGetProperty(val owner: IrExpression, val name: IrExpression, val isProp: Boolean) : IrExpression()
data class IrSetProperty(val owner: IrExpression, val name: IrExpression, val value: IrExpression, val isProp: Boolean) : IrExpression()

data class IrNewOrCall(val function: IrExpression, val args: List<IrExpression>, val isNewInstance: Boolean) : IrExpression()

sealed class IrLiteral<T>(val value: T) : IrExpression()
class IrNumberLiteral(value: Double) : IrLiteral<Double>(value)
class IrStringLiteral(value: String) : IrLiteral<String>(value)
class IrNullLiteral : IrLiteral<Nothing?>(null)
class IrBooleanLiteral(value: Boolean) : IrLiteral<Boolean>(value)
class IrRegexpLiteral(value: RegExpLiteral) : IrLiteral<RegExpLiteral>(value)

// TODO: change id type
data class IrLocalLoad(val id: Any) : IrExpression()

data class IrCommaExpr(val exprs: List<IrExpression>) : IrExpression()

data class IrConditional(val condition: IrExpression, val ifTrue: IrExpression, val ifFalse: IrExpression) : IrExpression()

data class IrSetName(val name: String, val value: IrExpression) : IrExpression()
data class IrGetName(val name: String) : IrExpression()

// convert java exception to js object
data class IrConvertException(val javaException: IrExpression) : IrExpression()

sealed class IrStatement : IrNode()
class IrJumpTarget() : IrStatement()
data class IrReturn(val value: IrExpression?) : IrStatement()
data class IrGoto(val target: IrJumpTarget) : IrStatement()
data class IrJsr(val target: IrJumpTarget) : IrStatement()
data class IrIfFalse(val condition: IrExpression, val target: IrJumpTarget) : IrStatement()
data class IrIfTrue(val condition: IrExpression, val target: IrJumpTarget) : IrStatement()
data class IrBreak(val target: IrJumpTarget) : IrStatement()
data class IrContinue(val target: IrJumpTarget) : IrStatement()
data class IrSwitch(val expr: IrExpression, val cases: List<Pair<IrExpression, IrJumpTarget>>) : IrStatement()

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
class IrVariableDecl(val variables: List<Pair<String, IrExpression?>>, val kind: VariableKind) : IrStatement()

data class IrThrow(val exception: IrExpression) : IrStatement()
data class IrRethrow(val id: Any) : IrStatement()

class IrEmptyStatement() : IrStatement()
data class IrExpressionStatement(val expr: IrExpression) : IrStatement()

class IrFunctionStatement(/* TODO */) : IrStatement()

sealed class IrBlockStatement(val statements: List<IrStatement>) : IrStatement()
class IrBlock(statements: List<IrStatement>) : IrBlockStatement(statements)
class IrScope(statements: List<IrStatement>, val table: Map<String, IrSymbol>) : IrBlockStatement(statements)

class IrSymbol(val name: String, val kind: VariableKind)

