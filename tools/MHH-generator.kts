/*
 * 'com.anatawa12.sai.linker.MHH' generator.
 *
 * The class is used to emit literal of MethodHandle.
 *
 * to generate them, use command shown below (for b shell)
 *
 * kotlinc-jvm -script tools/MHH-generator.kts > src/com/anatawa12/sai/linker/MHH.java
 */

println("package com.anatawa12.sai.linker;")
println("")
println("import java.lang.invoke.MethodHandle;")
println("")
println("/**")
println(" * MethodHandle helper")
println(" * argument must be lambda expression")
println(" */")
println("@SuppressWarnings(\"unchecked\")")
println("public class MHH {")
println("    private static MethodHandle error() {")
println("        throw new AssertionError(\"post process of build runs transform\");")
println("    }")
for (i in 0..5) {
    with(Values(i)) {
        println()
        println("    public static ${rArgs} MethodHandle create${i}r(NonVoidFunction${i}${rArgs} func) {")
        println("        return error();")
        println("    }")
        println()
        println("    public static ${vArgs} MethodHandle create${i}v(VoidFunction${i}${vArgs} func) {")
        println("        return error();")
        println("    }")
        println()
        println("    public static ${rvArgs} MethodHandle create${i}rv(NonVoidVarargFunction${i}${rvArgs} func) {")
        println("        return error();")
        println("    }")
        println()
        println("    public static ${vvArgs} MethodHandle create${i}vv(VoidVarargFunction${i}${vvArgs} func) {")
        println("        return error();")
        println("    }")

        println()
        println("    public interface NonVoidFunction${i}${rArgs} {")
        println("        public R func(")
        for (j in 1..i) {
            val comma = if (j == i) "" else ","
            println("                A$j arg$j$comma")
        }
        println("        ) throws Throwable;")
        println("    }")
        println()
        println("    public interface VoidFunction${i}${vArgs} {")
        println("        public void func(")
        for (j in 1..i) {
            val comma = if (j == i) "" else ","
            println("                A$j arg$j$comma")
        }
        println("        ) throws Throwable;")
        println("    }")
        println()
        println("    public interface NonVoidVarargFunction${i}${rvArgs} {")
        println("        public R func(")
        for (j in 1..i) {
            println("                A$j arg$j,")
        }
        println("                V... v")
        println("        ) throws Throwable;")
        println("    }")
        println()
        println("    public interface VoidVarargFunction${i}${vvArgs} {")
        println("        public void func(")
        for (j in 1..i) {
            println("                A$j arg$j,")
        }
        println("                V... v")
        println("        ) throws Throwable;")
        println("    }")
    }
}
println("}")

class Values(count: Int) {
    val rArgs = varargs(true, false, count)
    val vArgs = varargs(false, false, count)
    val rvArgs = varargs(true, true, count)
    val vvArgs = varargs(false, true, count)

    fun varargs(result: Boolean, vararg: Boolean, count: Int): String {
        val args = mutableListOf<String>()
        if (result) args += "R"
        for (i in 1..count) {
            args += "A$i"
        }
        if (vararg) args += "V"
        return if (args.isEmpty()) "" else args.joinToString(", ", "<", ">")
    }
}
