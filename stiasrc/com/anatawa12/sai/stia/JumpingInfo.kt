package com.anatawa12.sai.stia

import com.anatawa12.sai.Node
import com.anatawa12.sai.Token
import com.anatawa12.sai.ast.Jump

class JumpingInfo {
    val mayJump = Reachable()
    val mayContinue = Reachable()

    override fun toString(): String {
        return "JumpingInfo(jump=$mayJump, continue=$mayContinue)"
    }

    fun addFrom(reachable: Reachable) {
        mayJump.addFrom(reachable)
        mayContinue.addFrom(reachable)
    }

    companion object {
        private val jumpingInfoKey = InternalPropMap.Key<JumpingInfo>("jumpingInfo")
        val Node.jumpingInfo by jumpingInfoKey.computing(::JumpingInfo) {
            require(it is Jump || it.type == Token.FINALLY)
        }
    }
}
