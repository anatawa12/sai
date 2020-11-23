package com.anatawa12.sai;

import com.anatawa12.sai.linker.DynamicMethod;
import com.anatawa12.sai.linker.MethodOrConstructor;

import java.lang.ref.SoftReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;

/**
 * A java members data which not relates to scopes.
 */
class StaticJavaMembers {
    private StaticJavaMembers(Class<?> cl, boolean includeProtected) {
        try {
            Context cx = ContextFactory.getGlobal().enterContext();
            ClassShutter shutter = cx.getClassShutter();
            if (shutter != null && !shutter.visibleToScripts(cl.getName())) {
                throw RuntimeErrors.reportRuntimeError1("msg.access.prohibited",
                        cl.getName());
            }
            this.cl = cl;
            reflect(includeProtected);
        } finally {
            Context.exit();
        }
    }

    /**
     * Retrieves mapping of methods to accessible methods for a class.
     * In case the class is not public, retrieves methods with same
     * signature as its public methods from public superclasses and
     * interfaces (if they exist). Basically upcasts every method to the
     * nearest accessible method.
     */
    private static Collection<Method> discoverAccessibleMethods(Class<?> clazz,
                                                                boolean includeProtected) {
        Map<MethodSignature, Method> map = new HashMap<>();
        discoverAccessibleMethods(clazz, map, includeProtected);
        return map.values();
    }

    private static void discoverAccessibleMethods(
            Class<?> clazz,
            Map<MethodSignature, Method> map,
            boolean includeProtected
    ) {
        if (isPublic(clazz.getModifiers())) {
            try {
                if (includeProtected) {
                    while (clazz != null) {
                        try {
                            Method[] methods = clazz.getDeclaredMethods();
                            for (Method method : methods) {
                                int mods = method.getModifiers();

                                if (isPublic(mods) || isProtected(mods)) {
                                    MethodSignature sig = new MethodSignature(method);
                                    if (!map.containsKey(sig)) {
                                        map.put(sig, method);
                                    }
                                }
                            }
                            Class<?>[] interfaces = clazz.getInterfaces();
                            for (Class<?> intface : interfaces) {
                                discoverAccessibleMethods(intface, map, includeProtected);
                            }
                            clazz = clazz.getSuperclass();
                        } catch (SecurityException e) {
                            // Some security settings (i.e., applets) disallow
                            // access to Class.getDeclaredMethods. Fall back to
                            // Class.getMethods.
                            Method[] methods = clazz.getMethods();
                            for (Method method : methods) {
                                MethodSignature sig = new MethodSignature(method);
                                if (!map.containsKey(sig))
                                    map.put(sig, method);
                            }
                            break; // getMethods gets superclass methods, no
                            // need to loop any more
                        }
                    }
                } else {
                    Method[] methods = clazz.getMethods();
                    for (Method method : methods) {
                        MethodSignature sig = new MethodSignature(method);
                        // Array may contain methods with same signature but different return value!
                        if (!map.containsKey(sig))
                            map.put(sig, method);
                    }
                }
                return;
            } catch (SecurityException e) {
                Context.reportWarning(
                        "Could not discover accessible methods of class " +
                                clazz.getName() + " due to lack of privileges, " +
                                "attemping superclasses/interfaces.");
                // Fall through and attempt to discover superclass/interface
                // methods
            }
        }

        Class<?>[] interfaces = clazz.getInterfaces();
        for (Class<?> intface : interfaces) {
            discoverAccessibleMethods(intface, map, includeProtected);
        }
        Class<?> superclass = clazz.getSuperclass();
        if (superclass != null) {
            discoverAccessibleMethods(superclass, map, includeProtected);
        }
    }

    private static final class MethodSignature {
        private final String name;
        private final Class<?>[] args;

        private MethodSignature(String name, Class<?>[] args) {
            this.name = name;
            this.args = args;
        }

        MethodSignature(Method method) {
            this(method.getName(), method.getParameterTypes());
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof MethodSignature) {
                MethodSignature ms = (MethodSignature) o;
                return ms.name.equals(name) && Arrays.equals(args, ms.args);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return name.hashCode() ^ args.length;
        }
    }

    private void reflect(boolean includeProtected) {
        // We reflect methods first, because we want overloaded field/method
        // names to be allocated to the NativeJavaMethod before the field
        // gets in the way.

        Collection<Method> methods = discoverAccessibleMethods(cl, includeProtected);
        Collection<Field> fields = getAccessibleFields(includeProtected);

        Map<String, List<Method>> methodsByName = methods.stream()
                .collect(Collectors.groupingBy(Method::getName));

        {
            List<MethodOrConstructor> staticMethods = new ArrayList<>();
            List<MethodOrConstructor> instanceMethods = new ArrayList<>();

            for (Map.Entry<String, List<Method>> entry : methodsByName.entrySet()) {
                for (Method method : entry.getValue()) {
                    if (isStatic(method.getModifiers()))
                        staticMethods.add(new MethodOrConstructor(method));
                    else
                        instanceMethods.add(new MethodOrConstructor(method));
                }

                String name = entry.getKey();

                if (!staticMethods.isEmpty())
                    staticMembers.put(name, new AMethod(new DynamicMethod(new LinkedList<>(staticMethods), name), true));

                if (!instanceMethods.isEmpty())
                    instanceMembers.put(name, new AMethod(new DynamicMethod(new LinkedList<>(instanceMethods), name), false));

                staticMethods.clear();
                instanceMethods.clear();
            }
        }

        // Reflect fields.
        for (Field field : fields) {
            String name = field.getName();
            int mods = field.getModifiers();
            try {
                boolean isStatic = Modifier.isStatic(mods);
                Map<String, TheMember> ht = getMap(isStatic);
                Object member = ht.get(name);
                if (member == null) {
                    ht.put(name, new AField(field, isStatic));
                } else if (member instanceof AMethod) {
                    AMethod method = (AMethod) member;
                    AFieldAndMethods fam = new AFieldAndMethods(method.dynamicMethod, field, isStatic);
                    ht.put(name, fam);
                    getFieldAndMethods(isStatic).put(name, fam);
                } else if (member instanceof AField) {
                    AField oldField = (AField) member;
                    // If this newly reflected field shadows an inherited field,
                    // then replace it. Otherwise, since access to the field
                    // would be ambiguous from Java, no field should be
                    // reflected.
                    // For now, the first field found wins, unless another field
                    // explicitly shadows it.
                    if (oldField.theField.getDeclaringClass().
                            isAssignableFrom(field.getDeclaringClass())) {
                        ht.put(name, new AField(field, isStatic));
                    }
                } else {
                    // "unknown member type"
                    throw Kit.codeBug();
                }
            } catch (SecurityException e) {
                // skip this field
                Context.reportWarning("Could not access field "
                        + name + " of class " + cl.getName() +
                        " due to lack of privileges.");
            }
        }

        // Create bean properties from corresponding get/set methods first for
        // static members and then for instance members
        for (int tableCursor = 0; tableCursor != 2; ++tableCursor) {
            boolean isStatic = (tableCursor == 0);
            Map<String, TheMember> ht = getMap(isStatic);

            Map<String, AProperty> toAdd = new HashMap<>();

            // Now, For each member, make "bean" properties.
            for (String name : ht.keySet()) {
                // Is this a getter?
                boolean memberIsGetMethod = name.startsWith("get");
                boolean memberIsSetMethod = name.startsWith("set");
                boolean memberIsIsMethod = name.startsWith("is");
                if (memberIsGetMethod || memberIsIsMethod
                        || memberIsSetMethod) {
                    // Double check name component.
                    String nameComponent = name.substring(memberIsIsMethod ? 2 : 3);
                    if (nameComponent.length() == 0)
                        continue;

                    // Make the bean property name.
                    String beanPropertyName = nameComponent;
                    char ch0 = nameComponent.charAt(0);
                    if (Character.isUpperCase(ch0)) {
                        if (nameComponent.length() == 1) {
                            beanPropertyName = nameComponent.toLowerCase();
                        } else {
                            char ch1 = nameComponent.charAt(1);
                            if (!Character.isUpperCase(ch1)) {
                                beanPropertyName = Character.toLowerCase(ch0)
                                        + nameComponent.substring(1);
                            }
                        }
                    }

                    // If we already have a member by this name, don't do this
                    // property.
                    if (toAdd.containsKey(beanPropertyName))
                        continue;
                    TheMember v = ht.get(beanPropertyName);
                    if (v != null) {
                        // A private field shouldn't mask a public getter/setter
                        continue;
                    }

                    // Find the getter method, or if there is none, the is-
                    // method.
                    MethodOrConstructor getter;
                    getter = findGetter(isStatic, ht, "get", nameComponent);
                    // If there was no valid getter, check for an is- method.
                    if (getter == null) {
                        getter = findGetter(isStatic, ht, "is", nameComponent);
                    }

                    // setter
                    MethodOrConstructor setter = null;
                    AMethod setters = null;
                    String setterName = "set".concat(nameComponent);

                    if (ht.containsKey(setterName)) {
                        // Is this value a method?
                        Object member = ht.get(setterName);
                        if (member instanceof AMethod) {
                            AMethod njmSet = (AMethod) member;
                            if (getter != null) {
                                // We have a getter. Now, do we have a matching
                                // setter?
                                Class<?> type = getter.returnType();
                                setter = extractSetMethod(type, njmSet.dynamicMethod,
                                        isStatic);
                            } else {
                                // No getter, find any set method
                                setter = extractSetMethod(njmSet.dynamicMethod,
                                        isStatic);
                            }
                            if (njmSet.dynamicMethod.size() > 1) {
                                setters = njmSet;
                            }
                        }
                    }
                    // Make the property.
                    AProperty bp = new AProperty(getter, setter,
                            setters, isStatic);
                    toAdd.put(beanPropertyName, bp);
                }
            }

            // Add the new bean properties.
            ht.putAll(toAdd);
        }

        // Reflect constructors
        Constructor<?>[] constructors = getAccessibleConstructors();
        LinkedList<MethodOrConstructor> ctorMembers = Arrays.stream(constructors)
                .map(MethodOrConstructor::new)
                .collect(Collectors.toCollection(LinkedList::new));
        dynamicConstructor = new DynamicMethod(ctorMembers, cl.getSimpleName());
    }

    private Constructor<?>[] getAccessibleConstructors() {
        // The JVM currently doesn't allow changing access on java.lang.Class
        // constructors, so don't try
        return cl.getConstructors();
    }

    private Collection<Field> getAccessibleFields(boolean includeProtected) {
        if (includeProtected) {
            try {
                List<Field> fieldsList = new ArrayList<>();
                Class<?> currentClass = cl;

                while (currentClass != null) {
                    // get all declared fields in this class, make them
                    // accessible, and save
                    Field[] declared = currentClass.getDeclaredFields();
                    for (Field field : declared) {
                        int mod = field.getModifiers();
                        if (isPublic(mod) || isProtected(mod)) {
                            if (!field.isAccessible())
                                field.setAccessible(true);
                            fieldsList.add(field);
                        }
                    }
                    // walk up superclass chain.  no need to deal specially with
                    // interfaces, since they can't have fields
                    currentClass = currentClass.getSuperclass();
                }

                return fieldsList;
            } catch (SecurityException e) {
                // fall through to !includePrivate case
            }
        }
        return Arrays.asList(cl.getFields());
    }

    private static MethodOrConstructor findGetter(boolean isStatic, Map<String, TheMember> ht, String prefix,
                                                  String propertyName) {
        String getterName = prefix.concat(propertyName);
        if (ht.containsKey(getterName)) {
            // Check that the getter is a method.
            Object member = ht.get(getterName);
            if (member instanceof AMethod) {
                AMethod njmGet = (AMethod) member;
                return extractGetMethod(njmGet.dynamicMethod, isStatic);
            }
        }
        return null;
    }

    private static MethodOrConstructor extractGetMethod(DynamicMethod methods,
                                                        boolean isStatic) {
        // Inspect the list of all MemberBox for the only one having no
        // parameters
        for (MethodOrConstructor method : methods.getMethods()) {
            // Does getter method have an empty parameter list with a return
            // value (eg. a getSomething() or isSomething())?
            if (method.parameterCount() == 0 && (!isStatic || method.isStatic())) {
                Class<?> type = method.returnType();
                if (type != Void.TYPE) {
                    return method;
                }
                break;
            }
        }
        return null;
    }

    private static MethodOrConstructor extractSetMethod(Class<?> type, DynamicMethod methods,
                                                        boolean isStatic) {
        //
        // Note: it may be preferable to allow NativeJavaMethod.findFunction()
        //       to find the appropriate setter; unfortunately, it requires an
        //       instance of the target arg to determine that.
        //

        // Make two passes: one to find a method with direct type assignment,
        // and one to find a widening conversion.
        for (int pass = 1; pass <= 2; ++pass) {
            for (MethodOrConstructor method : methods.getMethods()) {
                if (!isStatic || method.isStatic()) {
                    Class<?>[] params = method.parameterArray();
                    if (params.length == 1) {
                        if (pass == 1) {
                            if (params[0] == type) {
                                return method;
                            }
                        } else {
                            //noinspection ConstantConditions
                            if (pass != 2) throw Kit.codeBug();
                            if (params[0].isAssignableFrom(type)) {
                                return method;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static MethodOrConstructor extractSetMethod(DynamicMethod methods,
                                                        boolean isStatic) {

        for (MethodOrConstructor method : methods.getMethods()) {
            if (!isStatic || method.isStatic()) {
                if (method.returnType() == Void.TYPE) {
                    if (method.parameterCount() == 1) {
                        return method;
                    }
                }
            }
        }
        return null;
    }

    private static final ClassMap<StaticJavaMembers> javaMembers = new ClassMap<>();

    static StaticJavaMembers lookupClass(Class<?> dynamicType, Class<?> staticType, boolean includeProtected) {
        StaticJavaMembers members;
        Objects.requireNonNull(dynamicType);
        Objects.requireNonNull(dynamicType);

        Class<?> cl = dynamicType;
        while (true) {
            members = javaMembers.get(cl);
            if (members != null) {
                if (cl != dynamicType) {
                    // member lookup for the original class failed because of
                    // missing privileges, cache the result so we don't try again
                    if (!javaMembers.compareAndSet(dynamicType, null, members))
                        members = javaMembers.get(dynamicType);
                }
                return members;
            }
            try {
                members = new StaticJavaMembers(cl, includeProtected);
                break;
            } catch (SecurityException e) {
                // Reflection may fail for objects that are in a restricted
                // access package (e.g. sun.*).  If we get a security
                // exception, try again with the static type if it is interface.
                // Otherwise, try superclass
                if (staticType != null && staticType.isInterface()) {
                    cl = staticType;
                    staticType = null; // try staticType only once
                } else {
                    assert cl != null;
                    Class<?> parent = cl.getSuperclass();
                    if (parent == null) {
                        if (cl.isInterface()) {
                            // last resort after failed staticType interface
                            parent = ScriptRuntime.ObjectClass;
                        } else {
                            throw e;
                        }
                    }
                    cl = parent;
                }
            }
        }

        if (!javaMembers.compareAndSet(cl, null, members)) {
            members = javaMembers.get(cl);
        }
        if (cl != dynamicType) {
            // member lookup for the original class failed because of
            // missing privileges, cache the result so we don't try again
            if (!javaMembers.compareAndSet(dynamicType, null, members))
                members = javaMembers.get(dynamicType);
        }

        return members;
    }

    /**
     * gets member. if {@code isStatic} is {@code true}, try only static members.
     * Otherwise, try instance members first and then try static members.
     *
     * @param name     the name of member to search
     * @param isStatic try onlu static members.
     * @return found member or null
     */
    TheMember get(String name, boolean isStatic) {
        Map<String, TheMember> ht = getMap(isStatic);
        TheMember member = ht.get(name);
        if (!isStatic && member == null) {
            // Try to get static member from instance (LC3)
            member = staticMembers.get(name);
        }

        return member;
    }

    Map<String, TheMember> getMap(boolean isStatic) {
        return isStatic ? staticMembers : instanceMembers;
    }

    Map<String, AFieldAndMethods> getFieldAndMethods(boolean isStatic) {
        return isStatic ? staticFieldAndMethods : instanceFieldAndMethods;
    }

    JavaMembers lookupForScope(Scriptable scope) {
        ClassCache cache = ClassCache.get(scope);
        return cache.getClassCacheMap()
                .computeIfAbsent(this, (key) -> new JavaMembers(scope, this));
    }

    public Class<?> getCl() {
        return cl;
    }

    private final Class<?> cl;
    private final Map<String, TheMember> instanceMembers = new HashMap<>();
    private final Map<String, TheMember> staticMembers = new HashMap<>();
    private final Map<String, AFieldAndMethods> instanceFieldAndMethods = new HashMap<>();
    private final Map<String, AFieldAndMethods> staticFieldAndMethods = new HashMap<>();
    private final Map<ClassCache, SoftReference<JavaMembers>> scoped = Collections.synchronizedMap(new WeakHashMap<>());
    DynamicMethod dynamicConstructor;

    static abstract class TheMember {
        final boolean isStatic;

        protected TheMember(boolean isStatic) {
            this.isStatic = isStatic;
        }
    }

    final static class AMethod extends TheMember {
        final DynamicMethod dynamicMethod;

        private AMethod(DynamicMethod dynamicMethod, boolean isStatic) {
            super(isStatic);
            this.dynamicMethod = dynamicMethod;
        }
    }

    final static class AField extends TheMember {
        final Field theField;

        private AField(Field theField, boolean isStatic) {
            super(isStatic);
            this.theField = theField;
        }
    }

    final static class AFieldAndMethods extends TheMember {
        final DynamicMethod dynamicMethod;
        final AField theField;

        private AFieldAndMethods(DynamicMethod dynamicMethod, Field theField, boolean isStatic) {
            super(isStatic);
            this.dynamicMethod = dynamicMethod;
            this.theField = new AField(theField, isStatic);
        }
    }

    final static class AProperty extends TheMember {
        private AProperty(MethodOrConstructor getter, MethodOrConstructor setter, AMethod setters, boolean isStatic) {
            super(isStatic);
            this.getter = getter;
            this.setter = setter;
            this.setters = setters;
        }

        MethodOrConstructor getter;
        MethodOrConstructor setter;
        AMethod setters;
    }
}
