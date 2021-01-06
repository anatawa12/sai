package com.anatawa12.sai

import com.anatawa12.sai.linker.DynamicMethod

object SaiAccessor {
    fun resolveMethod(clazz: Class<*>, name: String, isStatic: Boolean): DynamicMethod? {
        val theMember = StaticJavaMembers.lookupClass(clazz, clazz, false)
            .get(name, isStatic)
        return when (theMember) {
            is StaticJavaMembers.AMethod -> theMember.dynamicMethod
            is StaticJavaMembers.AFieldAndMethods -> theMember.dynamicMethod
            else -> null
        }
    }
    fun resolveField(clazz: Class<*>, name: String, isStatic: Boolean): Class<*>? {
        val theMember = StaticJavaMembers.lookupClass(clazz, clazz, false)
            .get(name, isStatic)
        return when (theMember) {
            is StaticJavaMembers.AFieldAndMethods -> theMember.theField.theField.type
            is StaticJavaMembers.AField -> theMember.theField.type
            is StaticJavaMembers.AProperty -> TODO()
            else -> null
        }
    }
}
