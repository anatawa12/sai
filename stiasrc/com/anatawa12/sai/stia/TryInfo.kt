package com.anatawa12.sai.stia

class TryInfo {
    var catchVariable: VariableId.Internal? = null
    var finallyVariable: VariableId.Internal? = null

    override fun toString(): String {
        return "TryInfo(catchVariable=$catchVariable, finallyVariable=$finallyVariable)"
    }
}
