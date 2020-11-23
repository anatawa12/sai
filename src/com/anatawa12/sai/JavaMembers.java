/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.anatawa12.sai;

import com.anatawa12.sai.StaticJavaMembers.AField;
import com.anatawa12.sai.StaticJavaMembers.AFieldAndMethods;
import com.anatawa12.sai.StaticJavaMembers.AMethod;
import com.anatawa12.sai.StaticJavaMembers.AProperty;
import com.anatawa12.sai.StaticJavaMembers.TheMember;
import com.anatawa12.sai.linker.DynamicMethod;
import com.anatawa12.sai.linker.MethodOrConstructor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 *
 * @author Mike Shaver
 * @author Norris Boyd
 * @see NativeJavaObject
 * @see NativeJavaClass
 */
class JavaMembers
{
    JavaMembers(Scriptable scope, StaticJavaMembers staticMembers) {
        staticJavaMembers = staticMembers;
        dynamicConstructor = staticMembers.dynamicConstructor;

        staticMethods = new HashMap<>();
        instanceMethods = new HashMap<>();
        reflect(scope);
    }

    boolean has(String name, boolean isStatic)
    {
        if (staticJavaMembers.getMap(isStatic).containsKey(name)) {
            return true;
        }
        return findExplicitFunction(name, isStatic) != null;
    }

    Object get(Scriptable scope, String name, Object javaObject,
               boolean isStatic)
    {
        BaseFunction method = getMethod(name, isStatic);
        if (method != null) return method;
        TheMember member = staticJavaMembers.get(name, isStatic);
        if (member == null) {
            BaseFunction result = this.getExplicitFunction(scope, name,
                    javaObject, isStatic);
            if (result == null)
                return Scriptable.NOT_FOUND;
            return result;
        }

        Context cx = Context.getContext();
        Object result;
        Class<?> type;
        try {
            if (member instanceof AField) {
                Field field = ((AField) member).theField;
                result = field.get(isStatic ? null : javaObject);
                type = field.getType();
            } else if (member instanceof AProperty) {
                AProperty bp = (AProperty) member;
                if (bp.getter == null)
                    return Scriptable.NOT_FOUND;
                result = MemberBox.invoke(bp.getter.asMethod(), javaObject, Context.emptyArgs);
                type = bp.getter.returnType();
            } else {
                // AMethod: should included in getMethod
                // AFieldAndMethods: should be got from getFieldAndMethodsObjects
                throw Kit.codeBug("there's " + member.getClass().getSimpleName() + " instance when get");
            }
            // Need to wrap the object before we return it.
            scope = ScriptableObject.getTopLevelScope(scope);
            return cx.getWrapFactory().wrap(cx, scope, result, type);
        } catch (Exception ex) {
            throw Context.throwAsScriptRuntimeEx(ex);
        }
    }

    void put(Scriptable scope, String name, Object javaObject,
             Object value, boolean isStatic)
    {
        TheMember member = staticJavaMembers.get(name, isStatic);
        if (member == null)
            throw reportMemberNotFound(name);
        if (member instanceof AFieldAndMethods)
            member = ((AFieldAndMethods) member).theField;

        if (member instanceof AProperty) {
            AProperty bp = (AProperty)member;
            if (bp.setter == null) {
                throw reportMemberNotFound(name);
            }
            // If there's only one setter or if the value is null, use the
            // main setter. Otherwise, let the NativeJavaMethod decide which
            // setter to use:
            if (bp.setters == null || value == null) {
                Class<?> setType = bp.setter.parameterType(0);
                Object[] args = { Context.jsToJava(value, setType) };
                try {
                    MemberBox.invoke(bp.setter.asMethod(), javaObject, args);
                } catch (Exception ex) {
                    throw Context.throwAsScriptRuntimeEx(ex);
                }
            } else {
                Object[] args = { value };
                NativeJavaMethod.callMethod(
                        Context.getContext(),
                        ScriptableObject.getTopLevelScope(scope),
                        scope,
                        bp.setters.dynamicMethod,
                        args
                );
            }
        } else if (member instanceof AField) {
            Field field = ((AField) member).theField;
            Object javaValue = Context.jsToJava(value, field.getType());
            try {
                field.set(javaObject, javaValue);
            } catch (IllegalAccessException accessEx) {
                if ((field.getModifiers() & Modifier.FINAL) != 0) {
                    // treat Java final the same as JavaScript [[READONLY]]
                    return;
                }
                throw Context.throwAsScriptRuntimeEx(accessEx);
            } catch (IllegalArgumentException argEx) {
                throw RuntimeErrors.reportRuntimeError3(
                        "msg.java.internal.field.type",
                        value.getClass().getName(), field,
                        javaObject.getClass().getName());
            }
        } else {
            String str = (member == null) ? "msg.java.internal.private"
                    : "msg.java.method.assign";
            throw RuntimeErrors.reportRuntimeError1(str, name);
        }
    }

    Object[] getIds(boolean isStatic)
    {
        return staticJavaMembers.getMap(isStatic).keySet().toArray(new Object[0]);
    }

    static String javaSignature(Class<?> type)
    {
        if (!type.isArray()) {
            return type.getName();
        }
        int arrayDimension = 0;
        do {
            ++arrayDimension;
            type = type.getComponentType();
        } while (type.isArray());
        String name = type.getName();
        String suffix = "[]";
        if (arrayDimension == 1) {
            return name.concat(suffix);
        }
        int length = name.length() + arrayDimension * suffix.length();
        StringBuilder sb = new StringBuilder(length);
        sb.append(name);
        while (arrayDimension != 0) {
            --arrayDimension;
            sb.append(suffix);
        }
        return sb.toString();
    }

    static String liveConnectSignature(Class<?>[] argTypes)
    {
        int N = argTypes.length;
        if (N == 0) { return "()"; }
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (int i = 0; i != N; ++i) {
            if (i != 0) {
                sb.append(',');
            }
            sb.append(javaSignature(argTypes[i]));
        }
        sb.append(')');
        return sb.toString();
    }

    private MethodOrConstructor findExplicitFunction(String name, boolean isStatic)
    {
        int sigStart = name.indexOf('(');
        if (sigStart < 0) { return null; }

        LinkedList<MethodOrConstructor> methodsOrConstructors = null;
        boolean isCtor = (isStatic && sigStart == 0);

        if (isCtor) {
            // Explicit request for an overloaded constructor
            methodsOrConstructors = staticJavaMembers.dynamicConstructor.getMethods();
        } else {
            // Explicit request for an overloaded method
            String trueName = name.substring(0,sigStart);
            TheMember obj = staticJavaMembers.get(trueName, isStatic);
            if (obj instanceof AMethod) {
                AMethod njm = (AMethod)obj;
                methodsOrConstructors = njm.dynamicMethod.getMethods();
            }
        }

        if (methodsOrConstructors != null) {
            for (MethodOrConstructor methodsOrCtor : methodsOrConstructors) {
                Class<?>[] type = methodsOrCtor.parameterArray();
                String sig = liveConnectSignature(type);
                if (sigStart + sig.length() == name.length()
                        && name.regionMatches(sigStart, sig, 0, sig.length()))
                {
                    return methodsOrCtor;
                }
            }
        }

        return null;
    }

    private BaseFunction getExplicitFunction(Scriptable scope, String name,
                                       Object javaObject, boolean isStatic)
    {
        Map<String, TheMember> ht = staticJavaMembers.getMap(isStatic);
        Map<String, BaseFunction> methodMap = getMethodMap(isStatic);
        BaseFunction member = null;
        MethodOrConstructor methodOrCtor = findExplicitFunction(name, isStatic);

        if (methodOrCtor != null) {
            Scriptable prototype =
                ScriptableObject.getFunctionPrototype(scope);

            if (methodOrCtor.isConstructor()) {
                NativeJavaConstructor fun =
                    new NativeJavaConstructor(methodOrCtor);
                fun.setPrototype(prototype);
                member = fun;
                methodMap.put(name, fun);
            } else {
                String trueName = methodOrCtor.name();
                Object mapValue = ht.get(trueName);

                if (mapValue instanceof AMethod &&
                    ((AMethod)mapValue).dynamicMethod.size() > 1 ) {
                    NativeJavaMethod fun =
                        new NativeJavaMethod(methodOrCtor);
                    fun.setPrototype(prototype);
                    methodMap.put(name, fun);
                    member = fun;
                } else {
                    return null;
                }
            }
        }

        return member;
    }

    private void reflect(Scriptable scope)
    {
        // We reflect methods first, because we want overloaded field/method
        // names to be allocated to the NativeJavaMethod before the field
        // gets in the way.

        for (boolean isStatic : new boolean[] {false, true}) {
            Map<String, BaseFunction> methodMap = getMethodMap(isStatic);
            for (Map.Entry<String, TheMember> entry : staticJavaMembers.getMap(isStatic).entrySet()) {
                TheMember member = entry.getValue();
                if (member instanceof AMethod) {
                    NativeJavaMethod fun = new NativeJavaMethod(((AMethod) member).dynamicMethod);
                    if (scope != null) {
                        ScriptRuntime.setFunctionProtoAndParent(fun, scope);
                    }
                    methodMap.put(entry.getKey(), fun);
                }
            }
        }
    }

    Map<String,FieldAndMethods> getFieldAndMethodsObjects(Scriptable scope,
            Object javaObject, boolean isStatic)
    {
        Map<String,AFieldAndMethods> fieldAndMethods = getFieldAndMethods(isStatic);
        if (fieldAndMethods == null)
            return null;
        int len = fieldAndMethods.size();
        Map<String,FieldAndMethods> result = new HashMap<>(len);
        for (AFieldAndMethods fam: fieldAndMethods.values()) {
            FieldAndMethods famNew = new FieldAndMethods(scope, fam.dynamicMethod,
                                                         fam.theField.theField);
            famNew.javaObject = javaObject;
            result.put(fam.theField.theField.getName(), famNew);
        }
        return result;
    }

    static JavaMembers lookupClass(Scriptable scope, Class<?> dynamicType,
                                   Class<?> staticType, boolean includeProtected)
    {
        StaticJavaMembers staticMembers = StaticJavaMembers
                .lookupClass(dynamicType, staticType, includeProtected);
        return staticMembers.lookupForScope(scope);
    }

    RuntimeException reportMemberNotFound(String memberName)
    {
        return RuntimeErrors.reportRuntimeError2(
            "msg.java.member.not.found", getCl().getName(), memberName);
    }

    private Map<String, BaseFunction> getMethodMap(boolean isStatic) {
        return isStatic ? staticMethods : instanceMethods;
    }

    private Map<String, AFieldAndMethods> getFieldAndMethods(boolean isStatic) {
        return staticJavaMembers.getFieldAndMethods(isStatic);
    }

    private BaseFunction getMethod(String name, boolean isStatic) {
        Map<String, BaseFunction> ht = getMethodMap(isStatic);
        BaseFunction member = ht.get(name);
        if (!isStatic && member == null) {
            // Try to get static member from instance (LC3)
            member = staticMethods.get(name);
        }

        return member;
    }

    public Class<?> getCl() {
        return staticJavaMembers.getCl();
    }

    DynamicMethod dynamicConstructor;
    StaticJavaMembers staticJavaMembers;
    Map<String, BaseFunction> staticMethods;
    Map<String, BaseFunction> instanceMethods;
}

class FieldAndMethods extends NativeJavaMethod
{
    private static final long serialVersionUID = -9222428244284796755L;

    FieldAndMethods(Scriptable scope, DynamicMethod methods, Field field)
    {
        super(methods);
        this.field = field;
        setParentScope(scope);
        setPrototype(ScriptableObject.getFunctionPrototype(scope));
    }

    @Override
    public Object getDefaultValue(Class<?> hint)
    {
        if (hint == ScriptRuntime.FunctionClass)
            return this;
        Object rval;
        Class<?> type;
        try {
            rval = field.get(javaObject);
            type = field.getType();
        } catch (IllegalAccessException accEx) {
            throw RuntimeErrors.reportRuntimeError1(
                "msg.java.internal.private", field.getName());
        }
        Context cx  = Context.getContext();
        rval = cx.getWrapFactory().wrap(cx, this, rval, type);
        if (rval instanceof Scriptable) {
            rval = ((Scriptable) rval).getDefaultValue(hint);
        }
        return rval;
    }

    Field field;
    Object javaObject;
}
