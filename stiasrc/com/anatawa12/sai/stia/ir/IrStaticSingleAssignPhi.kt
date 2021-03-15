package com.anatawa12.sai.stia.ir

/**
 * the resolver class for single static assign's phi
 *
 * this is used for instructions can be the target of jump.
 */
class IrStaticSingleAssignPhi {
    var scopes: List<MutableList<Phi>>? = null
    private var jumpFroms = mutableListOf<IrAllScopeSnapshot>()

    fun onRealScope(thisScope: IrAllScopeSnapshot) {
        if (scopes != null) error("already initialized")
        scopes = thisScope.scopes.map { scope ->
            scope.variable.mapTo(mutableListOf()) { variable ->
                Phi(variable)
            }
        }

        for (jumpFrom in jumpFroms) {
            onJumpFrom(jumpFrom)
        }
        jumpFroms = mutableListOf()
    }

    fun onJumpFrom(thisScope: IrAllScopeSnapshot) {
        val scopes = scopes
        if (scopes == null) {
            jumpFroms.add(thisScope)
            return
        }
        require(scopes.size <= thisScope.scopes.size) { "$thisScope is not sub-scope of this" }
        for ((phis, scope) in scopes.zip(thisScope.scopes))
            require(phis.size == scope.variable.size) { "$thisScope is not sub-scope of this" }

        for ((phis, scope) in scopes.zip(thisScope.scopes)) {
            for ((phi, variable) in phis.zip(scope.variable)) {
                phi.add(variable)
            }
        }
    }

    fun onJumpFrom(thisScope: List<List<List<IrInFunctionVariable>>>) {
        val scopes = checkNotNull(scopes) { "this scope is not set" }

        require(scopes.size == thisScope.size) { "$thisScope is not sub-scope of this" }
        for ((phis, scope) in scopes.zip(thisScope))
            require(phis.size == scope.size) { "$thisScope is not sub-scope of this" }

        for ((phis, scope) in scopes.zip(thisScope)) {
            for ((phi, variables) in phis.zip(scope)) {
                for (variable in variables) {
                    phi.add(variable)
                }
            }
        }
    }

    class Phi(setTo: IrInFunctionVariable) : IrGettingName, IrSettingName {
        val setTo by SettingVariableInfoDelegate(setTo)
        val setFrom = mutableListOf<GettingVariableInfoDelegate<IrInFunctionVariable>>()

        fun add(variable: IrInFunctionVariable) {
            setFrom += GettingVariableInfoDelegate(this, variable)
        }
    }
}
