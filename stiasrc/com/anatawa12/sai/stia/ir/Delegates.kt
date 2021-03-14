package com.anatawa12.sai.stia.ir

import kotlin.reflect.KProperty

fun nodeDelegateOfIrExpression() = IrDelegateProperty<IrExpression>(true)
fun nodeDelegateOfIrExpressionNullable() = IrDelegateProperty<IrExpression?>(false)
fun IrNode.nodeListDelegateOfIrExpression() = IrListDelegateProperty<IrExpression>(this) { it }

fun <T> IrNode.nodeListDelegateOf(nodeGetter: (T) -> IrNode?) = IrListDelegateProperty<T>(this, nodeGetter)

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

class GettingVariableInfoDelegate(val thisRef: IrGettingName) {
    private var backed: IrVariableInfo? = null
    var value: IrVariableInfo?
    get() = backed
    set(value) {
        backed?.onUnusedBy(thisRef)
        backed = value
        value?.onUsedBy(thisRef)
    }

    companion object {
        operator fun invoke(): GettingVariableInfoDelegateProvider = GettingVariableInfoDelegateProvider()
    }
}
class GettingVariableInfoDelegateProvider

class SettingVariableInfoDelegate(val thisRef: IrSettingName) {
    private var backed: IrVariableInfo? = null
    var value: IrVariableInfo?
        get() = backed
        set(value) {
            backed?.onUnsetBy(thisRef)
            backed = value
            value?.onSetBy(thisRef)
        }

    companion object {
        operator fun invoke(): SettingVariableInfoDelegateProvider = SettingVariableInfoDelegateProvider()
    }
}
class SettingVariableInfoDelegateProvider

inline operator fun <T : IrNode?> IrDelegateProperty<T>.getValue(thisRef: IrNode, prop: KProperty<*>): T = value
inline operator fun <T : IrNode?> IrDelegateProperty<T>.setValue(thisRef: IrNode, prop: KProperty<*>, value: T) = setValue(thisRef, value)
inline operator fun <T> IrListDelegateProperty<T>.getValue(thisRef: IrNode, prop: KProperty<*>): MutableList<T> = this
inline operator fun GettingVariableInfoDelegate.getValue(thisRef: IrGettingName, prop: KProperty<*>): IrVariableInfo? = value
inline operator fun GettingVariableInfoDelegate.setValue(thisRef: IrGettingName, prop: KProperty<*>, value: IrVariableInfo?) {
    this.value = value
}
inline operator fun GettingVariableInfoDelegateProvider.provideDelegate(thisRef: IrGettingName, prop: KProperty<*>) = GettingVariableInfoDelegate(thisRef)
inline operator fun SettingVariableInfoDelegate.getValue(thisRef: IrSettingName, prop: KProperty<*>): IrVariableInfo? = value
inline operator fun SettingVariableInfoDelegate.setValue(thisRef: IrSettingName, prop: KProperty<*>, value: IrVariableInfo?) {
    this.value = value
}
inline operator fun SettingVariableInfoDelegateProvider.provideDelegate(thisRef: IrSettingName, prop: KProperty<*>) = SettingVariableInfoDelegate(thisRef)
