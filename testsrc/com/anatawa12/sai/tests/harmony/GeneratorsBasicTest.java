/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

 package com.anatawa12.sai.tests.harmony;

 import com.anatawa12.sai.Context;
 import com.anatawa12.sai.drivers.LanguageVersion;
 import com.anatawa12.sai.drivers.RhinoTest;
 import com.anatawa12.sai.drivers.ScriptTestsBase;
 
 @RhinoTest("testsrc/jstests/harmony/generators-basic.js")
 @LanguageVersion(Context.VERSION_ES6)
 public class GeneratorsBasicTest extends ScriptTestsBase
 {
 }
 