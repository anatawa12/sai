/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.anatawa12.sai.optimizer;

import com.anatawa12.sai.CompilerEnvirons;
import com.anatawa12.sai.Context;
import com.anatawa12.sai.Evaluator;
import com.anatawa12.sai.FileNameMapping;
import com.anatawa12.sai.Function;
import com.anatawa12.sai.GeneratedClassLoader;
import com.anatawa12.sai.Kit;
import com.anatawa12.sai.NativeFunction;
import com.anatawa12.sai.ObjArray;
import com.anatawa12.sai.ObjToIntMap;
import com.anatawa12.sai.Parser;
import com.anatawa12.sai.RhinoException;
import com.anatawa12.sai.Script;
import com.anatawa12.sai.Scriptable;
import com.anatawa12.sai.SecurityController;
import com.anatawa12.sai.Token;
import com.anatawa12.sai.ast.FunctionNode;
import com.anatawa12.sai.ast.Name;
import com.anatawa12.sai.ast.ScriptNode;
import com.anatawa12.sai.classfile.ByteCode;
import com.anatawa12.sai.classfile.ClassFileWriter;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.anatawa12.sai.classfile.ClassFileWriter.ACC_FINAL;
import static com.anatawa12.sai.classfile.ClassFileWriter.ACC_PRIVATE;
import static com.anatawa12.sai.classfile.ClassFileWriter.ACC_PROTECTED;
import static com.anatawa12.sai.classfile.ClassFileWriter.ACC_PUBLIC;
import static com.anatawa12.sai.classfile.ClassFileWriter.ACC_STATIC;
import static com.anatawa12.sai.classfile.ClassFileWriter.ACC_VOLATILE;

/**
 * This class generates code for a given IR tree.
 *
 * @author Norris Boyd
 * @author Roger Lawrence
 */

public class Codegen implements Evaluator
{
    @Override
    public void captureStackInfo(RhinoException ex) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSourcePositionFromStack(Context cx, int[] linep) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getPatchedStack(RhinoException ex, String nativeStackTrace) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getScriptStack(RhinoException ex) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setEvalScriptFlag(Script script) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Object compile(CompilerEnvirons compilerEnv,
                          ScriptNode tree,
                          String encodedSource,
                          String realSource,
                          boolean returnFunction)
    {
        this.sourceString = realSource;
        int serial;
        synchronized (globalLock) {
            serial = ++globalSerialClassCounter;
        }

        String baseName = "c";
        if (tree.getSourceName().length() > 0) {
          baseName = tree.getSourceName().replaceAll("\\W", "_");
          if (!Character.isJavaIdentifierStart(baseName.charAt(0))) {
            baseName = "_" + baseName;
          }
        }

        String mainClassName = "com.anatawa12.sai.gen." + baseName + "_" + serial;

        byte[] mainClassBytes = compileToClassFile(compilerEnv, mainClassName,
                                                   tree, encodedSource,
                                                   returnFunction);

        return new Object[] { mainClassName, mainClassBytes };
    }

    @Override
    public Script createScriptObject(Object bytecode,
                                     Object staticSecurityDomain)
    {
        Class<?> cl = defineClass(bytecode, staticSecurityDomain);

        Script script;
        try {
            script = (Script)cl.newInstance();
        } catch (Exception ex) {
            throw new RuntimeException
                ("Unable to instantiate compiled class:" + ex.toString());
        }
        return script;
    }

    @Override
    public Function createFunctionObject(Context cx, Scriptable scope,
                                         Object bytecode,
                                         Object staticSecurityDomain)
    {
        Class<?> cl = defineClass(bytecode, staticSecurityDomain);

        NativeFunction f;
        try {
            Constructor<?>ctor = cl.getConstructors()[0];
            Object[] initArgs = { scope, cx, Integer.valueOf(0) };
            f = (NativeFunction)ctor.newInstance(initArgs);
        } catch (Exception ex) {
            throw new RuntimeException
                ("Unable to instantiate compiled class:"+ex.toString());
        }
        return f;
    }

    private Class<?> defineClass(Object bytecode,
                                 Object staticSecurityDomain)
    {
        Object[] nameBytesPair = (Object[])bytecode;
        String className = (String)nameBytesPair[0];
        byte[] classBytes = (byte[])nameBytesPair[1];

        // The generated classes in this case refer only to Rhino classes
        // which must be accessible through this class loader
        ClassLoader rhinoLoader = getClass().getClassLoader();
        GeneratedClassLoader loader;
        loader = SecurityController.createLoader(rhinoLoader,
                                                 staticSecurityDomain);
        Exception e;
        try {
            Class<?> cl = loader.defineClass(className, classBytes);
            loader.linkClass(cl);
            return cl;
        } catch (SecurityException x) {
            e = x;
        } catch (IllegalArgumentException x) {
            e = x;
        }
        throw new RuntimeException("Malformed optimizer package " + e);
    }

    public byte[] compileToClassFile(CompilerEnvirons compilerEnv,
                                     String mainClassName,
                                     ScriptNode scriptOrFn,
                                     String encodedSource,
                                     boolean returnFunction)
    {
        this.compilerEnv = compilerEnv;

        transform(scriptOrFn);

        if (Token.printTrees) {
            System.out.println(scriptOrFn.toStringTree(scriptOrFn));
        }

        if (returnFunction) {
            scriptOrFn = scriptOrFn.getFunctionNode(0);
        }

        initScriptNodesData(scriptOrFn);

        this.mainClassName = mainClassName;
        this.mainClassSignature = ClassFileWriter.classNameToSignature(mainClassName);

        return generateCode(encodedSource);
    }

    private void transform(ScriptNode tree)
    {
        initOptFunctions_r(tree);

        int optLevel = compilerEnv.getOptimizationLevel();

        Map<String,OptFunctionNode> possibleDirectCalls = null;
        if (optLevel > 0) {
           /*
            * Collect all of the contained functions into a hashtable
            * so that the call optimizer can access the class name & parameter
            * count for any call it encounters
            */
            if (tree.getType() == Token.SCRIPT) {
                int functionCount = tree.getFunctionCount();
                for (int i = 0; i != functionCount; ++i) {
                    OptFunctionNode ofn = OptFunctionNode.get(tree, i);
                    if (ofn.fnode.getFunctionType()
                        == FunctionNode.FUNCTION_STATEMENT)
                    {
                        String name = ofn.fnode.getName();
                        if (name.length() != 0) {
                            if (possibleDirectCalls == null) {
                                possibleDirectCalls = new HashMap<String,OptFunctionNode>();
                            }
                            possibleDirectCalls.put(name, ofn);
                        }
                    }
                }
            }
        }

        if (possibleDirectCalls != null) {
            directCallTargets = new ObjArray();
        }

        OptTransformer ot = new OptTransformer(possibleDirectCalls,
                                               directCallTargets);
        ot.transform(tree, compilerEnv);

        if (optLevel > 0) {
            (new Optimizer()).optimize(tree);
        }
    }

    private static void initOptFunctions_r(ScriptNode scriptOrFn)
    {
        for (int i = 0, N = scriptOrFn.getFunctionCount(); i != N; ++i) {
            FunctionNode fn = scriptOrFn.getFunctionNode(i);
            new OptFunctionNode(fn);
            initOptFunctions_r(fn);
        }
    }

    private void initScriptNodesData(ScriptNode scriptOrFn)
    {
        ObjArray x = new ObjArray();
        collectScriptNodes_r(scriptOrFn, x);

        int count = x.size();
        scriptOrFnNodes = new ScriptNode[count];
        x.toArray(scriptOrFnNodes);

        scriptOrFnIndexes = new ObjToIntMap(count);
        for (int i = 0; i != count; ++i) {
            scriptOrFnIndexes.put(scriptOrFnNodes[i], i);
        }
    }

    private static void collectScriptNodes_r(ScriptNode n,
                                                 ObjArray x)
    {
        x.add(n);
        int nestedCount = n.getFunctionCount();
        for (int i = 0; i != nestedCount; ++i) {
            collectScriptNodes_r(n.getFunctionNode(i), x);
        }
    }

    private byte[] generateCode(String encodedSource)
    {
        boolean hasScript = (scriptOrFnNodes[0].getType() == Token.SCRIPT);
        boolean hasFunctions = (scriptOrFnNodes.length > 1 || !hasScript);
        boolean isStrictMode = scriptOrFnNodes[0].isInStrictMode();

        String sourceFile = null;
        if (compilerEnv.isGenerateDebugInfo()) {
            if (compilerEnv.isSaiDirectiveEnabled()) {
                sourceFile = "sai-adapter-" + UUID.randomUUID();
            } else {
                sourceFile = scriptOrFnNodes[0].getSourceName();
            }
        }

        ClassFileWriter cfw = new ClassFileWriter(mainClassName,
                                                  SUPER_CLASS_NAME,
                                                  sourceFile);
        cfw.addField(ID_FIELD_NAME, "I", ACC_PRIVATE);

        if (hasFunctions) {
            generateFunctionConstructor(cfw);
        }

        if (hasScript) {
            cfw.addInterface("com/anatawa12/sai/Script");
            generateScriptCtor(cfw);
            generateMain(cfw);
            generateExecute(cfw);
        }

        generateCallMethod(cfw, isStrictMode);
        generateResumeGenerator(cfw);

        generateNativeFunctionOverrides(cfw, encodedSource);

        int count = scriptOrFnNodes.length;
        for (int i = 0; i != count; ++i) {
            ScriptNode n = scriptOrFnNodes[i];

            BodyCodegen bodygen = new BodyCodegen();
            bodygen.cfw = cfw;
            bodygen.codegen = this;
            bodygen.compilerEnv = compilerEnv;
            bodygen.scriptOrFn = n;
            bodygen.scriptOrFnIndex = i;

            bodygen.generateBodyCode();

            if (n.getType() == Token.FUNCTION) {
                OptFunctionNode ofn = OptFunctionNode.get(n);
                generateFunctionInit(cfw, ofn);
                if (ofn.isTargetOfDirectCall()) {
                    emitDirectConstructor(cfw, ofn);
                }
            }
        }

        emitRegExpInit(cfw);

        if (compilerEnv.isSaiDirectiveEnabled()) {
            cfw.addField(MAPPING_FIELD_NAME, "Lcom/anatawa12/sai/FileNameMapping;", (short)(ACC_PRIVATE | ACC_STATIC));
            emitGetFileNameMapping(cfw);
        }

        emitConstantDudeInitializers(cfw,
                compilerEnv.isSaiDirectiveEnabled() ? sourceFile : null);

        return cfw.toByteArray();
    }

    private void emitGetFileNameMapping(ClassFileWriter cfw) {
        cfw.startMethod("getFileNameMapping", "()Lcom/anatawa12/sai/FileNameMapping;", ACC_PROTECTED);
        cfw.add(ByteCode.GETSTATIC, mainClassName, MAPPING_FIELD_NAME, "Lcom/anatawa12/sai/FileNameMapping;");
        cfw.add(ByteCode.ARETURN);
        // only this
        cfw.stopMethod((short) 1);
    }

    private void emitDirectConstructor(ClassFileWriter cfw,
                                       OptFunctionNode ofn)
    {
/*
    we generate ..
        Scriptable directConstruct(<directCallArgs>) {
            Scriptable newInstance = createObject(cx, scope);
            Object val = <body-name>(cx, scope, newInstance, <directCallArgs>);
            if (val instanceof Scriptable) {
                return (Scriptable) val;
            }
            return newInstance;
        }
*/
        cfw.startMethod(getDirectCtorName(ofn.fnode),
                        getBodyMethodSignature(ofn.fnode),
                        (short)(ACC_STATIC | ACC_PRIVATE));

        int argCount = ofn.fnode.getParamCount();
        int firstLocal = (4 + argCount * 3) + 1;

        cfw.addALoad(0); // this
        cfw.addALoad(1); // cx
        cfw.addALoad(2); // scope
        cfw.addInvoke(ByteCode.INVOKEVIRTUAL,
                      "com/anatawa12/sai/BaseFunction",
                      "createObject",
                      "(Lcom/anatawa12/sai/Context;"
                      +"Lcom/anatawa12/sai/Scriptable;"
                      +")Lcom/anatawa12/sai/Scriptable;");
        cfw.addAStore(firstLocal);

        cfw.addALoad(0);
        cfw.addALoad(1);
        cfw.addALoad(2);
        cfw.addALoad(firstLocal);
        for (int i = 0; i < argCount; i++) {
            cfw.addALoad(4 + (i * 3));
            cfw.addDLoad(5 + (i * 3));
        }
        cfw.addALoad(4 + argCount * 3);
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      mainClassName,
                      getBodyMethodName(ofn.fnode),
                      getBodyMethodSignature(ofn.fnode));
        int exitLabel = cfw.acquireLabel();
        cfw.add(ByteCode.DUP); // make a copy of direct call result
        cfw.add(ByteCode.INSTANCEOF, "com/anatawa12/sai/Scriptable");
        cfw.add(ByteCode.IFEQ, exitLabel);
        // cast direct call result
        cfw.add(ByteCode.CHECKCAST, "com/anatawa12/sai/Scriptable");
        cfw.add(ByteCode.ARETURN);
        cfw.markLabel(exitLabel);

        cfw.addALoad(firstLocal);
        cfw.add(ByteCode.ARETURN);

        cfw.stopMethod((short)(firstLocal + 1));
    }

    static boolean isGenerator(ScriptNode node)
    {
        return (node.getType() == Token.FUNCTION ) &&
                ((FunctionNode)node).isGenerator();
    }

    // How dispatch to generators works:
    // Two methods are generated corresponding to a user-written generator.
    // One of these creates a generator object (NativeGenerator), which is
    // returned to the user. The other method contains all of the body code
    // of the generator.
    // When a user calls a generator, the call() method dispatches control to
    // to the method that creates the NativeGenerator object. Subsequently when
    // the user invokes .next(), .send() or any such method on the generator
    // object, the resumeGenerator() below dispatches the call to the
    // method corresponding to the generator body. As a matter of convention
    // the generator body is given the name of the generator activation function
    // appended by "_gen".
    private void generateResumeGenerator(ClassFileWriter cfw)
    {
        boolean hasGenerators = false;
        for (int i=0; i < scriptOrFnNodes.length; i++) {
            if (isGenerator(scriptOrFnNodes[i]))
                hasGenerators = true;
        }

        // if there are no generators defined, we don't implement a
        // resumeGenerator(). The base class provides a default implementation.
        if (!hasGenerators)
            return;

        cfw.startMethod("resumeGenerator",
                        "(Lcom/anatawa12/sai/Context;" +
                        "Lcom/anatawa12/sai/Scriptable;" +
                        "ILjava/lang/Object;" +
                        "Ljava/lang/Object;)Ljava/lang/Object;",
                        (short)(ACC_PUBLIC | ACC_FINAL));

        // load arguments for dispatch to the corresponding *_gen method
        cfw.addALoad(0);
        cfw.addALoad(1);
        cfw.addALoad(2);
        cfw.addALoad(4);
        cfw.addALoad(5);
        cfw.addILoad(3);

        cfw.addLoadThis();
        cfw.add(ByteCode.GETFIELD, cfw.getClassName(), ID_FIELD_NAME, "I");

        int startSwitch = cfw.addTableSwitch(0, scriptOrFnNodes.length - 1);
        cfw.markTableSwitchDefault(startSwitch);
        int endlabel = cfw.acquireLabel();

        for (int i = 0; i < scriptOrFnNodes.length; i++) {
            ScriptNode n = scriptOrFnNodes[i];
            cfw.markTableSwitchCase(startSwitch, i, (short)6);
            if (isGenerator(n)) {
                String type = "(" +
                              mainClassSignature +
                              "Lcom/anatawa12/sai/Context;" +
                              "Lcom/anatawa12/sai/Scriptable;" +
                              "Ljava/lang/Object;" +
                              "Ljava/lang/Object;I)Ljava/lang/Object;";
                cfw.addInvoke(ByteCode.INVOKESTATIC,
                              mainClassName,
                              getBodyMethodName(n) + "_gen",
                              type);
                cfw.add(ByteCode.ARETURN);
            } else {
                cfw.add(ByteCode.GOTO, endlabel);
            }
        }

        cfw.markLabel(endlabel);
        pushUndefined(cfw);
        cfw.add(ByteCode.ARETURN);


        // this method uses as many locals as there are arguments (hence 6)
        cfw.stopMethod((short)6);
    }

    private void generateCallMethod(ClassFileWriter cfw, boolean isStrictMode)
    {
        cfw.startMethod("call",
                        "(Lcom/anatawa12/sai/Context;" +
                        "Lcom/anatawa12/sai/Scriptable;" +
                        "Lcom/anatawa12/sai/Scriptable;" +
                        "[Ljava/lang/Object;)Ljava/lang/Object;",
                        (short)(ACC_PUBLIC | ACC_FINAL));

        // Generate code for:
        // if (!ScriptRuntime.hasTopCall(cx)) {
        //     return ScriptRuntime.doTopCall(this, cx, scope, thisObj, args);
        // }

        int nonTopCallLabel = cfw.acquireLabel();
        cfw.addALoad(1); //cx
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      "com/anatawa12/sai/ScriptRuntime",
                      "hasTopCall",
                      "(Lcom/anatawa12/sai/Context;"
                      +")Z");
        cfw.add(ByteCode.IFNE, nonTopCallLabel);
        cfw.addALoad(0);
        cfw.addALoad(1);
        cfw.addALoad(2);
        cfw.addALoad(3);
        cfw.addALoad(4);
        cfw.addPush(isStrictMode);
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      "com/anatawa12/sai/ScriptRuntime",
                      "doTopCall",
                      "(Lcom/anatawa12/sai/Callable;"
                      +"Lcom/anatawa12/sai/Context;"
                      +"Lcom/anatawa12/sai/Scriptable;"
                      +"Lcom/anatawa12/sai/Scriptable;"
                      +"[Ljava/lang/Object;"
                      +"Z"
                      +")Ljava/lang/Object;");
        cfw.add(ByteCode.ARETURN);
        cfw.markLabel(nonTopCallLabel);

        int tryStart = cfw.acquireLabel();
        cfw.markLabel(tryStart);

        // Now generate switch to call the real methods
        cfw.addALoad(0);
        cfw.addALoad(1);
        cfw.addALoad(2);
        cfw.addALoad(3);
        cfw.addALoad(4);

        int end = scriptOrFnNodes.length;
        boolean generateSwitch = (2 <= end);

        int switchStart = 0;
        int switchStackTop = 0;
        if (generateSwitch) {
            cfw.addLoadThis();
            cfw.add(ByteCode.GETFIELD, cfw.getClassName(), ID_FIELD_NAME, "I");
            // do switch from (1,  end - 1) mapping 0 to
            // the default case
            switchStart = cfw.addTableSwitch(1, end - 1);
        }

        for (int i = 0; i != end; ++i) {
            ScriptNode n = scriptOrFnNodes[i];
            if (generateSwitch) {
                if (i == 0) {
                    cfw.markTableSwitchDefault(switchStart);
                    switchStackTop = cfw.getStackTop();
                } else {
                    cfw.markTableSwitchCase(switchStart, i - 1,
                                            switchStackTop);
                }
            }
            if (n.getType() == Token.FUNCTION) {
                OptFunctionNode ofn = OptFunctionNode.get(n);
                if (ofn.isTargetOfDirectCall()) {
                    int pcount = ofn.fnode.getParamCount();
                    if (pcount != 0) {
                        // loop invariant:
                        // stack top == arguments array from addALoad4()
                        for (int p = 0; p != pcount; ++p) {
                            cfw.add(ByteCode.ARRAYLENGTH);
                            cfw.addPush(p);
                            int undefArg = cfw.acquireLabel();
                            int beyond = cfw.acquireLabel();
                            cfw.add(ByteCode.IF_ICMPLE, undefArg);
                            // get array[p]
                            cfw.addALoad(4);
                            cfw.addPush(p);
                            cfw.add(ByteCode.AALOAD);
                            cfw.add(ByteCode.GOTO, beyond);
                            cfw.markLabel(undefArg);
                            pushUndefined(cfw);
                            cfw.markLabel(beyond);
                            // Only one push
                            cfw.adjustStackTop(-1);
                            cfw.addPush(0.0);
                            // restore invariant
                            cfw.addALoad(4);
                        }
                    }
                }
            }
            cfw.addInvoke(ByteCode.INVOKESTATIC,
                          mainClassName,
                          getBodyMethodName(n),
                          getBodyMethodSignature(n));
            cfw.add(ByteCode.ARETURN);
        }

        if (compilerEnv.isSaiDirectiveEnabled() && compilerEnv.isGenerateDebugInfo()) {
            int tryEnd = cfw.acquireLabel();
            cfw.markLabel(tryEnd);
            int handleStart = cfw.acquireLabel();
            cfw.markHandler(handleStart);

            cfw.addAStore(5);
            cfw.addALoad(5);
            cfw.addInvoke(ByteCode.INVOKESTATIC, "com.anatawa12.sai.StackTraceEditor",
                    "editTrace",
                    "(Ljava/lang/Throwable;)V");
            cfw.addALoad(5);
            cfw.add(ByteCode.ATHROW);

            cfw.addExceptionHandler(tryStart, tryEnd, handleStart, "java.lang.Throwable");
        }

        cfw.stopMethod((short)6);
        // 5: this, cx, scope, js this, args[], throwable
    }

    private void generateMain(ClassFileWriter cfw)
    {
        cfw.startMethod("main", "([Ljava/lang/String;)V",
                        (short)(ACC_PUBLIC | ACC_STATIC));

        // load new ScriptImpl()
        cfw.add(ByteCode.NEW, cfw.getClassName());
        cfw.add(ByteCode.DUP);
        cfw.addInvoke(ByteCode.INVOKESPECIAL, cfw.getClassName(),
                      "<init>", "()V");
         // load 'args'
        cfw.add(ByteCode.ALOAD_0);
        // Call mainMethodClass.main(Script script, String[] args)
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      mainMethodClass,
                      "main",
                      "(Lcom/anatawa12/sai/Script;[Ljava/lang/String;)V");
        cfw.add(ByteCode.RETURN);
        // 1 = String[] args
        cfw.stopMethod((short)1);
    }

    private static void generateExecute(ClassFileWriter cfw)
    {
        cfw.startMethod("exec",
                        "(Lcom/anatawa12/sai/Context;"
                        +"Lcom/anatawa12/sai/Scriptable;"
                        +")Ljava/lang/Object;",
                        (short)(ACC_PUBLIC | ACC_FINAL));

        final int CONTEXT_ARG = 1;
        final int SCOPE_ARG = 2;

        cfw.addLoadThis();
        cfw.addALoad(CONTEXT_ARG);
        cfw.addALoad(SCOPE_ARG);
        cfw.add(ByteCode.DUP);
        cfw.add(ByteCode.ACONST_NULL);
        cfw.addInvoke(ByteCode.INVOKEVIRTUAL,
                      cfw.getClassName(),
                      "call",
                      "(Lcom/anatawa12/sai/Context;"
                      +"Lcom/anatawa12/sai/Scriptable;"
                      +"Lcom/anatawa12/sai/Scriptable;"
                      +"[Ljava/lang/Object;"
                      +")Ljava/lang/Object;");

        cfw.add(ByteCode.ARETURN);
        // 3 = this + context + scope
        cfw.stopMethod((short)3);
    }

    private static void generateScriptCtor(ClassFileWriter cfw)
    {
        cfw.startMethod("<init>", "()V", ACC_PUBLIC);

        cfw.addLoadThis();
        cfw.addInvoke(ByteCode.INVOKESPECIAL, SUPER_CLASS_NAME,
                      "<init>", "()V");
        // set id to 0
        cfw.addLoadThis();
        cfw.addPush(0);
        cfw.add(ByteCode.PUTFIELD, cfw.getClassName(), ID_FIELD_NAME, "I");

        cfw.add(ByteCode.RETURN);
        // 1 parameter = this
        cfw.stopMethod((short)1);
    }

    private void generateFunctionConstructor(ClassFileWriter cfw)
    {
        final int SCOPE_ARG = 1;
        final int CONTEXT_ARG = 2;
        final int ID_ARG = 3;

        cfw.startMethod("<init>", FUNCTION_CONSTRUCTOR_SIGNATURE, ACC_PUBLIC);
        cfw.addALoad(0);
        cfw.addInvoke(ByteCode.INVOKESPECIAL, SUPER_CLASS_NAME,
                      "<init>", "()V");

        cfw.addLoadThis();
        cfw.addILoad(ID_ARG);
        cfw.add(ByteCode.PUTFIELD, cfw.getClassName(), ID_FIELD_NAME, "I");

        cfw.addLoadThis();
        cfw.addALoad(CONTEXT_ARG);
        cfw.addALoad(SCOPE_ARG);

        int start = (scriptOrFnNodes[0].getType() == Token.SCRIPT) ? 1 : 0;
        int end = scriptOrFnNodes.length;
        if (start == end) throw badTree();
        boolean generateSwitch = (2 <= end - start);

        int switchStart = 0;
        int switchStackTop = 0;
        if (generateSwitch) {
            cfw.addILoad(ID_ARG);
            // do switch from (start + 1,  end - 1) mapping start to
            // the default case
            switchStart = cfw.addTableSwitch(start + 1, end - 1);
        }

        for (int i = start; i != end; ++i) {
            if (generateSwitch) {
                if (i == start) {
                    cfw.markTableSwitchDefault(switchStart);
                    switchStackTop = cfw.getStackTop();
                } else {
                    cfw.markTableSwitchCase(switchStart, i - 1 - start,
                                            switchStackTop);
                }
            }
            OptFunctionNode ofn = OptFunctionNode.get(scriptOrFnNodes[i]);
            cfw.addInvoke(ByteCode.INVOKESPECIAL,
                          mainClassName,
                          getFunctionInitMethodName(ofn),
                          FUNCTION_INIT_SIGNATURE);
            cfw.add(ByteCode.RETURN);
        }

        // 4 = this + scope + context + id
        cfw.stopMethod((short)4);
    }

    private void generateFunctionInit(ClassFileWriter cfw,
                                      OptFunctionNode ofn)
    {
        final int CONTEXT_ARG = 1;
        final int SCOPE_ARG = 2;
        cfw.startMethod(getFunctionInitMethodName(ofn),
                        FUNCTION_INIT_SIGNATURE,
                        (short)(ACC_PRIVATE | ACC_FINAL));

        // Call NativeFunction.initScriptFunction
        cfw.addLoadThis();
        cfw.addALoad(CONTEXT_ARG);
        cfw.addALoad(SCOPE_ARG);
        cfw.addInvoke(ByteCode.INVOKEVIRTUAL,
                      "com/anatawa12/sai/NativeFunction",
                      "initScriptFunction",
                      "(Lcom/anatawa12/sai/Context;"
                      +"Lcom/anatawa12/sai/Scriptable;"
                      +")V");

        // precompile all regexp literals
        if (ofn.fnode.getRegexpCount() != 0) {
            cfw.addALoad(CONTEXT_ARG);
            cfw.addInvoke(ByteCode.INVOKESTATIC, mainClassName,
                          REGEXP_INIT_METHOD_NAME, REGEXP_INIT_METHOD_SIGNATURE);
        }

        cfw.add(ByteCode.RETURN);
        // 3 = (scriptThis/functionRef) + scope + context
        cfw.stopMethod((short)3);
    }

    private void generateNativeFunctionOverrides(ClassFileWriter cfw,
                                                 String encodedSource)
    {
        // Override NativeFunction.getLanguageVersion() with
        // public int getLanguageVersion() { return <version-constant>; }

        cfw.startMethod("getLanguageVersion", "()I", ACC_PUBLIC);

        cfw.addPush(compilerEnv.getLanguageVersion());
        cfw.add(ByteCode.IRETURN);

        // 1: this and no argument or locals
        cfw.stopMethod((short)1);

        // The rest of NativeFunction overrides require specific code for each
        // script/function id

        final int Do_getFunctionName      = 0;
        final int Do_getParamCount        = 1;
        final int Do_getParamAndVarCount  = 2;
        final int Do_getParamOrVarName    = 3;
        final int Do_getEncodedSource     = 4;
        final int Do_getParamOrVarConst   = 5;
        final int Do_isGeneratorFunction  = 6;
        final int Do_getRealSource        = 7;
        final int Do_getSourceStartLineNo = 8;
        final int SWITCH_COUNT            = 9;

        for (int methodIndex = 0; methodIndex != SWITCH_COUNT; ++methodIndex) {
            if (methodIndex == Do_getEncodedSource && encodedSource == null) {
                continue;
            }
            if (methodIndex == Do_getRealSource && sourceString == null) {
                continue;
            }

            // Generate:
            //   prologue;
            //   switch over function id to implement function-specific action
            //   epilogue

            short methodLocals;
            switch (methodIndex) {
              case Do_getFunctionName:
                methodLocals = 1; // Only this
                cfw.startMethod("getFunctionName", "()Ljava/lang/String;",
                                ACC_PUBLIC);
                break;
              case Do_getParamCount:
                methodLocals = 1; // Only this
                cfw.startMethod("getParamCount", "()I",
                                ACC_PUBLIC);
                break;
              case Do_getParamAndVarCount:
                methodLocals = 1; // Only this
                cfw.startMethod("getParamAndVarCount", "()I",
                                ACC_PUBLIC);
                break;
              case Do_getParamOrVarName:
                methodLocals = 1 + 1; // this + paramOrVarIndex
                cfw.startMethod("getParamOrVarName", "(I)Ljava/lang/String;",
                                ACC_PUBLIC);
                break;
              case Do_getParamOrVarConst:
                methodLocals = 1 + 1 + 1; // this + paramOrVarName
                cfw.startMethod("getParamOrVarConst", "(I)Z",
                                ACC_PUBLIC);
                break;
              case Do_getEncodedSource:
                methodLocals = 1; // Only this
                cfw.startMethod("getEncodedSource", "()Ljava/lang/String;",
                                ACC_PUBLIC);
                cfw.addPush(encodedSource);
                break;
              case Do_isGeneratorFunction:
                methodLocals = 1; // Only this
                cfw.startMethod("isGeneratorFunction", "()Z",
                                ACC_PROTECTED);
                break;
              case Do_getRealSource:
                methodLocals = 1; // Only this
                cfw.startMethod("getRealSource", "()Ljava/lang/String;",
                        ACC_PROTECTED);
                cfw.addPush(sourceString);
                break;
              case Do_getSourceStartLineNo:
                methodLocals = 1; // Only this
                cfw.startMethod("getSourceStartLineNo", "()I",
                        ACC_PROTECTED);
                break;
              default:
                throw Kit.codeBug();
            }

            int count = scriptOrFnNodes.length;

            int switchStart = 0;
            int switchStackTop = 0;
            if (count > 1) {
                // Generate switch but only if there is more then one
                // script/function
                cfw.addLoadThis();
                cfw.add(ByteCode.GETFIELD, cfw.getClassName(),
                        ID_FIELD_NAME, "I");

                // do switch from 1 .. count - 1 mapping 0 to the default case
                switchStart = cfw.addTableSwitch(1, count - 1);
            }

            for (int i = 0; i != count; ++i) {
                ScriptNode n = scriptOrFnNodes[i];
                if (i == 0) {
                    if (count > 1) {
                        cfw.markTableSwitchDefault(switchStart);
                        switchStackTop = cfw.getStackTop();
                    }
                } else {
                    cfw.markTableSwitchCase(switchStart, i - 1,
                                            switchStackTop);
                }

                // Impelemnet method-specific switch code
                switch (methodIndex) {
                  case Do_getFunctionName:
                    // Push function name
                    if (n.getType() == Token.SCRIPT) {
                        cfw.addPush("");
                    } else {
                        String name = ((FunctionNode)n).getName();
                        cfw.addPush(name);
                    }
                    cfw.add(ByteCode.ARETURN);
                    break;

                  case Do_getParamCount:
                    // Push number of defined parameters
                    cfw.addPush(n.getParamCount());
                    cfw.add(ByteCode.IRETURN);
                    break;

                  case Do_getParamAndVarCount:
                    // Push number of defined parameters and declared variables
                    cfw.addPush(n.getParamAndVarCount());
                    cfw.add(ByteCode.IRETURN);
                    break;

                  case Do_getParamOrVarName:
                    // Push name of parameter using another switch
                    // over paramAndVarCount
                    int paramAndVarCount = n.getParamAndVarCount();
                    if (paramAndVarCount == 0) {
                        // The runtime should never call the method in this
                        // case but to make bytecode verifier happy return null
                        // as throwing execption takes more code
                        cfw.add(ByteCode.ACONST_NULL);
                        cfw.add(ByteCode.ARETURN);
                    } else if (paramAndVarCount == 1) {
                        // As above do not check for valid index but always
                        // return the name of the first param
                        cfw.addPush(n.getParamOrVarName(0));
                        cfw.add(ByteCode.ARETURN);
                    } else {
                        // Do switch over getParamOrVarName
                        cfw.addILoad(1); // param or var index
                        // do switch from 1 .. paramAndVarCount - 1 mapping 0
                        // to the default case
                        int paramSwitchStart = cfw.addTableSwitch(
                                                   1, paramAndVarCount - 1);
                        for (int j = 0; j != paramAndVarCount; ++j) {
                            if (cfw.getStackTop() != 0) Kit.codeBug();
                            String s = n.getParamOrVarName(j);
                            if (j == 0) {
                                cfw.markTableSwitchDefault(paramSwitchStart);
                            } else {
                                cfw.markTableSwitchCase(paramSwitchStart, j - 1,
                                                        0);
                            }
                            cfw.addPush(s);
                            cfw.add(ByteCode.ARETURN);
                        }
                    }
                    break;

                    case Do_getParamOrVarConst:
                        // Push name of parameter using another switch
                        // over paramAndVarCount
                        paramAndVarCount = n.getParamAndVarCount();
                        boolean [] constness = n.getParamAndVarConst();
                        if (paramAndVarCount == 0) {
                            // The runtime should never call the method in this
                            // case but to make bytecode verifier happy return null
                            // as throwing execption takes more code
                            cfw.add(ByteCode.ICONST_0);
                            cfw.add(ByteCode.IRETURN);
                        } else if (paramAndVarCount == 1) {
                            // As above do not check for valid index but always
                            // return the name of the first param
                            cfw.addPush(constness[0]);
                            cfw.add(ByteCode.IRETURN);
                        } else {
                            // Do switch over getParamOrVarName
                            cfw.addILoad(1); // param or var index
                            // do switch from 1 .. paramAndVarCount - 1 mapping 0
                            // to the default case
                            int paramSwitchStart = cfw.addTableSwitch(
                                                       1, paramAndVarCount - 1);
                            for (int j = 0; j != paramAndVarCount; ++j) {
                                if (cfw.getStackTop() != 0) Kit.codeBug();
                                if (j == 0) {
                                    cfw.markTableSwitchDefault(paramSwitchStart);
                                } else {
                                    cfw.markTableSwitchCase(paramSwitchStart, j - 1,
                                                            0);
                                }
                                cfw.addPush(constness[j]);
                                cfw.add(ByteCode.IRETURN);
                            }
                        }
                      break;

                    case Do_isGeneratorFunction:
                        // Push a boolean if it's a generator
                        if (n instanceof FunctionNode) {
                            cfw.addPush(((FunctionNode) n).isES6Generator());
                        } else {
                            cfw.addPush(false);
                        }
                        cfw.add(ByteCode.IRETURN);
                        break;

                  case Do_getEncodedSource:
                    // Push number encoded source start and end
                    // to prepare for encodedSource.substring(start, end)
                    cfw.addPush(n.getEncodedSourceStart());
                    cfw.addPush(n.getEncodedSourceEnd());
                    cfw.addInvoke(ByteCode.INVOKEVIRTUAL,
                                  "java/lang/String",
                                  "substring",
                                  "(II)Ljava/lang/String;");
                    cfw.add(ByteCode.ARETURN);
                    break;

                    case Do_getRealSource:
                        // Push number non-encoded source start and end
                        // to prepare for rawSource.substring(start, end)
                        cfw.addPush(n.getAbsolutePosition());
                        cfw.addPush(n.getAbsolutePosition() + n.getLength());
                        cfw.addInvoke(ByteCode.INVOKEVIRTUAL,
                                "java/lang/String",
                                "substring",
                                "(II)Ljava/lang/String;");
                        cfw.add(ByteCode.ARETURN);
                        break;

                    case Do_getSourceStartLineNo:
                        cfw.addPush(n.getLineno());
                        cfw.add(ByteCode.IRETURN);
                        break;

                  default:
                    throw Kit.codeBug();
                }
            }

            cfw.stopMethod(methodLocals);
        }
    }

    private void emitRegExpInit(ClassFileWriter cfw)
    {
        // precompile all regexp literals

        int totalRegCount = 0;
        for (int i = 0; i != scriptOrFnNodes.length; ++i) {
            totalRegCount += scriptOrFnNodes[i].getRegexpCount();
        }
        if (totalRegCount == 0) {
            return;
        }

        cfw.startMethod(REGEXP_INIT_METHOD_NAME, REGEXP_INIT_METHOD_SIGNATURE,
                (short)(ACC_STATIC | ACC_PRIVATE));
        cfw.addField("_reInitDone", "Z",
                     (short)(ACC_STATIC | ACC_PRIVATE | ACC_VOLATILE));
        cfw.add(ByteCode.GETSTATIC, mainClassName, "_reInitDone", "Z");
        int doInit = cfw.acquireLabel();
        cfw.add(ByteCode.IFEQ, doInit);
        cfw.add(ByteCode.RETURN);
        cfw.markLabel(doInit);

        // get regexp proxy and store it in local slot 1
        cfw.addALoad(0); // context
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      "com/anatawa12/sai/ScriptRuntime",
                      "checkRegExpProxy",
                      "(Lcom/anatawa12/sai/Context;"
                      +")Lcom/anatawa12/sai/RegExpProxy;");
        cfw.addAStore(1); // proxy

        // We could apply double-checked locking here but concurrency
        // shouldn't be a problem in practice
        for (int i = 0; i != scriptOrFnNodes.length; ++i) {
            ScriptNode n = scriptOrFnNodes[i];
            int regCount = n.getRegexpCount();
            for (int j = 0; j != regCount; ++j) {
                String reFieldName = getCompiledRegexpName(n, j);
                String reFieldType = "Ljava/lang/Object;";
                String reString = n.getRegexpString(j);
                String reFlags = n.getRegexpFlags(j);
                cfw.addField(reFieldName, reFieldType,
                             (short)(ACC_STATIC | ACC_PRIVATE));
                cfw.addALoad(1); // proxy
                cfw.addALoad(0); // context
                cfw.addPush(reString);
                if (reFlags == null) {
                    cfw.add(ByteCode.ACONST_NULL);
                } else {
                    cfw.addPush(reFlags);
                }
                cfw.addInvoke(ByteCode.INVOKEINTERFACE,
                              "com/anatawa12/sai/RegExpProxy",
                              "compileRegExp",
                              "(Lcom/anatawa12/sai/Context;"
                              +"Ljava/lang/String;Ljava/lang/String;"
                              +")Ljava/lang/Object;");
                cfw.add(ByteCode.PUTSTATIC, mainClassName,
                        reFieldName, reFieldType);
            }
        }

        cfw.addPush(1);
        cfw.add(ByteCode.PUTSTATIC, mainClassName, "_reInitDone", "Z");
        cfw.add(ByteCode.RETURN);
        cfw.stopMethod((short)2);
    }

    private void emitConstantDudeInitializers(ClassFileWriter cfw, String sourceFile)
    {
        int N = itsConstantListSize;
        if (N == 0 && sourceFile == null)
            return;

        cfw.startMethod("<clinit>", "()V", (short)(ACC_STATIC | ACC_FINAL));

        double[] array = itsConstantList;
        for (int i = 0; i != N; ++i) {
            double num = array[i];
            String constantName = "_k" + i;
            String constantType = getStaticConstantWrapperType(num);
            cfw.addField(constantName, constantType,
                        (short)(ACC_STATIC | ACC_PRIVATE));
            int inum = (int)num;
            if (inum == num) {
                cfw.addPush(inum);
                cfw.addInvoke(ByteCode.INVOKESTATIC, "java/lang/Integer",
                              "valueOf", "(I)Ljava/lang/Integer;");
            } else {
                cfw.addPush(num);
                addDoubleWrap(cfw);
            }
            cfw.add(ByteCode.PUTSTATIC, mainClassName,
                    constantName, constantType);
        }

        if (sourceFile != null) {
            FileNameMapping mappings = scriptOrFnNodes[0].getFileNameMapping();

            cfw.add(ByteCode.NEW, "com.anatawa12.sai.FileNameMapping");
            cfw.add(ByteCode.DUP);
            cfw.addLoadConstant(mappings.lastLine);
            cfw.addLoadConstant(mappings.mappings.size());
            cfw.add(ByteCode.ANEWARRAY, "com.anatawa12.sai.Parser$LineNoMapping");
            for (int i = 0; i < mappings.mappings.size(); i++) {
                Parser.LineNoMapping mapping = mappings.mappings.get(i);
                cfw.add(ByteCode.DUP);
                cfw.addLoadConstant(i);
                cfw.add(ByteCode.NEW, "com.anatawa12.sai.Parser$LineNoMapping");
                cfw.add(ByteCode.DUP);
                cfw.addLoadConstant(mapping.startLineNo);
                cfw.addLoadConstant(mapping.inTraceLineNo);
                cfw.addLoadConstant(mapping.fileName);
                cfw.addInvoke(ByteCode.INVOKESPECIAL, "com.anatawa12.sai.Parser$LineNoMapping",
                        "<init>", "(IILjava/lang/String;)V");
                cfw.add(ByteCode.AASTORE);
            }
            cfw.addInvoke(ByteCode.INVOKESTATIC, "java.util.Arrays",
                    "asList", "([Ljava/lang/Object;)Ljava/util/List;");

            cfw.addInvoke(ByteCode.INVOKESPECIAL, "com.anatawa12.sai.FileNameMapping",
                    "<init>", "(ILjava/util/List;)V");

            cfw.add(ByteCode.PUTSTATIC, mainClassName, MAPPING_FIELD_NAME, "Lcom/anatawa12/sai/FileNameMapping;");

            cfw.addLoadClassConstant(mainClassName);
            cfw.addLoadConstant(sourceFile);
            cfw.add(ByteCode.GETSTATIC, mainClassName, MAPPING_FIELD_NAME, "Lcom/anatawa12/sai/FileNameMapping;");
            cfw.addInvoke(ByteCode.INVOKESTATIC, "com.anatawa12.sai.StackTraceEditor",
                    "addMapping", "(Ljava/lang/Object;" +
                            "Ljava/lang/String;Lcom/anatawa12/sai/FileNameMapping;)V");
        }

        cfw.add(ByteCode.RETURN);
        cfw.stopMethod((short)0);
    }

    void pushNumberAsObject(ClassFileWriter cfw, double num)
    {
        if (num == 0.0) {
            if (1 / num > 0) {
                // +0.0
                cfw.add(ByteCode.GETSTATIC,
                        "com/anatawa12/sai/ScriptRuntime",
                        "zeroObj", "Ljava/lang/Double;");
            } else {
                cfw.addPush(num);
                addDoubleWrap(cfw);
            }

        } else if (num == 1.0) {
            cfw.add(ByteCode.GETSTATIC,
                    "com/anatawa12/sai/optimizer/OptRuntime",
                    "oneObj", "Ljava/lang/Double;");
            return;

        } else if (num == -1.0) {
            cfw.add(ByteCode.GETSTATIC,
                    "com/anatawa12/sai/optimizer/OptRuntime",
                    "minusOneObj", "Ljava/lang/Double;");

        } else if (Double.isNaN(num)) {
            cfw.add(ByteCode.GETSTATIC,
                    "com/anatawa12/sai/ScriptRuntime",
                    "NaNobj", "Ljava/lang/Double;");

        } else if (itsConstantListSize >= 2000) {
            // There appears to be a limit in the JVM on either the number
            // of static fields in a class or the size of the class
            // initializer. Either way, we can't have any more than 2000
            // statically init'd constants.
            cfw.addPush(num);
            addDoubleWrap(cfw);

        } else {
            int N = itsConstantListSize;
            int index = 0;
            if (N == 0) {
                itsConstantList = new double[64];
            } else {
                double[] array = itsConstantList;
                while (index != N && array[index] != num) {
                    ++index;
                }
                if (N == array.length) {
                    array = new double[N * 2];
                    System.arraycopy(itsConstantList, 0, array, 0, N);
                    itsConstantList = array;
                }
            }
            if (index == N) {
                itsConstantList[N] = num;
                itsConstantListSize = N + 1;
            }
            String constantName = "_k" + index;
            String constantType = getStaticConstantWrapperType(num);
            cfw.add(ByteCode.GETSTATIC, mainClassName,
                    constantName, constantType);
        }
    }

    private static void addDoubleWrap(ClassFileWriter cfw)
    {
        cfw.addInvoke(ByteCode.INVOKESTATIC,
                      "com/anatawa12/sai/optimizer/OptRuntime",
                      "wrapDouble", "(D)Ljava/lang/Double;");
    }

    private static String getStaticConstantWrapperType(double num)
    {
        int inum = (int)num;
        if (inum == num) {
            return "Ljava/lang/Integer;";
        }
        return "Ljava/lang/Double;";
    }
    static void pushUndefined(ClassFileWriter cfw)
    {
        cfw.add(ByteCode.GETSTATIC, "com/anatawa12/sai/Undefined",
                "instance", "Ljava/lang/Object;");
    }

    int getIndex(ScriptNode n)
    {
        return scriptOrFnIndexes.getExisting(n);
    }

    String getDirectCtorName(ScriptNode n)
    {
        return "_n" + getIndex(n);
    }

    String getBodyMethodName(ScriptNode n)
    {
        return "_c_" + cleanName(n) + "_" + getIndex(n);
    }

    /**
     * Gets a Java-compatible "informative" name for the the ScriptOrFnNode
     */
    String cleanName(final ScriptNode n)
    {
      String result = "";
      if (n instanceof FunctionNode) {
        Name name = ((FunctionNode) n).getFunctionName();
        if (name == null) {
          result = "anonymous";
        } else {
          result = name.getIdentifier();
        }
      } else {
        result = "script";
      }
      return result;
    }

    String getBodyMethodSignature(ScriptNode n)
    {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        sb.append(mainClassSignature);
        sb.append("Lcom/anatawa12/sai/Context;"
                  +"Lcom/anatawa12/sai/Scriptable;"
                  +"Lcom/anatawa12/sai/Scriptable;");
        if (n.getType() == Token.FUNCTION) {
            OptFunctionNode ofn = OptFunctionNode.get(n);
            if (ofn.isTargetOfDirectCall()) {
                int pCount = ofn.fnode.getParamCount();
                for (int i = 0; i != pCount; i++) {
                    sb.append("Ljava/lang/Object;D");
                }
            }
        }
        sb.append("[Ljava/lang/Object;)Ljava/lang/Object;");
        return sb.toString();
    }

    String getFunctionInitMethodName(OptFunctionNode ofn)
    {
        return "_i"+getIndex(ofn.fnode);
    }

    String getCompiledRegexpName(ScriptNode n, int regexpIndex)
    {
        return "_re"+getIndex(n)+"_"+regexpIndex;
    }

    static RuntimeException badTree()
    {
        throw new RuntimeException("Bad tree in codegen");
    }

     public void setMainMethodClass(String className)
     {
         mainMethodClass = className;
     }

     static final String DEFAULT_MAIN_METHOD_CLASS
        = "com.anatawa12.sai.optimizer.OptRuntime";

    private static final String SUPER_CLASS_NAME
        = "com.anatawa12.sai.NativeFunction";

    static final String ID_FIELD_NAME = "_id";

    static final String MAPPING_FIELD_NAME = "_mapping";

    static final String REGEXP_INIT_METHOD_NAME = "_reInit";
    static final String REGEXP_INIT_METHOD_SIGNATURE
        =  "(Lcom/anatawa12/sai/Context;)V";

    static final String FUNCTION_INIT_SIGNATURE
        =  "(Lcom/anatawa12/sai/Context;"
           +"Lcom/anatawa12/sai/Scriptable;"
           +")V";

   static final String FUNCTION_CONSTRUCTOR_SIGNATURE
        = "(Lcom/anatawa12/sai/Scriptable;"
          +"Lcom/anatawa12/sai/Context;I)V";

    private static final Object globalLock = new Object();
    private static int globalSerialClassCounter;

    private CompilerEnvirons compilerEnv;

    private ObjArray directCallTargets;
    ScriptNode[] scriptOrFnNodes;
    private ObjToIntMap scriptOrFnIndexes;

    private String mainMethodClass = DEFAULT_MAIN_METHOD_CLASS;

    String mainClassName;
    String mainClassSignature;

    private double[] itsConstantList;
    private int itsConstantListSize;

    public String sourceString;
}