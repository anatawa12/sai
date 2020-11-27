package com.anatawa12.sai.tests;

import com.anatawa12.sai.Context;
import com.anatawa12.sai.ContextFactory;
import com.anatawa12.sai.drivers.RhinoTest;
import com.anatawa12.sai.drivers.ScriptTestsBase;
import com.anatawa12.sai.drivers.TestUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;

@RhinoTest(
        value = "testsrc/jstests/function-tostring.js"
)
public class FunctionToStringTest
        extends ScriptTestsBase
{
    @BeforeClass
    public static void setupContext() {
        TestUtils.setGlobalContextFactory(new MyFactory());
    }

    @AfterClass
    public static void resetContext() {
        TestUtils.setGlobalContextFactory(null);
    }

    private static class MyFactory extends ContextFactory {
        @Override
        protected boolean hasFeature(Context cx, int featureIndex) {
            if (featureIndex == Context.FEATURE_FUNCTION_TO_STRING_RETURN_REAL_SOURCE)
                return true;
            return super.hasFeature(cx, featureIndex);
        }
    }
}
