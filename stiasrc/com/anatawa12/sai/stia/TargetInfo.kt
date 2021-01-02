package com.anatawa12.sai.stia

class TargetInfo {
    private var _reachableFromPrev: Reachable? = null
    val reachableFromPrev: Reachable
        get() = _reachableFromPrev ?: error("this label may not be reachable from prev")
    val reachable = Reachable()

    fun setPrev(prev: Reachable) {
        check(_reachableFromPrev == null) { "prev of this already set" }
        _reachableFromPrev = prev
        reachable.addFrom(prev)
    }

    override fun toString(): String {
        return buildString {
            append("TargetInfo(")
            append(reachable)
            append(")")
        }
    }
}
