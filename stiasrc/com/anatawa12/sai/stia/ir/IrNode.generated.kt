/**
 * Generated by IrNode.kts
 * Do not modify manually
 */

package com.anatawa12.sai.stia.ir

import com.anatawa12.autoVisitor.HasAccept
import com.anatawa12.autoVisitor.HasVisitor

@HasAccept("visitBinaryOperator", IrExpression::class)
class IrBinaryOperator(
    val type: IrBinaryOperatorType,
    left: IrExpression,
    right: IrExpression,
) : IrExpression() {
    var left: IrExpression by nodeDelegateOfIrExpression()
    var right: IrExpression by nodeDelegateOfIrExpression()

    init {
        this.left = left
        this.right = right
    }

    override fun toString() = "IrBinaryOperator(" +
        "type=$type, " +
        "left=$left, " +
        "right=$right, " +
    ")"

    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitBinaryOperator(this, arg)
}

@HasAccept("visitUnaryOperator", IrExpression::class)
class IrUnaryOperator(
    val type: IrUnaryOperatorType,
    expr: IrExpression,
) : IrExpression() {
    var expr: IrExpression by nodeDelegateOfIrExpression()

    init {
        this.expr = expr
    }

    override fun toString() = "IrUnaryOperator(" +
        "type=$type, " +
        "expr=$expr, " +
    ")"

    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitUnaryOperator(this, arg)
}

@HasAccept("visitGetProperty", IrExpression::class)
class IrGetProperty(
    owner: IrExpression,
    name: IrExpression,
    val isProp: Boolean,
) : IrExpression() {
    var owner: IrExpression by nodeDelegateOfIrExpression()
    var name: IrExpression by nodeDelegateOfIrExpression()

    init {
        this.owner = owner
        this.name = name
    }

    override fun toString() = "IrGetProperty(" +
        "owner=$owner, " +
        "name=$name, " +
        "isProp=$isProp, " +
    ")"

    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitGetProperty(this, arg)
}

@HasAccept("visitSetProperty", IrExpression::class)
class IrSetProperty(
    owner: IrExpression,
    name: IrExpression,
    value: IrExpression,
    val isProp: Boolean,
) : IrExpression() {
    var owner: IrExpression by nodeDelegateOfIrExpression()
    var name: IrExpression by nodeDelegateOfIrExpression()
    var value: IrExpression by nodeDelegateOfIrExpression()

    init {
        this.owner = owner
        this.name = name
        this.value = value
    }

    override fun toString() = "IrSetProperty(" +
        "owner=$owner, " +
        "name=$name, " +
        "value=$value, " +
        "isProp=$isProp, " +
    ")"

    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitSetProperty(this, arg)
}

@HasAccept("visitNewOrCall", IrExpression::class)
class IrNewOrCall(
    function: IrExpression,
    args: List<IrExpression>,
    val isNewInstance: Boolean,
) : IrExpression() {
    var function: IrExpression by nodeDelegateOfIrExpression()
    private val argsDelegate = nodeListDelegateOfIrExpression()
    val args: MutableList<IrExpression> by argsDelegate
    fun setArgs(args: List<IrExpression>) = argsDelegate.set(args)

    init {
        this.function = function
        setArgs(args)
    }

    override fun toString() = "IrNewOrCall(" +
        "function=$function, " +
        "args=$args, " +
        "isNewInstance=$isNewInstance, " +
    ")"

    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitNewOrCall(this, arg)
}

@HasAccept("visitCommaExpr", IrExpression::class)
class IrCommaExpr(
    exprs: List<IrExpression>,
) : IrExpression() {
    private val exprsDelegate = nodeListDelegateOfIrExpression()
    val exprs: MutableList<IrExpression> by exprsDelegate
    fun setExprs(exprs: List<IrExpression>) = exprsDelegate.set(exprs)

    init {
        setExprs(exprs)
    }

    override fun toString() = "IrCommaExpr(" +
        "exprs=$exprs, " +
    ")"

    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitCommaExpr(this, arg)
}

@HasAccept("visitConditional", IrExpression::class)
class IrConditional(
    condition: IrExpression,
    ifTrue: IrExpression,
    ifFalse: IrExpression,
) : IrExpression() {
    var condition: IrExpression by nodeDelegateOfIrExpression()
    var ifTrue: IrExpression by nodeDelegateOfIrExpression()
    var ifFalse: IrExpression by nodeDelegateOfIrExpression()

    init {
        this.condition = condition
        this.ifTrue = ifTrue
        this.ifFalse = ifFalse
    }

    override fun toString() = "IrConditional(" +
        "condition=$condition, " +
        "ifTrue=$ifTrue, " +
        "ifFalse=$ifFalse, " +
    ")"

    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitConditional(this, arg)
}

@HasAccept("visitSetName", IrExpression::class)
class IrSetName(
    val name: String,
    value: IrExpression,
) : IrExpression() {
    var value: IrExpression by nodeDelegateOfIrExpression()

    init {
        this.value = value
    }

    override fun toString() = "IrSetName(" +
        "name=$name, " +
        "value=$value, " +
    ")"

    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitSetName(this, arg)
}

@HasAccept("visitGetName", IrExpression::class)
class IrGetName(
    val name: String,
) : IrExpression() {

    init {
    }

    override fun toString() = "IrGetName(" +
        "name=$name, " +
    ")"

    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitGetName(this, arg)
}

// convert java exception to js object
@HasAccept("visitConvertException", IrExpression::class)
class IrConvertException(
    val internalVar: IrInternalVariableId,
) : IrExpression() {

    init {
    }

    override fun toString() = "IrConvertException(" +
        "internalVar=$internalVar, " +
    ")"

    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitConvertException(this, arg)
}

@HasAccept("visitJumpTarget", IrStatement::class)
class IrJumpTarget(
) : IrStatement() {

    init {
    }

    override fun toString() = "IrJumpTarget(" +
    ")"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitJumpTarget(this, arg)
}

@HasAccept("visitReturn", IrStatement::class)
class IrReturn(
    value: IrExpression?,
) : IrStatement() {
    var value: IrExpression? by nodeDelegateOfIrExpressionNullable()

    init {
        this.value = value
    }

    override fun toString() = "IrReturn(" +
        "value=$value, " +
    ")"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitReturn(this, arg)
}

@HasAccept("visitGoto", IrStatement::class)
class IrGoto(
    val target: IrJumpTarget,
) : IrStatement() {

    init {
    }

    override fun toString() = "IrGoto(" +
        "target=$target, " +
    ")"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitGoto(this, arg)
}

@HasAccept("visitJsr", IrStatement::class)
class IrJsr(
    val target: IrJumpTarget,
) : IrStatement() {

    init {
    }

    override fun toString() = "IrJsr(" +
        "target=$target, " +
    ")"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitJsr(this, arg)
}

@HasAccept("visitIfFalse", IrStatement::class)
class IrIfFalse(
    condition: IrExpression,
    val target: IrJumpTarget,
) : IrStatement() {
    var condition: IrExpression by nodeDelegateOfIrExpression()

    init {
        this.condition = condition
    }

    override fun toString() = "IrIfFalse(" +
        "condition=$condition, " +
        "target=$target, " +
    ")"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitIfFalse(this, arg)
}

@HasAccept("visitIfTrue", IrStatement::class)
class IrIfTrue(
    condition: IrExpression,
    val target: IrJumpTarget,
) : IrStatement() {
    var condition: IrExpression by nodeDelegateOfIrExpression()

    init {
        this.condition = condition
    }

    override fun toString() = "IrIfTrue(" +
        "condition=$condition, " +
        "target=$target, " +
    ")"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitIfTrue(this, arg)
}

// is IrBreak needed? maybe can be IrGoto
@HasAccept("visitBreak", IrStatement::class)
class IrBreak(
    val target: IrJumpTarget,
) : IrStatement() {

    init {
    }

    override fun toString() = "IrBreak(" +
        "target=$target, " +
    ")"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitBreak(this, arg)
}

// is IrContinue needed? maybe can be IrGoto
@HasAccept("visitContinue", IrStatement::class)
class IrContinue(
    val target: IrJumpTarget,
) : IrStatement() {

    init {
    }

    override fun toString() = "IrContinue(" +
        "target=$target, " +
    ")"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitContinue(this, arg)
}

@HasAccept("visitSwitch", IrStatement::class)
class IrSwitch(
    expr: IrExpression,
    cases: List<Pair<IrExpression, IrJumpTarget>>,
) : IrStatement() {
    var expr: IrExpression by nodeDelegateOfIrExpression()
    private val casesDelegate = nodeListDelegateOfPairIrExpressionIrJumpTarget()
    val cases: MutableList<Pair<IrExpression, IrJumpTarget>> by casesDelegate
    fun setCases(cases: List<Pair<IrExpression, IrJumpTarget>>) = casesDelegate.set(cases)

    init {
        this.expr = expr
        setCases(cases)
    }

    override fun toString() = "IrSwitch(" +
        "expr=$expr, " +
        "cases=$cases, " +
    ")"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitSwitch(this, arg)
}

@HasAccept("visitVariableDecl", IrStatement::class)
class IrVariableDecl(
    variables: List<Pair<String, IrExpression?>>,
    val kind: VariableKind,
) : IrStatement() {
    private val variablesDelegate = nodeListDelegateOfPairStringIrExpression()
    val variables: MutableList<Pair<String, IrExpression?>> by variablesDelegate
    fun setVariables(variables: List<Pair<String, IrExpression?>>) = variablesDelegate.set(variables)

    init {
        setVariables(variables)
    }

    override fun toString() = "IrVariableDecl(" +
        "variables=$variables, " +
        "kind=$kind, " +
    ")"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitVariableDecl(this, arg)
}

@HasAccept("visitThrow", IrStatement::class)
class IrThrow(
    exception: IrExpression,
) : IrStatement() {
    var exception: IrExpression by nodeDelegateOfIrExpression()

    init {
        this.exception = exception
    }

    override fun toString() = "IrThrow(" +
        "exception=$exception, " +
    ")"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitThrow(this, arg)
}

@HasAccept("visitRethrow", IrStatement::class)
class IrRethrow(
    val internalVar: IrInternalVariableId,
) : IrStatement() {

    init {
    }

    override fun toString() = "IrRethrow(" +
        "internalVar=$internalVar, " +
    ")"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitRethrow(this, arg)
}

@HasAccept("visitEmptyStatement", IrStatement::class)
class IrEmptyStatement(
) : IrStatement() {

    init {
    }

    override fun toString() = "IrEmptyStatement(" +
    ")"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitEmptyStatement(this, arg)
}

@HasAccept("visitExpressionStatement", IrStatement::class)
class IrExpressionStatement(
    expr: IrExpression,
) : IrStatement() {
    var expr: IrExpression by nodeDelegateOfIrExpression()

    init {
        this.expr = expr
    }

    override fun toString() = "IrExpressionStatement(" +
        "expr=$expr, " +
    ")"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitExpressionStatement(this, arg)
}

// TODO: body
@HasAccept("visitFunctionStatement", IrStatement::class)
class IrFunctionStatement(
) : IrStatement() {

    init {
    }

    override fun toString() = "IrFunctionStatement(" +
    ")"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitFunctionStatement(this, arg)
}

@HasVisitor(
    visitorType = IrExpressionVisitor::class,
    hasCustomDataParam = true,
    acceptName = "accept",
    subclasses = [
        IrBinaryOperator::class,
        IrUnaryOperator::class,
        IrGetProperty::class,
        IrSetProperty::class,
        IrNewOrCall::class,
        IrCommaExpr::class,
        IrConditional::class,
        IrSetName::class,
        IrGetName::class,
        IrConvertException::class,
        IrNumberLiteral::class,
        IrStringLiteral::class,
        IrNullLiteral::class,
        IrBooleanLiteral::class,
        IrRegexpLiteral::class,
        IrIncDec::class,
        IrNameIncDec::class,
        IrPropertyIncDec::class,
    ]
)
@HasAccept("visitExpression", IrExpression::class)
sealed class IrExpression : IrNode() {
    abstract fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R
}

@HasVisitor(
    visitorType = IrStatementVisitor::class,
    hasCustomDataParam = true,
    acceptName = "accept",
    subclasses = [
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
        IrInternalScope::class,
        IrBlock::class,
        IrScope::class,
    ]
)
@HasAccept("visitStatement", IrStatement::class)
sealed class IrStatement : IrNode() {
    abstract fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R
}
