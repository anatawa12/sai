package com.anatawa12.sai.stia.types

import com.anatawa12.sai.Node
import com.anatawa12.sai.ScriptRuntime
import com.anatawa12.sai.Token
import com.anatawa12.sai.Undefined
import com.anatawa12.sai.ast.Name
import com.anatawa12.sai.stia.*
import com.anatawa12.sai.stia.InternalPropMap
import com.anatawa12.sai.stia.internalProps

typealias ResolvedTypeHandler = ValueInfo.(ResolvedTypeKind, ResolvingType) -> Unit

enum class ResolvedTypeKind {
    ExactlyType,
    CurrentlyType,
}

sealed class ValueInfo {
    abstract fun onResolvedType(kind: ResolvedTypeKind?, block: ResolvedTypeHandler)
    abstract fun <T> onValue(type: CompileTimeConstantType<T>, block: (T) -> Unit)
    abstract fun onValue(type: Nothing?, block: (Any?) -> Unit)

    // if type is BooleanType, the value may be casted value.
    abstract fun <T : Any> getValueOrNone(type: CompileTimeConstantType<T>): Option<T>
    abstract fun getValueOrNone(type: Nothing?): Option<Any?>
    abstract override fun toString(): String
    abstract fun assignedFrom(from: ValueInfo)

    fun assignTo(to: ValueInfo) {
        to.assignedFrom(this)
    }

    // expecting types
    protected val expectTypes = mutableSetOf<ResolvingType>()
    private val expectHandler = Event<ResolvingType>()

    fun expect(type: ResolvingType) {
        if (expectTypes.add(type))
            expectHandler.call(type)
    }

    fun onExpect(block: (ResolvingType) -> Unit) {
        expectTypes.forEach(block)
        expectHandler.on(block)
    }
}

private class ResolvedTypeAndValueController(private val of: ValueInfo) {
    // resolved types
    var resolvedType: ResolvingType? = null
    var resolvedTypeKind: ResolvedTypeKind? = null

    // null iff ExactlyType
    private var resolvedTypeHandler: Event<ValueInfo>? = Event()

    fun setResolvedType(kind: ResolvedTypeKind, type: ResolvingType) {
        if (resolvedTypeKind == ResolvedTypeKind.ExactlyType) {
            check(resolvedType == type) { "already exactly type is assigned" }
            return
        }

        resolvedTypeKind = kind
        resolvedType = type
        resolvedTypeHandler!!.call(of)
    }

    fun onResolvedType(kind: ResolvedTypeKind?, block: ResolvedTypeHandler) {
        if (resolvedTypeKind == ResolvedTypeKind.ExactlyType) {
            // resolvedTypeKind is ResolvedTypeKind.ExactlyType now.
            callResolvedTypeHandler(ResolvedTypeKind.ExactlyType, kind, block)
        } else {
            resolvedTypeHandler!!.on {
                callResolvedTypeHandler(resolvedTypeKind!!, kind, block)
            }
        }
    }

    private fun callResolvedTypeHandler(
        curKind: ResolvedTypeKind,
        kind: ResolvedTypeKind?,
        block: ResolvedTypeHandler,
    ) {
        if (kind == null || curKind == kind)
            block.invoke(of, curKind, resolvedType!!)
    }

    // values
    var exactlyValue: Option<Any?> = Option.none()
    private var exactlyValueHandler: Event<Any?>? = Event()

    fun setExactlyValue(value: Any?) {
        require(exactlyValue.isNone()) { "already exactly value is assigned" }
        val type = ResolvingType.byCompileTimeValue(value)
        setResolvedType(ResolvedTypeKind.ExactlyType, type)
        exactlyValue = value.asSome()
        exactlyValueHandler!!.call(value)
        exactlyValueHandler = null
    }

    // if type is BooleanType, the value may be casted value.
    fun <T> onValue(type: CompileTimeConstantType<T>, block: (T) -> Unit) {
        if (exactlyValueHandler == null) {
            callValueHandlerWithType(type, exactlyValue.getOrThrow(), block)
        } else {
            exactlyValueHandler!!.on {
                callValueHandlerWithType(type, this, block)
            }
        }
    }

    fun onValue(@Suppress("UNUSED_PARAMETER") type: Nothing?, block: (Any?) -> Unit) {
        if (exactlyValueHandler == null) {
            exactlyValue.map { block(it) }
        } else {
            exactlyValueHandler!!.on(block)
        }
    }

    private fun <T> callValueHandlerWithType(
        type: CompileTimeConstantType<T>,
        value: Any?,
        block: (T) -> Unit,
    ) {
        castValueTo(value, type).map(block)
    }
}

class SSAValueInfo : ValueInfo() {
    // resolved types
    private val resolvedsController = ResolvedTypeAndValueController(this)
    private val resolvedType by resolvedsController::resolvedType
    private val resolvedTypeKind by resolvedsController::resolvedTypeKind

    fun setResolvedType(kind: ResolvedTypeKind, type: ResolvingType) {
        resolvedsController.setResolvedType(kind, type)
    }

    override fun onResolvedType(kind: ResolvedTypeKind?, block: ResolvedTypeHandler) {
        resolvedsController.onResolvedType(kind, block)
    }

    // values
    private val exactlyValue get() = resolvedsController.exactlyValue

    fun exactlyValue(value: Any?) {
        resolvedsController.setExactlyValue(value)
    }

    // if type is BooleanType, the value may be casted value.
    override fun <T> onValue(type: CompileTimeConstantType<T>, block: (T) -> Unit) {
        resolvedsController.onValue(type, block)
    }

    override fun onValue(type: Nothing?, block: (Any?) -> Unit) {
        resolvedsController.onValue(type, block)
    }

    fun exactly(type: ResolvingType) = setResolvedType(ResolvedTypeKind.ExactlyType, type)
    fun currently(type: ResolvingType) = setResolvedType(ResolvedTypeKind.CurrentlyType, type)

    override fun <T : Any> getValueOrNone(type: CompileTimeConstantType<T>): Option<T> {
        return exactlyValue.flatMap { castValueTo(it, type) }
    }

    override fun getValueOrNone(type: Nothing?): Option<Any?> = exactlyValue

    override fun toString(): String {
        return buildString {
            append("SSAValueInfo(")
            if (resolvedType != null) {
                append(resolvedTypeKind)
                append(": ")
                append(resolvedType)
                append(",")
            }
            exactlyValue.map {
                append(it)
                append(",")
            }
            append("expects ")
            append(expectTypes)
            append(")")
        }
    }

    override fun assignedFrom(from: ValueInfo) {
        val to = this
        from.onValue(null) {
            to.exactlyValue(it)
        }
        from.onResolvedType(null) { kind, type ->
            to.setResolvedType(kind, type)
        }
        to.onExpect {
            from.expect(it)
        }
    }
}

/**
 * Re-Assign-Able value info
 */
class RSAValueInfo : ValueInfo() {
    // resolved types
    private val resolvedsController = ResolvedTypeAndValueController(this)

    override fun onResolvedType(kind: ResolvedTypeKind?, block: ResolvedTypeHandler) {
        resolvedsController.onResolvedType(kind, block)
    }

    // if type is BooleanType, the value may be casted value.
    override fun <T> onValue(type: CompileTimeConstantType<T>, block: (T) -> Unit) {
        resolvedsController.onValue(type, block)
    }

    override fun onValue(type: Nothing?, block: (Any?) -> Unit) {
        resolvedsController.onValue(type, block)
    }

    override fun <T : Any> getValueOrNone(type: CompileTimeConstantType<T>): Option<T> {
        return Option.none()
    }

    override fun getValueOrNone(type: Nothing?): Option<Any?> = Option.none()

    override fun toString(): String {
        return buildString {
            append("RSAValueInfo(")
            append("assigned by ")
            append(assignedFrom.size)
            append(" places, ")
            if (resolvedsController.resolvedType != null) {
                append(resolvedsController.resolvedTypeKind)
                append(": ")
                append(resolvedsController.resolvedType)
                append(",")
            }
            resolvedsController.exactlyValue.map {
                append(it)
                append(",")
            }
            append("expects ")
            append(expectTypes)
            append(")")
        }
    }

    private val assignedFrom = mutableListOf<ValueInfo>()
    private val resolvedValues = mutableListOf<Any?>()
    private val exactlyTypes = mutableListOf<ResolvingType>()
    override fun assignedFrom(from: ValueInfo) {
        val to = this
        to.assignedFrom += from
        from.onValue(null) {
            resolvedValues += it
        }
        from.onResolvedType(ResolvedTypeKind.ExactlyType) { _, type ->
            exactlyTypes += type
        }
        to.onExpect {
            from.expect(it)
        }
    }

    /**
     * @return is modified
     */
    internal fun postProcess(): Boolean {
        if (assignedFrom.isEmpty()) return false
        var modified = false
        if (assignedFrom.size == exactlyTypes.size) {
            if (resolvedsController.resolvedTypeKind != ResolvedTypeKind.ExactlyType) {
                if (exactlyTypes.size == 1) {
                    resolvedsController.setResolvedType(ResolvedTypeKind.ExactlyType, exactlyTypes.single())
                    modified = true
                }
            }
            // do not include 2
        }
        return modified
    }
}

private val valueInfoKey = InternalPropMap.Key<ValueInfo>("valueInfo")
var Node.valueInfo: ValueInfo
    get() {
        if (isJumpTarget) error("target doesn't have valueInfo")
        val value = if (hasValueInfoInVarId) {
            (this as Name).varId.valueInfo
        } else {
            internalProps[valueInfoKey]
        }
        return value ?: error("not yet initialized for ${this.toInformationString()}")
    }
    set(value) {
        if (isJumpTarget) error("target doesn't have valueInfo")
        if (hasValueInfoInVarId) {
            error("assigning valueInfo for Name is not allowed")
        } else {
            internalProps[valueInfoKey] = value
        }
    }

private fun <T> castValueTo(value: Any?, type: CompileTimeConstantType<T>): Option<T> {
    @Suppress("UNCHECKED_CAST")
    return when (type) {
        NullType -> if (value == null) null.asSome() else Option.none()
        UndefinedType -> if (value == Undefined.instance) value.asSome() else Option.none()
        NumberType -> runCatching { ScriptRuntime.toNumber(value) }.getOrNone()
        BooleanType -> runCatching { ScriptRuntime.toBoolean(value) }.getOrNone()
        StringType -> runCatching { ScriptRuntime.toString(value) }.getOrNone()
        else -> error("")
    } as Option<T>
}

private val Node.hasValueInfoInVarId: Boolean
        get() = type == Token.NAME || type == Token.BINDNAME
