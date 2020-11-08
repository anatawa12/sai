/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.anatawa12.sai.tests;

import com.anatawa12.sai.Context;
import com.anatawa12.sai.ScriptableObject;

import junit.framework.TestCase;

public class NativeRegExpTest extends TestCase {

    public void testOpenBrace() {
        final String script = "/0{0/";
        Utils.runWithAllOptimizationLevels(_cx -> {
            final ScriptableObject scope = _cx.initStandardObjects();
            final Object result = _cx.evaluateString(scope, script, "test script", 0, null);
            assertEquals(script, Context.toString(result));
            return null;
        });
    }
}
