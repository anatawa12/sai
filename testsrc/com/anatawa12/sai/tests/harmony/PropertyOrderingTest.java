package com.anatawa12.sai.tests.harmony;

import com.anatawa12.sai.Context;
import com.anatawa12.sai.drivers.LanguageVersion;
import com.anatawa12.sai.drivers.RhinoTest;
import com.anatawa12.sai.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/harmony/property-ordering.js")
@LanguageVersion(Context.VERSION_ES6)
public class PropertyOrderingTest
    extends ScriptTestsBase
{
}
