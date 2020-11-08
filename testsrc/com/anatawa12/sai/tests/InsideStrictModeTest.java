package com.anatawa12.sai.tests;

import com.anatawa12.sai.drivers.RhinoTest;
import com.anatawa12.sai.drivers.ScriptTestsBase;

@RhinoTest(
    value = "testsrc/jstests/inside-strict-mode.js"
)
public class InsideStrictModeTest
    extends ScriptTestsBase
{
}
