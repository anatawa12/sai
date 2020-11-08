package com.anatawa12.sai.tests;

import com.anatawa12.sai.drivers.RhinoTest;
import com.anatawa12.sai.drivers.ScriptTestsBase;

@RhinoTest(
    value = "testsrc/jstests/redefine-properties.js"
)
public class RedefinePropertyTest
    extends ScriptTestsBase
{
}