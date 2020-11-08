package com.anatawa12.sai.tests;

import com.anatawa12.sai.Context;
import com.anatawa12.sai.drivers.LanguageVersion;
import com.anatawa12.sai.drivers.RhinoTest;
import com.anatawa12.sai.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/extensions/obsolete-generators.js")
@LanguageVersion(Context.VERSION_1_8)
public class ObsoleteGeneratorTest extends ScriptTestsBase {
}
