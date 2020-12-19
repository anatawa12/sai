
package com.anatawa12.sai.stia

import com.anatawa12.sai.ast.Name
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

fun Name.copy(): Name {
    return Name().also {
        it.identifier = identifier
        it.varId = varId
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
