package com.anatawa12.sai.stia

import com.anatawa12.sai.ast.Name

class ScopeInfo {
    private val _parameters = mutableListOf<Name>()
    val parameters: List<Name> get() = _parameters
    fun addParameter(variable: Name) {
        _parameters += variable
    }

    override fun toString(): String {
        return buildString {
            append("ScopeInfo(")
            if (_parameters.isNotEmpty()) {
                append("parameters=[")
                _parameters.joinTo(this) { "${it.varId}" }
                append("]")
            }
            append(")")
        }
    }


}
