package com.anatawa12.sai.linker;

/**
 * SMethodHandle helper
 * argument must be lambda expression
 */
@SuppressWarnings("unchecked")
public class MHHS {
    private static SMethodHandle error() {
        throw new AssertionError("post process of build runs transform");
    }

    public static <R> SMethodHandle create0r(NonVoidFunction0<R> func) {
        return error();
    }

    public static  SMethodHandle create0v(VoidFunction0 func) {
        return error();
    }

    public static <R, V> SMethodHandle create0rv(NonVoidVarargFunction0<R, V> func) {
        return error();
    }

    public static <V> SMethodHandle create0vv(VoidVarargFunction0<V> func) {
        return error();
    }

    public interface NonVoidFunction0<R> {
        public R func(
        ) throws Throwable;
    }

    public interface VoidFunction0 {
        public void func(
        ) throws Throwable;
    }

    public interface NonVoidVarargFunction0<R, V> {
        public R func(
                V... v
        ) throws Throwable;
    }

    public interface VoidVarargFunction0<V> {
        public void func(
                V... v
        ) throws Throwable;
    }

    public static <R, A1> SMethodHandle create1r(NonVoidFunction1<R, A1> func) {
        return error();
    }

    public static <A1> SMethodHandle create1v(VoidFunction1<A1> func) {
        return error();
    }

    public static <R, A1, V> SMethodHandle create1rv(NonVoidVarargFunction1<R, A1, V> func) {
        return error();
    }

    public static <A1, V> SMethodHandle create1vv(VoidVarargFunction1<A1, V> func) {
        return error();
    }

    public interface NonVoidFunction1<R, A1> {
        public R func(
                A1 arg1
        ) throws Throwable;
    }

    public interface VoidFunction1<A1> {
        public void func(
                A1 arg1
        ) throws Throwable;
    }

    public interface NonVoidVarargFunction1<R, A1, V> {
        public R func(
                A1 arg1,
                V... v
        ) throws Throwable;
    }

    public interface VoidVarargFunction1<A1, V> {
        public void func(
                A1 arg1,
                V... v
        ) throws Throwable;
    }

    public static <R, A1, A2> SMethodHandle create2r(NonVoidFunction2<R, A1, A2> func) {
        return error();
    }

    public static <A1, A2> SMethodHandle create2v(VoidFunction2<A1, A2> func) {
        return error();
    }

    public static <R, A1, A2, V> SMethodHandle create2rv(NonVoidVarargFunction2<R, A1, A2, V> func) {
        return error();
    }

    public static <A1, A2, V> SMethodHandle create2vv(VoidVarargFunction2<A1, A2, V> func) {
        return error();
    }

    public interface NonVoidFunction2<R, A1, A2> {
        public R func(
                A1 arg1,
                A2 arg2
        ) throws Throwable;
    }

    public interface VoidFunction2<A1, A2> {
        public void func(
                A1 arg1,
                A2 arg2
        ) throws Throwable;
    }

    public interface NonVoidVarargFunction2<R, A1, A2, V> {
        public R func(
                A1 arg1,
                A2 arg2,
                V... v
        ) throws Throwable;
    }

    public interface VoidVarargFunction2<A1, A2, V> {
        public void func(
                A1 arg1,
                A2 arg2,
                V... v
        ) throws Throwable;
    }

    public static <R, A1, A2, A3> SMethodHandle create3r(NonVoidFunction3<R, A1, A2, A3> func) {
        return error();
    }

    public static <A1, A2, A3> SMethodHandle create3v(VoidFunction3<A1, A2, A3> func) {
        return error();
    }

    public static <R, A1, A2, A3, V> SMethodHandle create3rv(NonVoidVarargFunction3<R, A1, A2, A3, V> func) {
        return error();
    }

    public static <A1, A2, A3, V> SMethodHandle create3vv(VoidVarargFunction3<A1, A2, A3, V> func) {
        return error();
    }

    public interface NonVoidFunction3<R, A1, A2, A3> {
        public R func(
                A1 arg1,
                A2 arg2,
                A3 arg3
        ) throws Throwable;
    }

    public interface VoidFunction3<A1, A2, A3> {
        public void func(
                A1 arg1,
                A2 arg2,
                A3 arg3
        ) throws Throwable;
    }

    public interface NonVoidVarargFunction3<R, A1, A2, A3, V> {
        public R func(
                A1 arg1,
                A2 arg2,
                A3 arg3,
                V... v
        ) throws Throwable;
    }

    public interface VoidVarargFunction3<A1, A2, A3, V> {
        public void func(
                A1 arg1,
                A2 arg2,
                A3 arg3,
                V... v
        ) throws Throwable;
    }

    public static <R, A1, A2, A3, A4> SMethodHandle create4r(NonVoidFunction4<R, A1, A2, A3, A4> func) {
        return error();
    }

    public static <A1, A2, A3, A4> SMethodHandle create4v(VoidFunction4<A1, A2, A3, A4> func) {
        return error();
    }

    public static <R, A1, A2, A3, A4, V> SMethodHandle create4rv(NonVoidVarargFunction4<R, A1, A2, A3, A4, V> func) {
        return error();
    }

    public static <A1, A2, A3, A4, V> SMethodHandle create4vv(VoidVarargFunction4<A1, A2, A3, A4, V> func) {
        return error();
    }

    public interface NonVoidFunction4<R, A1, A2, A3, A4> {
        public R func(
                A1 arg1,
                A2 arg2,
                A3 arg3,
                A4 arg4
        ) throws Throwable;
    }

    public interface VoidFunction4<A1, A2, A3, A4> {
        public void func(
                A1 arg1,
                A2 arg2,
                A3 arg3,
                A4 arg4
        ) throws Throwable;
    }

    public interface NonVoidVarargFunction4<R, A1, A2, A3, A4, V> {
        public R func(
                A1 arg1,
                A2 arg2,
                A3 arg3,
                A4 arg4,
                V... v
        ) throws Throwable;
    }

    public interface VoidVarargFunction4<A1, A2, A3, A4, V> {
        public void func(
                A1 arg1,
                A2 arg2,
                A3 arg3,
                A4 arg4,
                V... v
        ) throws Throwable;
    }

    public static <R, A1, A2, A3, A4, A5> SMethodHandle create5r(NonVoidFunction5<R, A1, A2, A3, A4, A5> func) {
        return error();
    }

    public static <A1, A2, A3, A4, A5> SMethodHandle create5v(VoidFunction5<A1, A2, A3, A4, A5> func) {
        return error();
    }

    public static <R, A1, A2, A3, A4, A5, V> SMethodHandle create5rv(NonVoidVarargFunction5<R, A1, A2, A3, A4, A5, V> func) {
        return error();
    }

    public static <A1, A2, A3, A4, A5, V> SMethodHandle create5vv(VoidVarargFunction5<A1, A2, A3, A4, A5, V> func) {
        return error();
    }

    public interface NonVoidFunction5<R, A1, A2, A3, A4, A5> {
        public R func(
                A1 arg1,
                A2 arg2,
                A3 arg3,
                A4 arg4,
                A5 arg5
        ) throws Throwable;
    }

    public interface VoidFunction5<A1, A2, A3, A4, A5> {
        public void func(
                A1 arg1,
                A2 arg2,
                A3 arg3,
                A4 arg4,
                A5 arg5
        ) throws Throwable;
    }

    public interface NonVoidVarargFunction5<R, A1, A2, A3, A4, A5, V> {
        public R func(
                A1 arg1,
                A2 arg2,
                A3 arg3,
                A4 arg4,
                A5 arg5,
                V... v
        ) throws Throwable;
    }

    public interface VoidVarargFunction5<A1, A2, A3, A4, A5, V> {
        public void func(
                A1 arg1,
                A2 arg2,
                A3 arg3,
                A4 arg4,
                A5 arg5,
                V... v
        ) throws Throwable;
    }
}
