/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.anatawa12.sai.tests;

import org.junit.Assert;
import org.junit.Test;
import com.anatawa12.sai.ContextFactory;
import com.anatawa12.sai.Function;

public class Bug496585Test {
    public String method(String one, Function function) {
        return "string+function";
    }

    public String method(String... strings) {
        return "string[]";
    }

    @Test
    public void callOverloadedFunction() {
        new ContextFactory().call(cx -> {
            cx.getWrapFactory().setJavaPrimitiveWrap(false);
            Assert.assertEquals("string[]", cx.evaluateString(
                    cx.initStandardObjects(),
                    "new com.anatawa12.sai.tests.Bug496585Test().method('one', 'two', 'three')",
                    "<test>", 1, null));
            Assert.assertEquals("string+function", cx.evaluateString(
                cx.initStandardObjects(),
                "new com.anatawa12.sai.tests.Bug496585Test().method('one', function() {})",
                "<test>", 1, null));
            return null;
        });
    }
}
