/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.anatawa12.sai.tests;

import com.anatawa12.sai.Context;

import com.anatawa12.sai.drivers.LanguageVersion;
import com.anatawa12.sai.drivers.RhinoTest;
import com.anatawa12.sai.drivers.ScriptTestsBase;

/**
 * Test for overloaded array concat with non-dense arg.
 * See https://bugzilla.mozilla.org/show_bug.cgi?id=477604
 * @author Marc Guillemot
 */
@RhinoTest("testsrc/jstests/array-concat-pre-es6.js")
@LanguageVersion(Context.VERSION_1_8)
public class ArrayConcatTest extends ScriptTestsBase {
    // Original test case code moved to the JS file above.
}
