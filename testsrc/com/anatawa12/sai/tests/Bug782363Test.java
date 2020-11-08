/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.anatawa12.sai.tests;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.anatawa12.sai.CompilerEnvirons;
import com.anatawa12.sai.Context;
import com.anatawa12.sai.EvaluatorException;
import com.anatawa12.sai.IRFactory;
import com.anatawa12.sai.Parser;
import com.anatawa12.sai.ScriptableObject;
import com.anatawa12.sai.ast.AstRoot;
import com.anatawa12.sai.ast.FunctionNode;
import com.anatawa12.sai.ast.ScriptNode;
import com.anatawa12.sai.optimizer.Codegen;
import com.anatawa12.sai.optimizer.OptFunctionNode;

/**
 * @author André Bargull
 *
 */
public class Bug782363Test {
    private Context cx;

    @Before
    public void setUp() {
        cx = Context.enter();
        cx.setLanguageVersion(Context.VERSION_1_8);
        cx.setOptimizationLevel(9);
    }

    @After
    public void tearDown() {
        Context.exit();
    }

    /**
     * Compiles {@code source} and returns the transformed and optimized
     * {@link ScriptNode}
     */
    protected ScriptNode compile(CharSequence source) {
        final String mainMethodClassName = "Main";
        final String scriptClassName = "Main";

        CompilerEnvirons compilerEnv = new CompilerEnvirons();
        compilerEnv.initFromContext(cx);
        Parser p = new Parser(compilerEnv);
        AstRoot ast = p.parse(source.toString(), "<eval>", 1);
        IRFactory irf = new IRFactory(compilerEnv);
        ScriptNode tree = irf.transformTree(ast);

        Codegen codegen = new Codegen();
        codegen.setMainMethodClass(mainMethodClassName);
        codegen.compileToClassFile(compilerEnv, scriptClassName, tree, tree.getEncodedSource(),
                false);

        return tree;
    }

    /**
     * Checks every variable {@code v} in {@code source} is marked as a
     * number-variable iff {@code numbers} contains {@code v}
     */
    protected void assertNumberVars(CharSequence source, String... numbers) {
        // wrap source in function
        ScriptNode tree = compile("function f(){" + source + "}");

        FunctionNode fnode = tree.getFunctionNode(0);
        assertNotNull(fnode);
        OptFunctionNode opt = OptFunctionNode.get(fnode);
        assertNotNull(opt);
        assertSame(fnode, opt.fnode);

        for (int i = 0, c = fnode.getParamCount(); i < c; ++i) {
            assertTrue(opt.isParameter(i));
            assertFalse(opt.isNumberVar(i));
        }

        Set<String> set = new HashSet<String>(asList(numbers));
        for (int i = fnode.getParamCount(), c = fnode.getParamAndVarCount(); i < c; ++i) {
            assertFalse(opt.isParameter(i));
            String name = fnode.getParamOrVarName(i);
            String msg = format("{%s -> number? = %b}", name, opt.isNumberVar(i));
            assertEquals(msg, set.contains(name), opt.isNumberVar(i));
        }
    }

    @Test
    public void testConst() {
        assertNumberVars("const a");
        assertNumberVars("const a=0", "a");
        assertNumberVars("const a; a=0");
        // inc/dec
        assertNumberVars("const a; a++");
        assertNumberVars("const a=0; a++", "a");
        assertNumberVars("const a; a=0; a++");
        // used before defined
        assertNumberVars("a; const a");
        assertNumberVars("a; const a=0");
        assertNumberVars("a; const a; a=0");
        // re-assignment
        assertNumberVars("const a=0; a=1", "a");
        assertNumberVars("const a=0; a='z'", "a");
        assertNumberVars("const a='z'; a=1");
    }

    @Test
    public void testMaxLocals() throws IOException {
        test(339);
        try {
            test(340);
        } catch (EvaluatorException e) {
            // may fail with 'out of locals' exception
        }
    }

    private void test(int variables) {
        double expected = (variables * (variables - 1)) / 2d;

        StringBuilder sb = new StringBuilder();
        sb.append("function F (){\n");
        for (int i = 0; i < variables; ++i) {
            sb.append("const x_").append(i).append("=").append(i).append(";");
        }
        sb.append("return 0");
        for (int i = 0; i < variables; ++i) {
            sb.append("+").append("x_").append(i);
        }
        sb.append("}; F()");

        ScriptableObject scope = cx.initStandardObjects();
        Object ret = cx.evaluateString(scope, sb.toString(), "<eval>", 1, null);
        assertTrue(ret instanceof Number);
        assertEquals(expected, ((Number) ret).doubleValue(), 0);
    }
}
