/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.anatawa12.sai.tests;

import java.io.InputStreamReader;

import com.anatawa12.sai.Context;
import com.anatawa12.sai.Script;
import com.anatawa12.sai.ScriptRuntime;
import com.anatawa12.sai.Scriptable;

import junit.framework.TestCase;

public class Issue176Test extends TestCase {


    Context cx;
    Scriptable scope;

    public void testThrowing() throws Exception {
        cx = Context.enter();
        try {
            Script script = cx.compileReader(new InputStreamReader(
                    Bug482203Test.class.getResourceAsStream("Issue176.js")),
                    "Issue176.js", 1, null);
            scope = cx.initStandardObjects();
            scope.put("host", scope, this);
            script.exec(cx, scope); // calls our methods
        } finally {
            Context.exit();
        }
    }


    public void throwError(String msg) {
        throw ScriptRuntime.throwError(cx, scope, msg);
    }


    public void throwCustomError(String constr, String msg) {
        throw ScriptRuntime.throwCustomError(cx, scope, constr, msg);
    }

}
