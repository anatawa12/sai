package com.anatawa12.sai.tests.backwardcompat;

import com.anatawa12.sai.Context;
import com.anatawa12.sai.drivers.LanguageVersion;
import com.anatawa12.sai.drivers.RhinoTest;

@RhinoTest("testsrc/jstests/backwardcompat/backward-proto-property.js")
@LanguageVersion(Context.VERSION_1_8)
public class BackwardProtoPropertyTest {}
