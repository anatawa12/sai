package com.anatawa12.sai.stia

import com.anatawa12.sai.Node
import com.anatawa12.sai.ast.Name

sealed class VariableId {
    val usedBy = mutableSetOf<Name>()

    abstract override fun toString(): String

    fun asLocal(): Local = this as Local
    abstract fun checkName(name: Name)

    class Internal internal constructor(val id: Int, val version: Int, val previousVersion: Internal?)
        : VariableId() {
        lateinit var producer: Node
        private val producerOrNull get() = if (::producer.isInitialized) producer else null

        override fun toString(): String {
            return buildString {
                append("\'internal^")
                append(id)
                append('#')
                append(version)
                append('#')
                append(shortHash())
                append('<')
                append(producerOrNull.shortHash())
                append('\'')
            }
        }

        override fun checkName(name: Name) {
            require(false) { "internal variable cannot be assigned to Name" }
        }
    }

    class Local internal constructor(val name: String, val version: Int, val previousVersion: Local?)
        : VariableId() {
        lateinit var producer: Node
        private val producerOrNull get() = if (::producer.isInitialized) producer else null

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

        fun replacedBy(replacement: Local, replaceAt: Node) {
            require(replacement !== this) { "can't replace this with this" }
            //println("$this <- $replacement")
            if (producer != replaceAt) {
                replacement.producer = producer
            }
            for (name in usedBy.toList()) {
                name.varId = replacement
            }
        }

        override fun checkName(name: Name) {
            require(name.identifier == this.name) { "name mismatch" }
        }
    }

    class Global internal constructor(val name: String) : VariableId() {
        var producers = mutableListOf<Node>()

        override fun toString(): String {
            return buildString {
                append('\'')
                append("global#")
                append(name)
                append(shortHash())
                append('\'')
            }
        }

        override fun checkName(name: Name) {
            require(name.identifier == this.name) { "name mismatch" }
        }
    }
}
