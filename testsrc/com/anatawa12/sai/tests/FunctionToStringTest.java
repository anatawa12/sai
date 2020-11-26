package com.anatawa12.sai.tests;

import com.anatawa12.sai.drivers.RhinoTest;
import com.anatawa12.sai.drivers.ScriptTestsBase;

@RhinoTest(
        value = "testsrc/jstests/function-tostring.js"
)
public class FunctionToStringTest
        extends ScriptTestsBase
{
}
