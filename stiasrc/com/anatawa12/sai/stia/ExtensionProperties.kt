package com.anatawa12.sai.stia

import com.anatawa12.sai.Node
import com.anatawa12.sai.Token
import com.anatawa12.sai.ast.Name

var Name.varId: VariableId
    get() = getProp(Node.STIA_INTERNAL_PROP) as VariableId?
        ?: error("$this(${this.shortHash()}) doesn't have varId")
    set(value) {
        require(value.name == identifier)
        (getProp(Node.STIA_INTERNAL_PROP) as VariableId?)
            ?.usedBy?.remove(this)
        putProp(Node.STIA_INTERNAL_PROP, value)
        value.usedBy.add(this)
    }

val Node.isJumpTarget get() = type == Token.TARGET || type == Token.JSR

var Node.targetInfo: TargetInfo
    set(value) {
        require(isJumpTarget)
        putProp(Node.STIA_INTERNAL_PROP, value)
    }
    get() {
        require(isJumpTarget)
        val got = getProp(Node.STIA_INTERNAL_PROP)
        if (got != null)
            return got as TargetInfo
        error("not initialized")
    }
