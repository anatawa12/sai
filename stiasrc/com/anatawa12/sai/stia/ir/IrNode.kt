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

sealed class IrVariableInfo(val name: String) {
    abstract val usedBy: Set<GettingVariableInfoDelegate<*>>
    abstract val setBy: Set<SettingVariableInfoDelegate<*>>
    abstract fun onUnusedBy(ref: GettingVariableInfoDelegate<*>)
    abstract fun onUsedBy(ref: GettingVariableInfoDelegate<*>)
    abstract fun onUnsetBy(ref: SettingVariableInfoDelegate<*>)
    abstract fun onSetBy(ref: SettingVariableInfoDelegate<*>)

    abstract override fun toString(): String
}

@Suppress("CanSealedSubClassBeObject")
class IrInFunctionVariable(name: String, val revision: Int) : IrVariableInfo(name) {
    var setBySingle: SettingVariableInfoDelegate<*>? = null
    override val setBy: Set<SettingVariableInfoDelegate<*>> get() = setOfNotNull(setBySingle)
    override val usedBy = mutableSetOf<GettingVariableInfoDelegate<*>>()

    override fun onUnusedBy(ref: GettingVariableInfoDelegate<*>) {
        check(usedBy.remove(ref)) { "$this is not used by $ref" }
    }

    override fun onUsedBy(ref: GettingVariableInfoDelegate<*>) {
        check(setBySingle != null) { "$this is not assigned" }
        usedBy += ref
    }

    override fun onUnsetBy(ref: SettingVariableInfoDelegate<*>) {
        check(usedBy.isEmpty()) { "unsetting $this is allowed only if this is not used" }
        setBySingle = null
    }

    override fun onSetBy(ref: SettingVariableInfoDelegate<*>) {
        check(setBySingle == null) { "$this have already set by $setBySingle" }
        setBySingle = ref
    }

    fun replaceTo(replaceWith: IrInFunctionVariable) {
        for (usedBy in usedBy.toList()) {
            @Suppress("UNCHECKED_CAST")
            (usedBy as GettingVariableInfoDelegate<IrInFunctionVariable>).value = replaceWith
        }
        check(usedBy.isEmpty())
    }

    override fun toString(): String = "InFunction($name#$revision)"
}

@Suppress("CanSealedSubClassBeObject")
class IrOuterVariable(name: String) : IrVariableInfo(name) {
    override val setBy = mutableSetOf<SettingVariableInfoDelegate<*>>()
    override val usedBy = mutableSetOf<GettingVariableInfoDelegate<*>>()

    override fun onUnusedBy(ref: GettingVariableInfoDelegate<*>) {
        check(usedBy.remove(ref)) { "$this is not used by $ref" }
    }

    override fun onUsedBy(ref: GettingVariableInfoDelegate<*>) {
        usedBy += ref
    }

    override fun onUnsetBy(ref: SettingVariableInfoDelegate<*>) {
        check(setBy.remove(ref)) { "$this is not used by $ref" }
    }

    override fun onSetBy(ref: SettingVariableInfoDelegate<*>) {
        setBy += ref
    }
    override fun toString(): String = "Outer($name)"
}

interface IrGettingName
interface IrGettingNameExpression : IrGettingName {
    val variableName: String
    var variableForGet: IrVariableInfo?
}

interface IrSettingName
interface IrSettingNameExpression : IrSettingName {
    val variableName: String
    var variableForSet: IrVariableInfo?
}

@HasAccept("visitSetName", IrExpression::class)
class IrSetName(
    val name: String,
    value: IrExpression,
) : IrExpression(), IrSettingNameExpression {
    var value: IrExpression by nodeDelegateOfIrExpression()

    override val variableName: String get() = name
    override var variableForSet by SettingVariableInfoDelegate()

    init {
        this.value = value
    }

    @Suppress("OVERRIDE_BY_INLINE")
    override inline fun runWithChildExpressions(func: (IrExpression) -> Unit) {
        func(value)
    }

    override fun toString() = "IrSetName(name=$name, value=$value)"

    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitSetName(this, arg)
}

@HasAccept("visitGetName", IrExpression::class)
class IrGetName(
    val name: String,
) : IrExpression(), IrGettingNameExpression {
    override val variableName: String get() = name
    override var variableForGet by GettingVariableInfoDelegate()

    @Suppress("OVERRIDE_BY_INLINE")
    override inline fun runWithChildExpressions(func: (IrExpression) -> Unit) {
    }

    override fun toString() = "IrGetName(name=$name)"

    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitGetName(this, arg)
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
) : IrIncDec(isDecrement, isSuffix), IrGettingNameExpression, IrSettingNameExpression {
    override val variableName: String get() = name
    override var variableForGet by GettingVariableInfoDelegate()
    override var variableForSet by SettingVariableInfoDelegate()

    @Suppress("OVERRIDE_BY_INLINE")
    override inline fun runWithChildExpressions(func: (IrExpression) -> Unit) {}

    override fun toString() = "IrNameIncDec(name=$name, kind=${incDecInfo()})"

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

    @Suppress("OVERRIDE_BY_INLINE")
    override inline fun runWithChildExpressions(func: (IrExpression) -> Unit) {
        func(owner)
        func(name)
    }

    override fun toString() = "IrPropertyIncDec(owner=$owner, name=$name, isProp=$isProp, kind=${incDecInfo()})"

    override fun <R, T> accept(visitor: IrExpressionVisitor<R, T>, arg: T): R = visitor.visitPropertyIncDec(this, arg)
}

// hand written
sealed class IrLiteral<T>(val value: T) : IrExpression() {
    @Suppress("OVERRIDE_BY_INLINE")
    final override inline fun runWithChildExpressions(func: (IrExpression) -> Unit) {}
}

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
