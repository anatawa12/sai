package com.anatawa12.sai.tests;

import com.anatawa12.sai.drivers.RhinoTest;
import com.anatawa12.sai.drivers.ScriptTestsBase;

@RhinoTest(
    value = "testsrc/jstests/inc-dec.js"
)
public class IncDecTest
    extends ScriptTestsBase
{
}
