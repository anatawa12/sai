import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

public class ReplaceMHHSCall extends DefaultTask {
    private Iterable<File> classpath = Collections.emptyList();

    @InputFiles
    public Iterable<File> getClasspath() {
        return classpath;
    }

    public void setClasspath(Iterable<File> classpath) {
        this.classpath = classpath;
    }

    private String mhhClass;
    private String mhhInternalName;
    private String methodHandleClass;
    private String methodHandleInternalName;
    private boolean bindEnabled;

    @Input
    public String getMhhClass() {
        return mhhClass;
    }

    public void setMhhClass(String mhhClass) {
        this.mhhClass = mhhClass;
    }

    @Input
    public String getMethodHandleClass() {
        return methodHandleClass;
    }

    public void setMethodHandleClass(String methodHandleClass) {
        this.methodHandleClass = methodHandleClass;
    }

    @Input
    public boolean getBindEnabled() {
        return bindEnabled;
    }

    public void setBindEnabled(boolean bindEnabled) {
        this.bindEnabled = bindEnabled;
    }

    @TaskAction
    protected void execute() throws IOException {
        if (mhhClass == null) throw new IllegalStateException("mhhClass is not specified");
        mhhInternalName = mhhClass.replace('.', '/');
        methodHandleInternalName = methodHandleClass.replace('.', '/');
        for (File file : classpath) {
            processDirectory(file);
        }
    }

    private void processDirectory(File dir) throws IOException {
        if (!dir.exists()) return;

        Files.walk(dir.toPath(), FileVisitOption.FOLLOW_LINKS)
                .filter(path -> path.toString().endsWith(".class"))
                .forEach(this::processClassFile);
    }

    private void processClassFile(Path path) {
        try {
            if (!Files.isRegularFile(path)) return;

            ClassReader readClass = new ClassReader(Files.readAllBytes(path));
            ClassNode node = new ClassNode();
            readClass.accept(node, 0);
            boolean modified = false;
            for (MethodNode method : node.methods) {
                modified |= processMethod(node.name, method);
            }
            if (!modified) return;
            ClassWriter writer = new ClassWriter(readClass, 0);
            node.accept(writer);

            Files.write(path, writer.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean processMethod(String internalName, MethodNode method) {
        boolean modified = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode methodInsnNode = (MethodInsnNode) instruction;
            if (methodInsnNode.owner.equals(mhhInternalName)
                    && isCreateMethod(methodInsnNode.name, methodInsnNode.desc)) {
                processCreateMethodHandleInsn(internalName, method, methodInsnNode);
                modified = true;
            }
        }
        return modified;
    }

    private void processCreateMethodHandleInsn(
            String internalName,
            MethodNode method,
            MethodInsnNode methodInsnNode
    ) {
        AbstractInsnNode prevInsn = methodInsnNode.getPrevious();
        while (prevInsn.getOpcode() == -1) prevInsn = prevInsn.getPrevious();
        if (!(prevInsn instanceof InvokeDynamicInsnNode))
            throw throwNonLambda(internalName, method, methodInsnNode, "non-lambda or non-method reference");

        InvokeDynamicInsnNode indy = (InvokeDynamicInsnNode) prevInsn;
        Handle bsm = indy.bsm;
        if (!bsm.getOwner().equals("java/lang/invoke/LambdaMetafactory"))
            throw throwNonLambda(internalName, method, methodInsnNode, "non-lambda or non-method reference");
        // should allow single for instance::method with MethodHandle.bindTo
        Type[] args = Type.getArgumentTypes(indy.desc);
        boolean withBind;
        if (args.length == 0) {
            // ok
            withBind = false;
        } else if (bindEnabled && args.length == 1) {
            withBind = true;
            // ok with bind
            if (args[0].getSort() != Type.OBJECT)
                throw throwNonLambda(internalName, method, methodInsnNode, "receiver is not a object");
        } else {
            throw throwNonLambda(internalName, method, methodInsnNode, "lambda with capture");
        }

        Handle handle;
        if (bsm.getName().equals("metafactory")) {
            handle = (Handle)indy.bsmArgs[1];
        } else if (bsm.getName().equals("altMetafactory")) {
            handle = (Handle)indy.bsmArgs[1];
        } else {
            throw throwNonLambda(internalName, method, methodInsnNode, "non-lambda or non-method reference");
        }

        AbstractInsnNode last = indy;
        method.instructions.set(last, last = new LdcInsnNode(handle));
        method.instructions.insert(last,
                last = new MethodInsnNode(Opcodes.INVOKESTATIC,
                        methodHandleInternalName,
                        "direct",
                        "(Ljava/lang/invoke/MethodHandle;)L" + methodHandleInternalName + ";"));
        if (withBind) {
            method.instructions.insert(last, last =  new InsnNode(Opcodes.SWAP));
            method.instructions.insert(last,
                    last = new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                            methodHandleInternalName,
                            "bindTo",
                            "(Ljava/lang/Object;)L" + methodHandleInternalName + ";"));
        }
        method.instructions.remove(methodInsnNode);
    }

    private static IllegalArgumentException throwNonLambda(
            String internalName,
            MethodNode method,
            MethodInsnNode methodInsnNode,
            String message
    ) {
        throw new IllegalArgumentException(
                "The method " + internalName + "." + method.name + ":" + method.desc
                        + " has invocation to " + methodInsnNode.name + " passing "
                        + message
        );
    }

    private boolean isCreateMethod(String name, String desc) {
        // first, check its starts with 'create'
        if (!name.startsWith("create")) return false;
        int i = "create".length();
        StringBuilder countString = new StringBuilder();
        // then check 'create' is followed by number
        while (true) {
            if (i == name.length()) return false;
            if (!isNumberChar(name.charAt(i))) break;
            countString.append(name.charAt(i));
            i++;
        }

        // then check the number is followed by 'r' or 'v'
        boolean isVoid;
        if (name.charAt(i) == 'r') {
            isVoid = false;
        } else if (name.charAt(i) == 'v') {
            isVoid = true;
        } else {
            return false;
        }
        i++;

        // then check the 'r' or 'v' is followed by 'v' if there's following characters.
        boolean isVararg = false;
        if (i != name.length()) {
            isVararg = true;
            if (name.charAt(i) != 'v') return false;
            i++;
            // 'v' must be last character of name
            if (i != name.length()) return false;
        }

        // check desc.
        StringBuilder expectSignature = new StringBuilder();
        expectSignature.append('(');
        expectSignature.append('L');
        expectSignature.append(mhhInternalName);
        expectSignature.append('$');
        if (!isVoid) expectSignature.append("Non");
        expectSignature.append("Void");
        if (isVararg) expectSignature.append("Vararg");
        expectSignature.append("Function");
        expectSignature.append(countString);
        expectSignature.append(";)L").append(methodHandleInternalName).append(";");

        return expectSignature.toString().equals(desc);
    }

    private static boolean isNumberChar(char c) {
        return '0' <= c && c <= '9';
    }
}
