package com.anatawa12.sai.tests.harmony;

import com.anatawa12.sai.drivers.RhinoTest;
import com.anatawa12.sai.drivers.ScriptTestsBase;

@RhinoTest(
    value = "testsrc/jstests/harmony/math-functions.js"
)
public class MathFunctionsTest
    extends ScriptTestsBase
{
}
