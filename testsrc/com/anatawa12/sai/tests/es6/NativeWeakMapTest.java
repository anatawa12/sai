/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.anatawa12.sai.tests.es6;

import com.anatawa12.sai.Context;
import com.anatawa12.sai.drivers.LanguageVersion;
import com.anatawa12.sai.drivers.RhinoTest;
import com.anatawa12.sai.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/es6/weakmap.js")
@LanguageVersion(Context.VERSION_ES6)
public class NativeWeakMapTest extends ScriptTestsBase
{
}
