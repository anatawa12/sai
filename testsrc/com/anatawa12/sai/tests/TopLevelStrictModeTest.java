package com.anatawa12.sai.tests;

import com.anatawa12.sai.drivers.RhinoTest;
import com.anatawa12.sai.drivers.ScriptTestsBase;

@RhinoTest(
    value = "testsrc/jstests/top-level-strict-mode.js"
)
public class TopLevelStrictModeTest
    extends ScriptTestsBase
{
}
