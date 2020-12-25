package com.anatawa12.sai.stia

class TryInfo {
    var catchVariable: VariableId? = null
    var finallyVariable: VariableId? = null

    override fun toString(): String {
        return "TryInfo(catchVariable=$catchVariable, finallyVariable=$finallyVariable)"
    }
}
