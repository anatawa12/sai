/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.anatawa12.sai;

import com.anatawa12.sai.linker.MethodResolveCache;
import com.anatawa12.sai.linker.MethodOrConstructor;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedList;

/**
 * This class reflects Java methods into the JavaScript environment and
 * handles overloading of methods.
 *
 * @author Mike Shaver
 * @see NativeJavaArray
 * @see NativeJavaPackage
 * @see NativeJavaClass
 */

public class NativeJavaMethod extends BaseFunction
{
    private static final long serialVersionUID = -3440381785576412928L;

    NativeJavaMethod(LinkedList<MethodOrConstructor> methods)
    {
        this(new MethodResolveCache(methods, methods.iterator().next().name()));
    }

    NativeJavaMethod(MethodOrConstructor method)
    {
        this(new MethodResolveCache(new LinkedList<>(Collections.singletonList(method)), method.name()));
    }

    NativeJavaMethod(MethodResolveCache methodResolveCache)
    {
        this.functionName = methodResolveCache.getMethods().iterator().next().name();
        this.methodResolveCache = methodResolveCache;
    }

    @Override
    public String getFunctionName()
    {
        return functionName;
    }

    static String scriptSignature(Object[] values)
    {
        StringBuilder sig = new StringBuilder();
        for (int i = 0; i != values.length; ++i) {
            Object value = values[i];

            String s;
            if (value == null) {
                s = "null";
            } else if (value instanceof Boolean) {
                s = "boolean";
            } else if (value instanceof String) {
                s = "string";
            } else if (value instanceof Number) {
                s = "number";
            } else if (value instanceof Scriptable) {
                if (value instanceof Undefined) {
                    s = "undefined";
                } else if (value instanceof Wrapper) {
                    Object wrapped = ((Wrapper)value).unwrap();
                    s = wrapped.getClass().getName();
                } else if (value instanceof Function) {
                    s = "function";
                } else {
                    s = "object";
                }
            } else {
                s = JavaMembers.javaSignature(value.getClass());
            }

            if (i != 0) {
                sig.append(',');
            }
            sig.append(s);
        }
        return sig.toString();
    }

    @Override
    String decompile(int indent, int flags)
    {
        StringBuilder sb = new StringBuilder();
        boolean justbody = (0 != (flags & Decompiler.ONLY_BODY_FLAG));
        if (!justbody) {
            sb.append("function ");
            sb.append(getFunctionName());
            sb.append("() {");
        }
        sb.append("/*\n");
        sb.append(toString());
        sb.append(justbody ? "*/\n" : "*/}\n");
        return sb.toString();
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        for (MethodOrConstructor methodOrCtor : methodResolveCache.getMethods()) {

            // Check member type, we also use this for overloaded constructors
            if (methodOrCtor.isMethod()) {
                Method method = methodOrCtor.asMethod();
                sb.append(JavaMembers.javaSignature(method.getReturnType()));
                sb.append(' ');
                sb.append(method.getName());
            } else {
                sb.append(methodOrCtor.name());
            }
            sb.append(JavaMembers.liveConnectSignature(methodOrCtor.parameterArray()));
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    public Object call(Context cx, Scriptable scope, Scriptable thisObj,
                       Object[] args)
    {
        return callMethod(cx, scope, thisObj, methodResolveCache, args);
    }

    public static Object callMethod(Context cx, Scriptable scope, Scriptable thisObj,
                                    MethodResolveCache methodResolveCache, Object[] args)
    {
        MethodOrConstructor method = methodResolveCache.getResolved(args);

        args = MemberBox.marshallParameters(method, args);

        Object javaObject;
        if (method.isStatic()) {
            javaObject = null;  // don't need an object
        } else {
            Scriptable o = thisObj;
            Class<?> c = method.getDeclaringClass();
            while(true) {
                if (o == null) {
                    throw RuntimeErrors.reportRuntimeError3(
                        "msg.nonjava.method", method.name(),
                        ScriptRuntime.toString(thisObj), c.getName());
                }
                if (o instanceof Wrapper) {
                    javaObject = ((Wrapper)o).unwrap();
                    if (c.isInstance(javaObject)) {
                        break;
                    }
                }
                if (o instanceof NativePrimitive && ((NativePrimitive) o).unwrappedType() == c) {
                    javaObject = ((NativePrimitive)o).unwrap();
                    break;
                }
                o = o.getPrototype();
            }
        }
        if (debug) {
            printDebug("Calling ", method, args);
        }

        Object retval = MemberBox.invoke(method.asMethod(), javaObject, args);
        Class<?> staticType = method.returnType();

        if (debug) {
            Class<?> actualType = (retval == null) ? null
                                                : retval.getClass();
            System.err.println(" ----- Returned " + retval +
                               " actual = " + actualType +
                               " expect = " + staticType);
        }

        Object wrapped = cx.getWrapFactory().wrap(cx, scope,
                                                  retval, staticType);
        if (debug) {
            Class<?> actualType = (wrapped == null) ? null
                                                 : wrapped.getClass();
            System.err.println(" ----- Wrapped as " + wrapped +
                               " class = " + actualType);
        }

        if (wrapped == null && staticType == Void.TYPE) {
            wrapped = Undefined.instance;
        }
        return wrapped;
    }

    private static final boolean debug = false;

    private static void printDebug(String msg, MethodOrConstructor member,
                                   Object[] args)
    {
        if (debug) {
            StringBuilder sb = new StringBuilder();
            sb.append(" ----- ");
            sb.append(msg);
            sb.append(member.getDeclaringClass().getName());
            sb.append('.');
            if (member.isMethod()) {
                sb.append(member.name());
            }
            sb.append(JavaMembers.liveConnectSignature(member.parameterArray()));
            sb.append(" for arguments (");
            sb.append(scriptSignature(args));
            sb.append(')');
            System.out.println(sb);
        }
    }

    private String functionName;

    MethodResolveCache methodResolveCache;
}
