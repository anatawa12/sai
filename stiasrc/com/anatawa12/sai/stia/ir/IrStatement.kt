package com.anatawa12.sai.stia.ir

import com.anatawa12.autoVisitor.HasAccept
import com.anatawa12.autoVisitor.HasVisitor

class IrScopeSnapshot(val id: Int, val variable: List<IrInFunctionVariable>)

class IrAllScopeSnapshot(val scopes: List<IrScopeSnapshot>)

@HasAccept("visitJumpTarget", IrStatement::class)
class IrJumpTarget() : IrStatement() {
    val jumpFroms = mutableListOf<IrAllScopeSnapshot>()
    lateinit var realScope: IrAllScopeSnapshot

    @Suppress("OVERRIDE_BY_INLINE")
    override inline fun runWithChildExpressions(func: (IrExpression) -> Unit) {
    }

    override fun toString() = "IrJumpTarget()"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitJumpTarget(this, arg)
}

@HasAccept("visitReturn", IrStatement::class)
class IrReturn(value: IrExpression?) : IrStatement() {
    var value: IrExpression? by nodeDelegateOfIrExpressionNullable()

    init {
        this.value = value
    }

    @Suppress("OVERRIDE_BY_INLINE")
    override inline fun runWithChildExpressions(func: (IrExpression) -> Unit) {
        value?.let(func)
    }

    override fun toString() = "IrReturn(value=$value)"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitReturn(this, arg)
}

@HasAccept("visitSimpleJumping", IrStatement::class)
sealed class IrSimpleJumping(val target: IrJumpTarget) : IrStatement() {
    @Suppress("OVERRIDE_BY_INLINE")
    final override inline fun runWithChildExpressions(func: (IrExpression) -> Unit) {
    }
}

@HasAccept("visitGoto", IrStatement::class)
class IrGoto(target: IrJumpTarget) : IrSimpleJumping(target) {
    override fun toString() = "IrGoto(target=$target)"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitGoto(this, arg)
}

@HasAccept("visitJsr", IrStatement::class)
class IrJsr(target: IrJumpTarget) : IrSimpleJumping(target) {
    override fun toString() = "IrJsr(target=$target)"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitJsr(this, arg)
}

@HasAccept("visitIfFalse", IrStatement::class)
class IrIfFalse(
    condition: IrExpression,
    target: IrJumpTarget,
) : IrSimpleJumping(target) {
    var condition: IrExpression by nodeDelegateOfIrExpression()

    init {
        this.condition = condition
    }

    override fun toString() = "IrIfFalse(condition=$condition, target=$target)"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitIfFalse(this, arg)
}

@HasAccept("visitIfTrue", IrStatement::class)
class IrIfTrue(
    condition: IrExpression,
    target: IrJumpTarget,
) : IrSimpleJumping(target) {
    var condition: IrExpression by nodeDelegateOfIrExpression()

    init {
        this.condition = condition
    }

    override fun toString() = "IrIfTrue(condition=$condition, target=$target)"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitIfTrue(this, arg)
}

@HasAccept("visitSwitch", IrStatement::class)
class IrSwitch(
    expr: IrExpression,
    cases: List<Pair<IrExpression, IrJumpTarget>>,
) : IrStatement() {
    var expr: IrExpression by nodeDelegateOfIrExpression()
    private val casesDelegate = nodeListDelegateOf<Pair<IrExpression, IrJumpTarget>> { it.first }
    val cases: MutableList<Pair<IrExpression, IrJumpTarget>> by casesDelegate
    fun setCases(cases: List<Pair<IrExpression, IrJumpTarget>>) = casesDelegate.set(cases)

    init {
        this.expr = expr
        setCases(cases)
    }

    @Suppress("OVERRIDE_BY_INLINE")
    override inline fun runWithChildExpressions(func: (IrExpression) -> Unit) {
        func(expr)
        cases.forEach{ it.first.let(func) }
    }

    override fun toString() = "IrSwitch(expr=$expr, cases=$cases)"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitSwitch(this, arg)
}

@HasAccept("visitVariableDecl", IrStatement::class)
class IrVariableDecl(
    variables: List<Pair<String, IrExpression?>>,
    val kind: VariableKind,
) : IrStatement() {
    private val variablesDelegate = nodeListDelegateOf<Pair<String, IrExpression?>> { it.second }
    val variables: MutableList<Pair<String, IrExpression?>> by variablesDelegate
    fun setVariables(variables: List<Pair<String, IrExpression?>>) = variablesDelegate.set(variables)

    init {
        setVariables(variables)
    }

    @Suppress("OVERRIDE_BY_INLINE")
    override inline fun runWithChildExpressions(func: (IrExpression) -> Unit) {
        variables.forEach{ it.second?.let(func) }
    }

    override fun toString() = "IrVariableDecl(variables=$variables, kind=$kind)"

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

    @Suppress("OVERRIDE_BY_INLINE")
    override inline fun runWithChildExpressions(func: (IrExpression) -> Unit) {
        func(exception)
    }

    override fun toString() = "IrThrow(" +
            "exception=$exception" +
            ")"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitThrow(this, arg)
}

@HasAccept("visitRethrow", IrStatement::class)
class IrRethrow(val internalVar: IrInternalVariableId) : IrStatement() {
    @Suppress("OVERRIDE_BY_INLINE")
    override inline fun runWithChildExpressions(func: (IrExpression) -> Unit) {
    }

    override fun toString() = "IrRethrow(internalVar=$internalVar)"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitRethrow(this, arg)
}

@HasAccept("visitEmptyStatement", IrStatement::class)
class IrEmptyStatement() : IrStatement() {
    @Suppress("OVERRIDE_BY_INLINE")
    override inline fun runWithChildExpressions(func: (IrExpression) -> Unit) {
    }

    override fun toString() = "IrEmptyStatement()"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitEmptyStatement(this, arg)
}

@HasAccept("visitExpressionStatement", IrStatement::class)
class IrExpressionStatement(expr: IrExpression) : IrStatement() {
    var expr: IrExpression by nodeDelegateOfIrExpression()

    init {
        this.expr = expr
    }

    @Suppress("OVERRIDE_BY_INLINE")
    override inline fun runWithChildExpressions(func: (IrExpression) -> Unit) {
        func(expr)
    }

    override fun toString() = "IrExpressionStatement(expr=$expr)"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitExpressionStatement(this, arg)
}

// TODO: body
@HasAccept("visitFunctionStatement", IrStatement::class)
class IrFunctionStatement() : IrStatement() {
    @Suppress("OVERRIDE_BY_INLINE")
    override inline fun runWithChildExpressions(func: (IrExpression) -> Unit) {
    }

    override fun toString() = "IrFunctionStatement()"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitFunctionStatement(this, arg)
}

@HasAccept("visitSetThisFn", IrStatement::class)
class IrSetThisFn(val name: String) : IrStatement(), IrSettingName {
    var variableForSet by SettingVariableInfoDelegate()

    @Suppress("OVERRIDE_BY_INLINE")
    override inline fun runWithChildExpressions(func: (IrExpression) -> Unit) {
    }

    override fun toString() = "IrSetThisFn(name=$name)"

    override fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R = visitor.visitSetThisFn(this, arg)
}

// hand written
@HasAccept("visitBlockStatement", IrStatement::class)
sealed class IrBlockStatement(val statements: List<IrStatement>) : IrStatement() {
    @Suppress("OVERRIDE_BY_INLINE")
    final override inline fun runWithChildExpressions(func: (IrExpression) -> Unit) {}

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

@HasVisitor(
    visitorType = IrStatementVisitor::class,
    hasCustomDataParam = true,
    acceptName = "accept",
    subclasses = [
        IrReturn::class,
        IrSimpleJumping::class,
        IrGoto::class,
        IrJsr::class,
        IrIfFalse::class,
        IrIfTrue::class,
        IrSwitch::class,
        IrVariableDecl::class,
        IrThrow::class,
        IrRethrow::class,
        IrEmptyStatement::class,
        IrExpressionStatement::class,
        IrFunctionStatement::class,
        IrSetThisFn::class,
        IrBlockStatement::class,
        IrInternalScope::class,
        IrBlock::class,
        IrScope::class,
        IrJumpTarget::class,
    ]
)
@HasAccept("visitStatement", IrStatement::class)
sealed class IrStatement : IrNode() {
    abstract fun runWithChildExpressions(func: (IrExpression) -> Unit)
    abstract fun <R, T> accept(visitor: IrStatementVisitor<R, T>, arg: T): R
}

abstract class IrStatementVisitor<out R, in T> {
    open fun visitJumpTarget(node: IrJumpTarget, arg: T): R = visitStatement(node, arg)
    open fun visitReturn(node: IrReturn, arg: T): R = visitStatement(node, arg)
    open fun visitSimpleJumping(node: IrSimpleJumping, arg: T): R = visitStatement(node, arg)
    open fun visitGoto(node: IrGoto, arg: T): R = visitSimpleJumping(node, arg)
    open fun visitJsr(node: IrJsr, arg: T): R = visitSimpleJumping(node, arg)
    open fun visitIfFalse(node: IrIfFalse, arg: T): R = visitSimpleJumping(node, arg)
    open fun visitIfTrue(node: IrIfTrue, arg: T): R = visitSimpleJumping(node, arg)
    open fun visitSwitch(node: IrSwitch, arg: T): R = visitStatement(node, arg)
    open fun visitVariableDecl(node: IrVariableDecl, arg: T): R = visitStatement(node, arg)
    open fun visitThrow(node: IrThrow, arg: T): R = visitStatement(node, arg)
    open fun visitRethrow(node: IrRethrow, arg: T): R = visitStatement(node, arg)
    open fun visitEmptyStatement(node: IrEmptyStatement, arg: T): R = visitStatement(node, arg)
    open fun visitExpressionStatement(node: IrExpressionStatement, arg: T): R = visitStatement(node, arg)
    open fun visitFunctionStatement(node: IrFunctionStatement, arg: T): R = visitStatement(node, arg)
    open fun visitSetThisFn(node: IrSetThisFn, arg: T): R = visitStatement(node, arg)
    open fun visitBlockStatement(node: IrBlockStatement, arg: T): R = visitStatement(node, arg)
    open fun visitInternalScope(node: IrInternalScope, arg: T): R = visitBlockStatement(node, arg)
    open fun visitBlock(node: IrBlock, arg: T): R = visitBlockStatement(node, arg)
    open fun visitScope(node: IrScope, arg: T): R = visitBlockStatement(node, arg)
    abstract fun visitStatement(node: IrStatement, arg: T): R
}
