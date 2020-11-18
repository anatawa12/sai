package com.anatawa12.sai.linker;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class MethodOrConstructor implements Serializable {
    private static final long serialVersionUID = 6566289354763847747L;

    private Executable executable;
    private Class<?>[] params;

    public MethodOrConstructor(Executable executable) {
        Objects.requireNonNull(executable);
        this.executable = executable;
    }

    public boolean isVarArgs() {
        return executable.isVarArgs();
    }

    public int parameterCount() {
        return executable.getParameterCount();
    }

    public Class<?>[] parameterArray() {
        return params != null ? params : (params = executable.getParameterTypes());
    }

    public Class<?> parameterType(int i) {
        return parameterArray()[i];
    }

    public Class<?> returnType() {
        if (executable instanceof Constructor<?>)
            return executable.getDeclaringClass();
        return ((Method)executable).getReturnType();
    }

    public boolean isStatic() {
        return Modifier.isStatic(executable.getModifiers());
    }

    public Class<?> getDeclaringClass() {
        return executable.getDeclaringClass();
    }

    public boolean isMethod() {
        return executable instanceof Method;
    }

    public boolean isConstructor() {
        return executable instanceof Constructor<?>;
    }

    public String name() {
        return executable.getName();
    }

    public Method asMethod() {
        return (Method)executable;
    }

    public Constructor<?> asConstructor() {
        return (Constructor<?>)executable;
    }

    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException
    {
        executable = ExecutableSerializer.readMember(in);
    }

    private void writeObject(ObjectOutputStream out)
            throws IOException
    {
        ExecutableSerializer.writeMember(out, executable);
    }

    private static class ExecutableSerializer {
        /**
         * Writes a Constructor or Method object.
         *
         * Methods and Constructors are not serializable, so we must serialize
         * information about the class, the name, and the parameters and
         * recreate upon deserialization.
         */
        private static void writeMember(ObjectOutputStream out, Executable member)
                throws IOException
        {
            if (!(member instanceof Method || member instanceof Constructor))
                throw new IllegalArgumentException("not Method or Constructor");
            out.writeBoolean(member instanceof Method);
            out.writeObject(member.getDeclaringClass());

            if (member instanceof Method) {
                out.writeObject(member.getName());
                writeParameters(out, member.getParameterTypes());
            } else {
                writeParameters(out, member.getParameterTypes());
            }
        }

        /**
         * Reads a Method or a Constructor from the stream.
         */
        private static Executable readMember(ObjectInputStream in)
                throws IOException, ClassNotFoundException
        {
            boolean isMethod = in.readBoolean();
            Class<?> declaring = (Class<?>) in.readObject();
            try {
                if (isMethod) {
                    String name = (String)in.readObject();
                    Class<?>[] params = readParameters(in);
                    return declaring.getMethod(name, params);
                } else {
                    Class<?>[] params = readParameters(in);
                    return declaring.getConstructor(params);
                }
            } catch (NoSuchMethodException e) {
                throw new IOException("Cannot find member: " + e);
            }
        }

        /**
         * Writes an array of parameter types to the stream.
         *
         * Requires special handling because primitive types cannot be
         * found upon deserialization by the default Java implementation.
         */
        private static void writeParameters(ObjectOutputStream out, Class<?>[] parms)
                throws IOException
        {
            out.writeShort(parms.length);
            for (int i=0; i < parms.length; i++) {
                writeClass(out, parms[i]);
            }
        }

        /**
         * Reads an array of parameter types from the stream.
         */
        private static Class<?>[] readParameters(ObjectInputStream in)
                throws IOException, ClassNotFoundException
        {
            Class<?>[] result = new Class[in.readShort()];
            for (int i=0; i < result.length; i++) {
                result[i] = readClass(in);
            }
            return result;
        }

        private static final Class<?>[] preDefined = {
                null, // 0: reference type
                Boolean.TYPE,
                Byte.TYPE,
                Character.TYPE,
                Double.TYPE,
                Float.TYPE,
                Integer.TYPE,
                Long.TYPE,
                Short.TYPE,
                Void.TYPE,
        };

        private static final Map<Class<?>, Integer> preDefinedIndices;

        static {
            HashMap<Class<?>, Integer> preDefinedIndices1 = new HashMap<>();
            // 0 is null
            for (int i = 1; i < preDefined.length; i++) {
                preDefinedIndices1.put(preDefined[i], i);
            }
            preDefinedIndices = preDefinedIndices1;
        }

        private static void writeClass(ObjectOutputStream out, Class<?> clazz)
                throws IOException {
            Integer index = preDefinedIndices.get(clazz);
            if (index != null) {
                out.writeByte(index);
            } else {
                if (clazz.isPrimitive()) throw new IOException("invalid primitive: " + clazz);
                out.writeByte(0);
                out.writeObject(clazz.getName());
            }
        }

        private static Class<?> readClass(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
            int index = in.readByte();
            if (index != 0) {
                if (index < 0 || index >= preDefined.length)
                    throw new IOException("invalid predefined type index");
                return preDefined[index];
            } else {
                String name = (String)in.readObject();
                return Class.forName(name, false, ExecutableSerializer.class.getClassLoader());
            }
        }
    }
}
