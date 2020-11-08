package com.anatawa12.sai.tests.es6;

import com.anatawa12.sai.Context;
import com.anatawa12.sai.drivers.LanguageVersion;
import com.anatawa12.sai.drivers.RhinoTest;

@RhinoTest("testsrc/jstests/es6/proto-property.js")
@LanguageVersion(Context.VERSION_ES6)
public class ProtoPropertyTest {}
