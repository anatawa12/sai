package com.anatawa12.sai.stia

class Reachable private constructor(private var cameFrom: MutableList<Reachable>?) {
    constructor(): this(cameFrom = mutableListOf())

    private val alwaysReach get() = cameFrom === AlwaysReachList.of<Reachable>()

    val neverReach get() = cameFrom == null

    val reachable: Boolean
        get() {
            val cameFrom = cameFrom ?: return false
            if (alwaysReach) return true
            for (reachable in cameFrom) {
                if (reachable.alwaysReach) {
                    this.cameFrom = AlwaysReachList.of()
                    return true
                }
                if (reachable.reachable) return true
            }
            return false
        }

    fun never() {
        cameFrom = null
    }

    override fun toString(): String = if (alwaysReach) "always-reach" else if (reachable) "may-reach" else "never-reach"

    fun addFrom(from: Reachable) {
        if (from.alwaysReach) {
            cameFrom = AlwaysReachList.of()
        }
        cameFrom?.add(from)
    }

    companion object {
        private val neverReach = Reachable(null)
        private val alwaysReach = Reachable(AlwaysReachList.of())
        fun neverReach() = neverReach
        fun alwaysReach() = alwaysReach
        fun withBefore(pre: Reachable) = Reachable().apply {
            addFrom(pre)
        }

        private object AlwaysReachList : MutableList<Any?> {
            @Suppress("UNCHECKED_CAST")
            fun <T> of() = AlwaysReachList as MutableList<T>

            override val size: Int get() = 0
            override fun contains(element: Any?): Boolean = false
            override fun containsAll(elements: Collection<Any?>): Boolean = false
            override fun get(index: Int): Nothing = indexOutOfBounds(index)
            override fun indexOf(element: Any?): Int = -1
            override fun isEmpty(): Boolean = true
            override fun iterator(): MutableIterator<Any?> = MutableListIteratorImpl
            override fun lastIndexOf(element: Any?): Int = -1
            override fun add(element: Any?): Boolean = false
            override fun add(index: Int, element: Any?) = Unit
            override fun addAll(index: Int, elements: Collection<Any?>): Boolean = false
            override fun addAll(elements: Collection<Any?>): Boolean = false
            override fun clear() = Unit
            override fun listIterator(): MutableListIterator<Any?> = MutableListIteratorImpl
            override fun listIterator(index: Int): MutableListIterator<Any?> =
                if (index == 0) MutableListIteratorImpl else indexOutOfBounds(index)
            override fun remove(element: Any?): Boolean = false
            override fun removeAll(elements: Collection<Any?>): Boolean = false
            override fun removeAt(index: Int): Nothing = indexOutOfBounds(index)
            override fun retainAll(elements: Collection<Any?>): Boolean = false
            override fun set(index: Int, element: Any?): Nothing = indexOutOfBounds(index)
            override fun subList(fromIndex: Int, toIndex: Int): MutableList<Any?> {
                if (fromIndex != 0) indexOutOfBounds(fromIndex)
                if (toIndex != 0) indexOutOfBounds(toIndex)
                return AlwaysReachList
            }

            private object MutableListIteratorImpl : MutableListIterator<Any?> {
                override fun hasPrevious(): Boolean = false
                override fun nextIndex(): Int = 0
                override fun previous(): Nothing = throw NoSuchElementException()
                override fun previousIndex(): Int = -1
                override fun add(element: Any?) = throw IllegalStateException()
                override fun hasNext(): Boolean = false
                override fun next(): Nothing = throw NoSuchElementException()
                override fun remove() = throw IllegalStateException()
                override fun set(element: Any?) = throw IllegalStateException()
            }

            private fun indexOutOfBounds(index: Int): Nothing = throw IndexOutOfBoundsException("empty, $index")
        }
    }
}
