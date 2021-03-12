package com.anatawa12.sai.stia.ir

import kotlin.reflect.KProperty

fun nodeDelegateOfIrExpression() = IrDelegateProperty<IrExpression>(true)
fun nodeDelegateOfIrExpressionNullable() = IrDelegateProperty<IrExpression?>(false)
fun IrNode.nodeListDelegateOfIrExpression() = IrListDelegateProperty<IrExpression>(this) { it }
fun IrNode.nodeListDelegateOfPairIrExpressionIrJumpTarget() =
    IrListDelegateProperty<Pair<IrExpression, IrJumpTarget>>(this) { it.second }
fun IrNode.nodeListDelegateOfPairStringIrExpression() =
    IrListDelegateProperty<Pair<String, IrExpression?>>(this) { it.second }

class IrDelegateProperty<T : IrNode?>(val notNull: Boolean) {
    private var backed: T? = null

    @Suppress("UNCHECKED_CAST")
    val value: T get() = backed ?: if (notNull) error("no initial value") else null as T

    fun setValue(thisRef: IrNode, value: T) {
        check(value?.parent == null) { "the value already have parent: $value" }
        backed?.parent = null
        backed = value
        value?.parent = thisRef
    }
}

class IrListDelegateProperty<T>(val thisRef: IrNode, val nodeGetter: (T) -> IrNode?) :
    AbstractMutableList<T>(), MutableList<T> {
    fun set(list: List<T>) {
        clear()
        addAll(list)
    }


    private val backed = ArrayList<T>()
    override val size: Int get() = backed.size

    override fun contains(element: T): Boolean = backed.contains(element)
    override fun containsAll(elements: Collection<T>): Boolean = backed.containsAll(elements)
    override fun get(index: Int): T = backed[index]
    override fun indexOf(element: T): Int = backed.indexOf(element)
    override fun isEmpty(): Boolean = backed.isEmpty()
    override fun lastIndexOf(element: T): Int = backed.lastIndexOf(element)
    override fun iterator(): MutableIterator<T> = listIterator()
    override fun listIterator(): MutableListIterator<T> = MutableListIteratorImpl(backed.listIterator())
    override fun listIterator(index: Int): MutableListIterator<T> = MutableListIteratorImpl(backed.listIterator(index))

    override fun add(element: T): Boolean {
        val node = checkElement(element)
        val result = backed.add(element)
        node?.parent = thisRef
        return result
    }

    override fun add(index: Int, element: T) {
        val node = checkElement(element)
        backed.add(index, element)
        node?.parent = thisRef
    }

    override fun clear() {
        val nodes = backed.map(nodeGetter)
        backed.clear()
        nodes.forEach { it?.parent = null }
    }

    override fun remove(element: T): Boolean {
        val node = nodeGetter(element)
        check(node == null || node.parent == thisRef) { "cannot remove child node of other element: $node" }
        return if (backed.remove(element)) {
            node?.parent = null
            true
        } else {
            false
        }
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        var modified = false
        for (element in elements)
            if (remove(element))
                modified = true
        return modified
    }

    override fun removeAt(index: Int): T = backed.removeAt(index).also { nodeGetter(it)?.parent = null }

    override fun set(index: Int, element: T): T {
        val node = checkElement(element)
        val old = backed.set(index, element)
        nodeGetter(old)?.parent = null
        node?.parent = thisRef
        return old
    }

    private fun checkElement(element: T): IrNode? {
        val node = nodeGetter(element)
        check(node?.parent == null) { "the value already have parent: $element" }
        return node
    }

    private inner class MutableListIteratorImpl(val backed: MutableListIterator<T>): MutableListIterator<T> {
        private var cur: T? = null
        @Suppress("UNCHECKED_CAST")
        private fun castedCur() = cur as T

        override fun hasPrevious(): Boolean = backed.hasPrevious()
        override fun nextIndex(): Int = backed.nextIndex()
        override fun previous(): T = backed.previous().also { cur = it }
        override fun previousIndex(): Int = backed.previousIndex()
        override fun hasNext(): Boolean = backed.hasNext()
        override fun next(): T = backed.next().also { cur = it }

        override fun remove() {
            backed.remove()
            nodeGetter(castedCur())?.parent = null
        }

        override fun add(element: T) {
            val node = nodeGetter(element)
            check(node?.parent != null) { "the value already have parent: $element" }
            backed.add(element)
            node?.parent = thisRef
        }

        override fun set(element: T) {
            val node = nodeGetter(element)
            check(node?.parent != null) { "the value already have parent: $element" }
            backed.set(element)
            nodeGetter(castedCur())?.parent = null
            node?.parent = thisRef
        }
    }
}

inline operator fun <T : IrNode?> IrDelegateProperty<T>.getValue(thisRef: IrNode, prop: KProperty<*>): T = value
inline operator fun <T : IrNode?> IrDelegateProperty<T>.setValue(thisRef: IrNode, prop: KProperty<*>, value: T) = setValue(thisRef, value)
inline operator fun <T> IrListDelegateProperty<T>.getValue(thisRef: IrNode, prop: KProperty<*>): MutableList<T> = this
