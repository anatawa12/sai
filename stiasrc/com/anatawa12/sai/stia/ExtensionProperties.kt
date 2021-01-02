package com.anatawa12.sai.stia

import com.anatawa12.sai.Node
import com.anatawa12.sai.Token
import com.anatawa12.sai.ast.Jump
import com.anatawa12.sai.ast.Name
import com.anatawa12.sai.ast.Scope
import java.util.function.BiConsumer
import kotlin.reflect.KProperty

internal val Node.internalProps: InternalPropMap
    get() {
        val got = getProp(Node.STIA_INTERNAL_PROP)
        if (got != null)
            return got as InternalPropMap
        val value = InternalPropMap()
        putProp(Node.STIA_INTERNAL_PROP, value)
        return value
    }

internal class InternalPropMap : BiConsumer<String, String> {
    private val props = mutableListOf<Entry<*>>()

    fun <T> getOrNull(key: Key<T>): T? {
        return props.firstOrNull { it.key == key }?.valueAs<T>()
    }

    inline fun <T> getOrCompute(key: Key<T>, compute: () -> T): T {
        return getOrNull(key) ?: kotlin.run {
            val value = compute()
            put(key, value)
            value
        }
    }

    fun <T> put(key: Key<T>, value: T) {
        @Suppress("UNCHECKED_CAST")
        val entry = props.firstOrNull { it.key == key } as Entry<T>?
        if (entry == null)
            props.add(Entry(key, value))
        else
            entry.value = value
    }

    fun remove(key: Key<*>) {
        props.removeIf { it.key == key }
    }

    @Suppress("NOTHING_TO_INLINE")
    inline operator fun <T> set(key: Key<T>, value: T) = put(key, value)
    @Suppress("NOTHING_TO_INLINE")
    inline operator fun <T> get(key: Key<T>): T? = getOrNull(key)

    override fun accept(indent: String, forLast: String) {
        val it = props.iterator()
        while (it.hasNext()) {
            val item = it.next()
            if (it.hasNext())
                print(indent)
            else
                print(forLast)
            print(item.key.name)
            print(": ")
            val value = item.value
            if (value is Array<*>) print(value.contentDeepToString())
            else print(value)
            print("(")
            print(value?.javaClass)
            print(":")
            print(value.shortHash())
            print(")")
            println()
        }
    }

    private class Entry<T>(val key: Key<T>, var value: T) {
        @Suppress("UNCHECKED_CAST")
        fun <U> valueAs(): U? = value as U
    }

    class Key<T>(val name: String) {
        fun computing(compute: () -> T) = ComputingProp(this, {}, compute)
        fun computing(compute: () -> T, condition: (Node) -> Unit) = ComputingProp(this, condition, compute)
    }

    class ComputingProp<T>(
        val key: Key<T>,
        val condition: (Node) -> Unit,
        val compute: () -> T
    ) {
        @Suppress("NOTHING_TO_INLINE")
        inline operator fun getValue(thisRef: Node, property: KProperty<*>): T {
            return getValue(thisRef)
        }

        @Suppress("NOTHING_TO_INLINE")
        inline operator fun setValue(thisRef: Node, property: KProperty<*>, value: T) {
            setValue(thisRef, value)
        }

        fun getValue(thisRef: Node): T {
            condition(thisRef)
            val got = thisRef.internalProps[key]
            if (got != null) {
                return got
            }
            val value = compute()
            thisRef.internalProps[key] = value
            return value
        }

        fun setValue(thisRef: Node, value: T) {
            condition(thisRef)
            thisRef.internalProps[key] = value
        }
    }
}

private val varIdKey = InternalPropMap.Key<VariableId>("varId")
fun Name.deleteVarId() = internalProps.remove(varIdKey)
var Name.varId: VariableId
    get() = internalProps[varIdKey]
        ?: error("${this.identifier}(${this.shortHash()}) doesn't have varId")
    set(value) {
        require(value.name == identifier)
        internalProps[varIdKey]?.usedBy?.remove(this)
        internalProps[varIdKey] = value
        value.usedBy.add(this)
    }

private val realLocalVarIdKey = InternalPropMap.Key<VariableId>("realLocalVarId")
fun Node.deleteRealLocalVarId() = internalProps.remove(realLocalVarIdKey)
val Node.realLocalVarIdOrNull: VariableId?
    get() {
        require(hasLocalBlockVariableRef) { "$type can't have realVarName" }
        return internalProps[realLocalVarIdKey]
    }
var Node.realLocalVarId: VariableId
    get() {
        require(hasLocalBlockVariableRef) { "$type can't have realVarName" }
        return internalProps[realLocalVarIdKey]
            ?: error("${this.type}(${this.shortHash()}) doesn't have realVarName")
    }
    set(value) {
        require(hasLocalBlockVariableRef) { "$type can't have realVarName" }
        require(type != Token.TRY) { "TRY can't have realVarName" } // use custom struct
        internalProps[realLocalVarIdKey] = value
    }
val Node.hasLocalBlockVariableRef: Boolean get() = when (this.type) {
    // declare
    Token.LOCAL_BLOCK,
    // simple load
    Token.LOCAL_LOAD,
    // try-catch-scope
    Token.TRY,
    Token.FINALLY,
    Token.RETHROW,
    // enumeration
    Token.ENUM_INIT_KEYS,
    Token.ENUM_INIT_VALUES,
    Token.ENUM_INIT_ARRAY,
    Token.ENUM_INIT_VALUES_IN_ORDER,
    Token.ENUM_NEXT,
    Token.ENUM_ID,
    -> true
    else -> false
}

private val tryInfoKey = InternalPropMap.Key<TryInfo>("tryInfo")
var Jump.tryInfo by tryInfoKey.computing(::TryInfo) {
    require(it.type == Token.TRY)
}

private val jumpFromKey = InternalPropMap.Key<Node?>("jumpFrom")
var Name.jumpFrom: Node?
    set(value) {
        require(value is Jump? || value?.type == Token.FINALLY)
        internalProps[jumpFromKey] = value
    }
    get() {
        return internalProps[jumpFromKey]
    }

val Node.isJumpTarget get() = type == Token.TARGET || type == Token.JSR

private val targetInfoKey = InternalPropMap.Key<TargetInfo>("targetInfo")
var Node.targetInfo: TargetInfo by targetInfoKey.computing(::TargetInfo) { require(it.isJumpTarget) }

private val scopeInfoKey = InternalPropMap.Key<ScopeInfo>("scopeInfo")
var Scope.scopeInfo: ScopeInfo by scopeInfoKey.computing(::ScopeInfo)
