
package com.anatawa12.sai.stia

import com.anatawa12.sai.Node
import com.anatawa12.sai.Token
import com.anatawa12.sai.ast.Jump
import com.anatawa12.sai.ast.Name
import com.anatawa12.sai.ast.NumberLiteral
import com.anatawa12.sai.ast.Scope
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

fun <T> List<T?>.isNotNulls(): Boolean {
    return all { it != null }
}

@Suppress("UNCHECKED_CAST")
fun <T> List<T?>.castAsNonNulls() = this as List<T>

@Suppress("UNCHECKED_CAST")
fun <T> List</*in T*/*>.castAsElementsAre() = this as List<T>

@Suppress("UNCHECKED_CAST")
fun <T> Iterable</*in T*/*>.castAsElementsAre() = this as Iterable<T>

fun <T> Sequence<T?>.isNotNulls(): Boolean {
    return all { it != null }
}

@Suppress("UNCHECKED_CAST")
fun <T> Sequence<T?>.castAsNonNulls() = this as Sequence<T>

@Suppress("UNCHECKED_CAST")
fun <T> Sequence</*in T*/*>.castAsElementsAre() = this as Sequence<T>

fun <T> Iterable<T>.asPair(): Pair<T, T> = asPairOrNull() ?: error("the list is not Pair")

fun <T> Iterable<T>.asTriple(): Triple<T, T, T> = asTripleOrNull() ?: error("the list is not Triple")

fun <T> Iterable<T>.asPairOrNull(): Pair<T, T>? {
    val itr = iterator()
    if (!itr.hasNext()) return null
    val a = itr.next()
    if (!itr.hasNext()) return null
    val b = itr.next()
    if (itr.hasNext()) return null
    return a to b
}

fun <T> Iterable<T>.asTripleOrNull(): Triple<T, T, T>? {
    val itr = iterator()
    if (!itr.hasNext()) return null
    val a = itr.next()
    if (!itr.hasNext()) return null
    val b = itr.next()
    if (!itr.hasNext()) return null
    val c = itr.next()
    if (itr.hasNext()) return null
    return Triple(a, b, c)
}

fun <T> Iterable<T>.takeLastOrNull(count: Int): List<T>? {
    val result = ArrayDeque<T>(count)
    val itr = iterator()
    while (itr.hasNext()) {
        if (result.size == count)
            result.removeFirst()
        result.addLast(itr.next())
    }
    if (result.size != count)
        return null
    return result
}

@OptIn(ExperimentalContracts::class)
inline fun <T, reified R> Collection<T>.mapToArray(map: (T) -> R): Array<R> {
    contract {
        callsInPlace(map, InvocationKind.UNKNOWN)
    }
    val result = arrayOfNulls<R>(size)
    for ((i, value) in withIndex()) {
        result[i] = map(value)
    }
    @Suppress("UNCHECKED_CAST")
    return result as Array<R>
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T> T?.nullable() = this

@Suppress("NOTHING_TO_INLINE")
inline fun <T: Any> T.notnull() = this

fun Any?.shortHash(): String {
    if (this == null) return "null"
    val hashInt = this.hashCode()
    val hashShort = (hashInt and 0xFFFF) xor (hashInt ushr 16)
    return hashShort.toString(16).padStart(4, '0')
}

fun <T, K> MutableIterable<T>.uniqueBy(keySelector: (T) -> K) {
    val keys = hashSetOf<K>()
    val it = iterator()
    while (it.hasNext()) {
        val v = it.next()
        val k = keySelector(v)
        if (!keys.add(k))
            it.remove()
    }
}

/**
 * if size of this is less than minimumSize, result will be
 * [this[0], this[1], this[2], ..., this[this.lastIndex], default, default, ...]
 * if size of this is greater than or equals to minimumSize, result will be
 * [this[0], this[1], this[2], ..., this[this.lastIndex]]
 */
fun <T> List<T>.asResizedWithDefault(minimumSize: Int, default: T): List<T> {
    return ResizedWithDefaultList(this, minimumSize, default)
}

private class ResizedWithDefaultList<T>(val base: List<T>, val minimumSize: Int, val default: T)
    : AbstractList<T>(), List<T> {
    override val size: Int get() = maxOf(base.size, minimumSize)

    private val canDefault: Boolean get() = base.size < minimumSize

    override fun get(index: Int): T {
        if (index in base.indices) return base[index]
        if (index < 0) throw IndexOutOfBoundsException("$index")
        if (index >= size) throw IndexOutOfBoundsException("$index, size: $size")
        return default
    }

    override fun indexOf(element: T): Int {
        if (canDefault && element == default)
            return base.size
        return base.indexOf(element)
    }

    override fun lastIndexOf(element: T): Int {
        if (canDefault && element == default)
            return lastIndex
        return base.lastIndexOf(element)
    }
}

class Event<Handler> {
    private val functions = mutableListOf<Handler.() -> Unit>()

    fun on(func: Handler.() -> Unit) {
        functions += func
    }

    fun call(handler: Handler) {
        for (function in functions) {
            function(handler)
        }
    }
}

@Suppress("NON_PUBLIC_PRIMARY_CONSTRUCTOR_OF_INLINE_CLASS")
inline class Option<out T> private constructor(@PublishedApi @JvmField internal val value: Any?) {
    @Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
    @PublishedApi
    internal inline fun valueAs(): T = value as T

    override fun toString(): String {
        return if (isNone()) "None" else "Some($value)"
    }

    companion object {
        @PublishedApi internal val NONE_VALUE = Any()
        fun <T> none() = Option<T>(NONE_VALUE)
        fun <T> some(value: T) = Option<T>(value)
    }
}

fun <T> Option<T>.isNone() = value == Option.NONE_VALUE
fun <T> Option<T>.isSome() = !isNone()

inline fun <T, R> Option<T>.map(map: (T) -> R): Option<R> =
    if (isNone()) Option.none() else Option.some(map(valueAs()))

inline fun <T, R> Option<T>.flatMap(map: (T) -> Option<R>): Option<R> =
    if (isNone()) Option.none() else map(valueAs())

inline fun <T> Option<T>.orElse(map: () -> T): T =
    if (isNone()) map() else valueAs()

inline fun <T> Option<T>.orFlatElse(map: () -> Option<T>): Option<T> =
    if (isNone()) map() else this

fun <T> Option<T>.getOrNull(): T? = orElse { null }

fun <T> Option<T>.getOrThrow(): T = orElse { error("none on getOrThrow") }

@Suppress("UNCHECKED_CAST")
fun <T> Result<T>.getOrNone(): Option<T> = when {
    isFailure -> Option.none()
    // this is always safe and faster
    else -> Option.some(getOrNull() as T)
}

fun <T: Any> T?.asOption(): Option<T> = if (this == null) Option.none() else Option.some(this)
fun <T> T.asSome(): Option<T> = Option.some(this)

fun <T> Sequence<T>.dropLast(n: Int): Sequence<T> = when {
    n < 0 -> throw IllegalArgumentException("Requested element count $n is less than zero.")
    n == 0 -> this
    else -> DropLastSequence(this, n)
}

private class DropLastSequence<T>(private val seq: Sequence<T>, val count: Int) : Sequence<T> {
    override fun iterator(): Iterator<T> = IteratorImpl(seq.iterator(), count)

    class IteratorImpl<T>(private val iter: Iterator<T>, count: Int) : Iterator<T> {
        // null: hasNext returns null
        // RETURNED: to be computed.
        @Suppress("UNCHECKED_CAST")
        private var buffer: Array<Any?>? = Array(count) { RETURNED }
        private var index = 0

        override fun hasNext(): Boolean {
            val buffer = buffer ?: return false
            while (buffer[index] == RETURNED) {
                if (!iter.hasNext()) {
                    this.buffer = null
                    return false
                }
                buffer[index++] = iter.next()
                if (index == buffer.size) index = 0
            }
            if (!iter.hasNext()) {
                this.buffer = null
                return false
            }
            return true
        }

        override fun next(): T {
            if (!hasNext())
                throw NoSuchElementException()
            val buffer = buffer!!
            @Suppress("UNCHECKED_CAST")
            val result = buffer[index] as T
            buffer[index] = RETURNED
            return result
        }

        companion object {
            private val RETURNED = Any()
            private val END_REACHED = Any()
        }
    }
}

/*
fun Node.toInformationString(): String {
    val node = this

    val extra = when (node) {
        is Name -> ": ${node.identifier}"
        is NumberLiteral -> ": ${node.number}"
        is Jump -> {
            var result = ": ${node.target.shortHash()}"
            when (node.type) {
                Token.TRY -> result += ", ${node.finally.shortHash()}"
                Token.BREAK -> result += ", ${node.jumpStatement.shortHash()}"
                Token.CONTINUE -> result += ", ${node.jumpStatement.shortHash()}"
                Token.LOOP -> result += ", ${node.`continue`.shortHash()}"
            }
            if (node is Scope) {
                result += ", {"
                result += node.symbolTable?.entries?.joinToString { (k, v) ->
                    "$k: $v"
                }
                result += "}"
            }
            result
        }
        else -> ""
    }
    return ExToken.name(node.type) +
            "(${node.javaClass.simpleName}" +
            ";${node.shortHash()}" +
            ")$extra"
}
 */
