
package com.anatawa12.sai.stia

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
