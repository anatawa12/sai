package com.anatawa12.sai.stia

import com.anatawa12.sai.Node
import com.anatawa12.sai.ast.Name
import com.anatawa12.sai.ast.Scope

class VariableId internal constructor(val name: String, val version: Int, val previousVersion: VariableId?) {
    lateinit var producer: Node
    private val producerOrNull get() = if (::producer.isInitialized) producer else null
    val usedBy = mutableSetOf<Name>()

    var reachable: Boolean = true
        private set

    override fun toString(): String {
        return buildString {
            append('\'')
            if (!reachable) append('*')
            append(name)
            append('#')
            append(version)
            append('#')
            append(shortHash())
            append('<')
            append(producerOrNull.shortHash())
            append('\'')
        }
    }

    fun markUnreachable() {
        reachable = false
    }

    fun replacedBy(replacement: VariableId) {
        require(replacement !== this) { "can't replace this with this" }
        //println("$this <- $replacement")
        for (name in usedBy.toList()) {
            name.varId = replacement
        }
    }

    val producerNoAssigning: Node get() {
        var cur: VariableId = this
        while (true) {
            if (cur.producer.isJumpTarget || cur.producer is Scope)
                return cur.producer
            cur = cur.previousVersion ?: return cur.producer
        }
    }
}
