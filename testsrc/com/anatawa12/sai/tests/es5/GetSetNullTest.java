package com.anatawa12.sai.tests.es5;

import com.anatawa12.sai.Context;
import com.anatawa12.sai.drivers.LanguageVersion;
import com.anatawa12.sai.drivers.RhinoTest;
import com.anatawa12.sai.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/extensions/getset-null.js")
@LanguageVersion(Context.VERSION_1_8)
public class GetSetNullTest extends ScriptTestsBase
{
}
