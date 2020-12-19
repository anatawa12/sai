package com.anatawa12.sai.stia

class TargetInfo(
    val reachable: Boolean,
) {
    override fun toString(): String {
        return buildString {
            append("TargetInfo(")
            if (reachable)
                append("reachable")
            else
                append("unreachable")
            append(")")
        }
    }
}
