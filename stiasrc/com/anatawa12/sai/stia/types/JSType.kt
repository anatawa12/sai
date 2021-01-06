package com.anatawa12.sai.stia.types

import com.anatawa12.sai.Undefined
import com.anatawa12.sai.ast.RegExpLiteral

/**
 * オブジェクトに対しては各オブジェクトに型を付けたい
 */
sealed class ResolvingType {
    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T> byCompileTimeValue(value: T): CompileTimeConstantType<T> {
            return byCompileTimeValueInternal(value) as CompileTimeConstantType<T>
        }
        private fun byCompileTimeValueInternal(value: Any?): CompileTimeConstantType<*> {
            if (value == null) return NullType
            return when (value.javaClass) {
                Boolean::class.javaObjectType-> BooleanType
                Double::class.javaObjectType -> NumberType
                String::class.java -> StringType
                Undefined::class.java -> UndefinedType
                else -> error("unknown compile time value: $value(${value.javaClass})")
            }
        }

        fun byClass(type: Class<*>?): ResolvingType {
            if (type == null) return UndefinedType
            if (type.isPrimitive) {
                if (type == Void.TYPE)
                    return UndefinedType
                if (type == Boolean::class.java)
                    return BooleanType
                return NumberType
            }
            return JavaValueType(type)
        }
    }
}

sealed class CompileTimeConstantType<T> : ResolvingType()

object BooleanType : CompileTimeConstantType<Boolean>() {
    override fun toString(): String = "Boolean"
}

object NumberType: CompileTimeConstantType<Double>() {
    override fun toString(): String = "Number"
}

object StringType : CompileTimeConstantType<String>() {
    override fun toString(): String = "String"
}

object NullType : CompileTimeConstantType<Nothing?>() {
    override fun toString(): String = "Null"
}

object UndefinedType : CompileTimeConstantType<Undefined>() {
    override fun toString(): String = "Undefined"
}

object RegexpType : CompileTimeConstantType<RegExpLiteral>() {
    override fun toString(): String = "RegExp"
}

data class InternalValueType(val type: Class<*>) : ResolvingType()

data class JavaValueType(val type: Class<*>) : ResolvingType()

data class JavaClassType(val type: Class<*>) : ResolvingType()

data class JavaPackageType(val fqn: String) : ResolvingType()

fun getTypeByObject(value: Any?): ResolvingType {
    return when (value) {
        null -> NullType
        is Undefined -> UndefinedType

        else -> error("")
    }
}

fun ResolvingType.isNumber() = this == NumberType
fun ResolvingType.mayNumber() = this != StringType
