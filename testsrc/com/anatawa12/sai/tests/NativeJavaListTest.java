/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.anatawa12.sai.tests;

import junit.framework.TestCase;
import com.anatawa12.sai.Context;
import com.anatawa12.sai.ContextFactory;
import com.anatawa12.sai.NativeArray;
import com.anatawa12.sai.Scriptable;
import com.anatawa12.sai.tools.shell.Global;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * From @makusuko (Markus Sunela), imported from PR https://github.com/mozilla/rhino/pull/561
 */
public class NativeJavaListTest extends TestCase {
    protected final Global global = new Global();

    public NativeJavaListTest() {
        global.init(ContextFactory.getGlobal());
    }


    public void testAccessingJavaListIntegerValues() {
        List<Integer> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        assertEquals(2, runScriptAsInt("value[1]", list));
        assertEquals(3, runScriptAsInt("value[2]", list));
        assertEquals(3, runScriptAsInt("value.length", list));
    }

    public void testLengthProperty() {
        List<Integer> list = new ArrayList<>();
        assertEquals(0, runScriptAsInt("value.length", list));
        list.add(1);
        list.add(2);
        list.add(3);
        assertEquals(3, runScriptAsInt("value.length", list));
    }

    public void testJavaMethodsCalls() {
        List<Integer> list = new ArrayList<>();
        assertEquals(0, runScriptAsInt("value.size()", list));
        list.add(1);
        list.add(2);
        list.add(3);
        assertEquals(3, runScriptAsInt("value.size()", list));
    }

    public void testUpdatingJavaListIntegerValues() {
        List<Number> list = new ArrayList<>();
        list.add(1);
        list.add(2);
        list.add(3);

        assertEquals(2, runScriptAsInt("value[1]", list));
        assertEquals(5, runScriptAsInt("value[1]=5;value[1]", list));
        assertEquals(5, list.get(1).intValue());
    }

    public void testAccessingJavaListStringValues() {
        List<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        list.add("c");

        assertEquals("b", runScriptAsString("value[1]", list));
        assertEquals("c", runScriptAsString("value[2]", list));
    }

    public void testUpdatingJavaListStringValues() {
        List<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        list.add("c");

        assertEquals("b", runScriptAsString("value[1]", list));
        assertEquals("f", runScriptAsString("value[1]=\"f\";value[1]", list));
        assertEquals("f", list.get(1));
    }

    public void testKeys() {
        List<String> list = new ArrayList<>();
        NativeArray resEmpty = (NativeArray) runScript("Object.keys(value)", list, Function.identity());
        assertEquals(0, resEmpty.size());

        list.add("a");
        list.add("b");
        list.add("c");

        NativeArray res = (NativeArray) runScript("Object.keys(value)", list, Function.identity());
        assertEquals(3, res.size());
        assertTrue(res.contains("0"));
        assertTrue(res.contains("1"));
        assertTrue(res.contains("2"));
    }

    private int runScriptAsInt(String scriptSourceText, Object value) {
        return runScript(scriptSourceText, value, Context::toNumber).intValue();
    }

    private String runScriptAsString(String scriptSourceText, Object value) {
        return runScript(scriptSourceText, value, Context::toString);
    }

    private <T> T runScript(String scriptSourceText, Object value, Function<Object, T> convert) {
        return ContextFactory.getGlobal().call(context -> {
            Scriptable scope = context.initStandardObjects(global);
            scope.put("value", scope, Context.javaToJS(value, scope));
            return convert.apply(context.evaluateString(scope, scriptSourceText, "", 1, null));
        });
    }
}