package com.anatawa12.sai.linker.adapters;

import com.anatawa12.sai.Callable;
import com.anatawa12.sai.Context;
import com.anatawa12.sai.ContextFactory;
import com.anatawa12.sai.ScriptRuntime;
import com.anatawa12.sai.Scriptable;
import com.anatawa12.sai.WrapFactory;
import com.anatawa12.sai.classfile.ByteCode;
import com.anatawa12.sai.classfile.ClassFileWriter;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.CodeSource;
import java.security.SecureClassLoader;

public class InterfaceAdapterFactory {
    private static final ClassValue<MethodHandle> adapters = new ClassValue<MethodHandle>() {
        @Override
        protected MethodHandle computeValue(Class type) {
            return generateAdapter(type);
        }
    };

    /**
     * returns (Callable)->implSAM
     */
    public static MethodHandle getAdapter(Class<?> implSAM) {
        return adapters.get(implSAM);
    }

    public static boolean canConvertAsSam(Class<?> implSAM) {
        return BytecodeGenerator.canConvertAsSam(implSAM);
    }

    private static MethodHandle generateAdapter(Class<?> implSAM) {
        ClassLoader loader = createClassLoader(implSAM.getClassLoader(),
                new BytecodeGenerator(implSAM).generateClass(),
                getImplName(implSAM),
                implSAM.getProtectionDomain().getCodeSource());
        try {
            return MethodHandles.publicLookup()
                    .unreflectConstructor(loader.loadClass(getImplName(implSAM)).getConstructor(BytecodeGenerator.generatedClassConstructorArguments))
                    .asType(MethodType.methodType(implSAM, Callable.class));
        } catch (NoSuchMethodException | ClassNotFoundException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static ClassLoader createClassLoader(ClassLoader parent, byte[] classBytes, String className, CodeSource protectionDomain) {
        return new AdapterClassLoader(parent, classBytes, className, protectionDomain);
    }

    private static class AdapterClassLoader extends SecureClassLoader {
        private static final String SAI_PACKAGE = getPackageName(Callable.class.getName());

        private static String getPackageName(String name) {
            return name.substring(0, name.lastIndexOf('.') + 1);
        }

        private static final ClassLoader myLoader = AdapterClassLoader.class.getClassLoader();
        private final byte[] classBytes;
        private final String className;
        private final CodeSource protectionDomain ;

        public AdapterClassLoader(ClassLoader parent, byte[] classBytes, String className, CodeSource protectionDomain) {
            super(parent);
            this.classBytes = classBytes;
            this.className = className;
            this.protectionDomain = protectionDomain;
        }

        @Override
        public Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(SAI_PACKAGE)) {
                return myLoader.loadClass(name);
            }
            return super.loadClass(name, resolve);
        }

        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            if(name.equals(className)) {
                assert classBytes != null : "what? already cleared .class bytes!!";

                return defineClass(name, classBytes, 0, classBytes.length, protectionDomain);
            }
            throw new ClassNotFoundException(name);
        }
    }

    private static String getImplName(Class<?> implSAM) {
        return BASE_PACKAGE + implSAM.getName() + SAM_IMPL_NAME;
    }

    private static class BytecodeGenerator {
        private final Class<?> implSAM;

        private BytecodeGenerator(Class<?> implSAM) {
            this.implSAM = implSAM;
        }

        //region verifier

        private static final ClassValue<Boolean> canConvertAsSam = new ClassValue<Boolean>() {
            @Override
            protected Boolean computeValue(Class<?> type) {
                return computeCanConvertAsSam(type);
            }
        };

        public static boolean canConvertAsSam(Class<?> implSAM) {
            return canConvertAsSam.get(implSAM);
        }

        public static boolean computeCanConvertAsSam(Class<?> implSAM) {
            return getAbstractMethodOf(implSAM) != null && hasNoArgConstructor(implSAM);
        }

        private static Method getAbstractMethodOf(Class<?> clazz) {
            Method result = null;
            for (Method method : clazz.getDeclaredMethods()) {
                if (isVisible(method.getModifiers())
                        && !Modifier.isStatic(method.getModifiers())
                        && Modifier.isAbstract(method.getModifiers())) {
                    if (result != null) return null;
                    result = method;
                }
            }
            Method method;
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null) {
                method = getAbstractMethodOf(superClass);
                if (method != null && result != null) return null;
                if (method != null) result = method;
            }
            for (Class<?> anInterface : clazz.getInterfaces()) {
                method = getAbstractMethodOf(anInterface);
                if (method != null && result != null) return null;
                if (method != null) result = method;
            }
            return result;
        }

        private static boolean hasNoArgConstructor(Class<?> implSAM) {
            // if implSAM is interface, wee use java.lang.Object, it has no arg constructor.
            if (implSAM.isInterface()) return true;
            for (Constructor<?> constructor : implSAM.getDeclaredConstructors()) {
                if (constructor.getParameterCount() == 0) {
                    return isVisible(constructor.getModifiers());
                }
            }
            return false;
        }

        private static boolean isVisible(int modifiers) {
            return (modifiers & (Modifier.PUBLIC | Modifier.PROTECTED)) != 0;
        }

        //endregion

        Method implMethod;
        Class<?> superClass;
        ClassFileWriter writer;

        public byte[] generateClass() {
            implMethod = getAbstractMethod();
            if (implMethod == null)
                throw new IllegalArgumentException("the implSAM does not have or has two or more abstract function");
            if (!hasNoArgConstructor(implSAM))
                throw new IllegalArgumentException("the implSAM does not have accessible zero argument constructor");

            superClass = implSAM.isInterface() ? Object.class : implSAM;
            writer = new ClassFileWriter(getImplName(implSAM),
                    superClass.getName(),
                    "<InterfaceAdapterFactory generated>");
            if (implSAM.isInterface()) writer.addInterface(implSAM.getName());
            generateFields();
            generateConstructor();
            generateImplMethod();
            return writer.toByteArray();
        }

        private Method getAbstractMethod() {
            return getAbstractMethodOf(implSAM);
        }

        private void generateFields() {
            writer.addField("callable", CallableDescriptor, ClassFileWriter.ACC_PRIVATE);
            writer.addField("factory", ContextFactoryDescriptor, ClassFileWriter.ACC_PRIVATE);
        }

        private static final Class<?>[] generatedClassConstructorArguments = new Class[]{
                Callable.class,
        };

        private void generateConstructor() {
            writer.startMethod("<init>",
                    "(" + CallableDescriptor + ")V",
                    ClassFileWriter.ACC_PUBLIC);
            writer.add(ByteCode.ALOAD_0);
            writer.addInvoke(ByteCode.INVOKESPECIAL, superClass.getName(), "<init>", "()V");

            writer.add(ByteCode.ALOAD_0);
            writer.add(ByteCode.ALOAD_1);
            writer.add(ByteCode.PUTFIELD, writer.getClassName(), "callable", CallableDescriptor);

            writer.add(ByteCode.ALOAD_0);
            writer.addInvoke(ByteCode.INVOKESTATIC, ContextName, "getCurrentContext", "()" + ContextDescriptor);
            writer.addInvoke(ByteCode.INVOKEVIRTUAL, ContextName, "getFactory", "()" + ContextFactoryDescriptor);
            writer.add(ByteCode.PUTFIELD, writer.getClassName(), "factory", ContextFactoryDescriptor);
            writer.add(ByteCode.RETURN);
            writer.stopMethod((short) 3);
        }

        private void generateImplMethod() {
            writer.startMethod(implMethod.getName(),
                    getDescriptorOf(implMethod),
                    ClassFileWriter.ACC_PUBLIC);

            writer.add(ByteCode.ALOAD_0);
            writer.add(ByteCode.GETFIELD, writer.getClassName(), "factory", ContextFactoryDescriptor);
            writer.add(ByteCode.ALOAD_0);
            writer.add(ByteCode.GETFIELD, writer.getClassName(), "callable", CallableDescriptor);
            writer.add(ByteCode.ALOAD_0);
            writer.add(ByteCode.GETFIELD, writer.getClassName(), "topScope", ScriptableDescriptor);
            writer.add(ByteCode.ALOAD_0); // thisObject
            writer.addLoadConstant(implMethod.getReturnType()); // javaResultType

            // args
            Class<?>[] parameters = implMethod.getParameterTypes();
            writer.addLoadConstant(parameters.length);
            writer.add(ByteCode.ANEWARRAY, "java.lang.Object");

            for (int i = 0; i < parameters.length; i++) {
                Class<?> parameter = parameters[i];
                writer.add(ByteCode.DUP);
                writer.addLoadConstant(i);
                loadLocalVariableAsObject(parameter, i + 1);
                writer.add(ByteCode.AASTORE);
            }

            writer.addInvoke(ByteCode.INVOKESTATIC, InterfaceAdapterFactoryName, "invoke",
                    "(" +
                            ContextFactoryDescriptor +
                            CallableDescriptor +
                            ScriptableDescriptor +
                            "L" + "java/lang/Object" + ";" +
                            "L" + "java/lang/Class" + ";" +
                            "[L" + "java/lang/Object" + ";" +
                            ")" +
                            "L" + "java/lang/String" + ";"
            );

            castStackValueTo(implMethod.getReturnType());

            returnValue(implMethod.getReturnType());

            writer.stopMethod((short) (implMethod.getParameterCount() + 1));
        }

        private void loadLocalVariableAsObject(Class<?> type, int i) {
            if (type.isPrimitive()) {
                switch (type.toString()) {
                    case "boolean":
                        loadLocal(writer, ByteCode.ILOAD, ByteCode.ILOAD_0, i);
                        writer.addInvoke(ByteCode.INVOKESTATIC, "java.lang.Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
                        break;
                    case "byte":
                        loadLocal(writer, ByteCode.ILOAD, ByteCode.ILOAD_0, i);
                        writer.addInvoke(ByteCode.INVOKESTATIC, "java.lang.Byte", "valueOf", "(B)Ljava/lang/Byte;");
                        break;
                    case "char":
                        loadLocal(writer, ByteCode.ILOAD, ByteCode.ILOAD_0, i);
                        writer.addInvoke(ByteCode.INVOKESTATIC, "java.lang.Character", "valueOf", "(C)Ljava/lang/Character;");
                        break;
                    case "double":
                        loadLocal(writer, ByteCode.DLOAD, ByteCode.DLOAD_0, i);
                        writer.addInvoke(ByteCode.INVOKESTATIC, "java.lang.Double", "valueOf", "(D)Ljava/lang/Double;");
                        break;
                    case "float":
                        loadLocal(writer, ByteCode.FLOAD, ByteCode.FLOAD_0, i);
                        writer.addInvoke(ByteCode.INVOKESTATIC, "java.lang.Float", "valueOf", "(F)Ljava/lang/Float;");
                        break;
                    case "int":
                        loadLocal(writer, ByteCode.ILOAD, ByteCode.ILOAD_0, i);
                        writer.addInvoke(ByteCode.INVOKESTATIC, "java.lang.Integer", "valueOf", "(I)Ljava/lang/Integer;");
                        break;
                    case "long":
                        loadLocal(writer, ByteCode.LLOAD, ByteCode.LLOAD_0, i);
                        writer.addInvoke(ByteCode.INVOKESTATIC, "java.lang.Long", "valueOf", "(J)Ljava/lang/Long;");
                        break;
                    case "short":
                        loadLocal(writer, ByteCode.ILOAD, ByteCode.ILOAD_0, i);
                        writer.addInvoke(ByteCode.INVOKESTATIC, "java.lang.Short", "valueOf", "(S)Ljava/lang/Short;");
                        break;
                    default:
                        throw new IllegalArgumentException("unsupported primitive: " + type);
                }

            } else {
                loadLocal(writer, ByteCode.ALOAD, ByteCode.ALOAD_0, i);
            }
        }

        private void castStackValueTo(Class<?> type) {
            if (type.isPrimitive()) {
                switch (type.toString()) {
                    case "boolean":
                        writer.add(ByteCode.CHECKCAST, "java.lang.Boolean");
                        writer.addInvoke(ByteCode.INVOKEVIRTUAL, "java.lang.Boolean", "booleanValue", "()Z");
                        break;
                    case "byte":
                        writer.add(ByteCode.CHECKCAST, "java.lang.Byte");
                        writer.addInvoke(ByteCode.INVOKEVIRTUAL, "java.lang.Byte", "byteValue", "()B");
                        break;
                    case "char":
                        writer.add(ByteCode.CHECKCAST, "java.lang.Character");
                        writer.addInvoke(ByteCode.INVOKEVIRTUAL, "java.lang.Character", "charValue", "()C");
                        break;
                    case "double":
                        writer.add(ByteCode.CHECKCAST, "java.lang.Double");
                        writer.addInvoke(ByteCode.INVOKEVIRTUAL, "java.lang.Double", "doubleValue", "()D");
                        break;
                    case "float":
                        writer.add(ByteCode.CHECKCAST, "java.lang.Float");
                        writer.addInvoke(ByteCode.INVOKEVIRTUAL, "java.lang.Float", "floatValue", "()F");
                        break;
                    case "int":
                        writer.add(ByteCode.CHECKCAST, "java.lang.Integer");
                        writer.addInvoke(ByteCode.INVOKEVIRTUAL, "java.lang.Integer", "intValue", "()I");
                        break;
                    case "long":
                        writer.add(ByteCode.CHECKCAST, "java.lang.Long");
                        writer.addInvoke(ByteCode.INVOKEVIRTUAL, "java.lang.Long", "longValue", "()J");
                        break;
                    case "short":
                        writer.add(ByteCode.CHECKCAST, "java.lang.Short");
                        writer.addInvoke(ByteCode.INVOKEVIRTUAL, "java.lang.Short", "shortValue", "()S");
                        break;
                    case "void":
                        writer.add(ByteCode.POP);
                        break;
                    default:
                        throw new IllegalArgumentException("unsupported primitive: " + type);
                }
            } else {
                writer.add(ByteCode.CHECKCAST, type.getName());
            }
        }

        private void returnValue(Class<?> type) {
            if (type.isPrimitive()) {
                switch (type.toString()) {
                    case "double":
                        writer.add(ByteCode.DRETURN);
                        break;
                    case "float":
                        writer.add(ByteCode.FRETURN);
                        break;
                    case "long":
                        writer.add(ByteCode.LRETURN);
                        break;
                    case "boolean":
                    case "byte":
                    case "char":
                    case "short":
                    case "int":
                        writer.add(ByteCode.IRETURN);
                        break;
                    case "void":
                        writer.add(ByteCode.RETURN);
                        break;
                    default:
                        throw new IllegalArgumentException("unsupported primitive: " + type);
                }
            } else {
                writer.add(ByteCode.ARETURN);
            }
        }

        private static void loadLocal(ClassFileWriter writer, int normal, int zero, int index) {
            switch (index) {
                case 0:
                    writer.add(zero);
                    break;
                case 1:
                    writer.add(zero + 1);
                    break;
                case 2:
                    writer.add(zero + 2);
                    break;
                case 3:
                    writer.add(zero + 3);
                    break;
                default:
                    writer.add(normal, index);
                    break;
            }
        }

        private static String getDescriptorOf(Method implMethod) {
            StringBuilder builder = new StringBuilder("(");
            for (Class<?> parameterType : implMethod.getParameterTypes()) {
                appendTypeName(builder, parameterType);
            }
            builder.append(')');
            appendTypeName(builder, implMethod.getReturnType());
            return builder.toString();
        }

        private static void appendTypeName(StringBuilder builder, Class<?> type) {
            if (type.isPrimitive()) {
                switch (type.toString()) {
                    case "boolean":
                        builder.append('Z');
                        break;
                    case "byte":
                        builder.append('B');
                        break;
                    case "char":
                        builder.append('C');
                        break;
                    case "double":
                        builder.append('D');
                        break;
                    case "float":
                        builder.append('F');
                        break;
                    case "int":
                        builder.append('I');
                        break;
                    case "long":
                        builder.append('J');
                        break;
                    case "short":
                        builder.append('S');
                        break;
                    case "void":
                        builder.append('V');
                        break;
                    default:
                        throw new IllegalArgumentException("unsupported primitive: " + type);
                }
            } else {
                builder.append(ClassFileWriter.classNameToSignature(type.getName()));
            }
        }
    }

    // called by generated class
    @SuppressWarnings("unused")
    public static Object invoke(ContextFactory cf,
                                final Callable target,
                                final Scriptable topScope,
                                final Object thisObject,
                                final Class<?> javaResultType,
                                final Object[] args)
    {
        return cf.call(cx -> invokeImpl(cx, target, topScope, thisObject, javaResultType, args));
    }

    private static Object invokeImpl(Context cx,
                                     Callable target,
                                     Scriptable topScope,
                                     Object thisObject,
                                     Class<?> javaResultType,
                                     Object[] args)
    {
        WrapFactory wf = cx.getWrapFactory();
        if (args == null) {
            args = ScriptRuntime.emptyArgs;
        } else {
            args = AdapterContext.wrapToRAll(args, cx);
        }
        Scriptable thisObj = wf.wrapAsJavaObject(cx, topScope, thisObject, null);

        Object result = target.call(cx, topScope, thisObj, args);
        if (javaResultType == Void.TYPE) {
            result = null;
        } else {
            result = Context.jsToJava(result, javaResultType);
        }
        return result;
    }

    private static final String BASE_PACKAGE = "";
    private static final String SAM_IMPL_NAME = "$SaiSamAdapter";

    private static final String CallableDescriptor = ClassFileWriter.classNameToSignature(Callable.class.getName());
    private static final String ContextFactoryDescriptor = ClassFileWriter.classNameToSignature(ContextFactory.class.getName());
    private static final String ScriptableDescriptor = ClassFileWriter.classNameToSignature(Scriptable.class.getName());
    private static final String InterfaceAdapterFactoryName = InterfaceAdapterFactory.class.getName();
    private static final String ContextName = Context.class.getName();
    private static final String ContextDescriptor = ClassFileWriter.classNameToSignature(ContextName);
}
