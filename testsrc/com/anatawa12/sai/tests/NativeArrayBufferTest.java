/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.anatawa12.sai.tests;

import org.junit.Assert;
import org.junit.Test;
import com.anatawa12.sai.Context;
import com.anatawa12.sai.Scriptable;
import com.anatawa12.sai.Undefined;

public class NativeArrayBufferTest {

    /**
     * Test case for issue {@link https://github.com/mozilla/rhino/issues/437}
     *
     * @throws Exception in case of failure
     */
    @Test
    public void test() throws Exception {
        Context cx = Context.enter();
        cx.setLanguageVersion(Context.VERSION_ES6);
        Scriptable global = cx.initStandardObjects();
        Object result = cx.evaluateString(global, "(new ArrayBuffer(5)).isView", "", 1, null);
        Assert.assertEquals(Undefined.instance, result);
        Context.exit();
    }
}
