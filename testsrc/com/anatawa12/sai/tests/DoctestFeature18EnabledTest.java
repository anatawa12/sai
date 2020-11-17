package com.anatawa12.sai.tests;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import com.anatawa12.sai.Context;
import com.anatawa12.sai.ContextFactory;
import com.anatawa12.sai.tools.shell.Global;

@RunWith(Parameterized.class)
public class DoctestFeature18EnabledTest extends DoctestsTest {
    public DoctestFeature18EnabledTest(String name, String source, int optimizationLevel) {
        super(name, source, optimizationLevel);
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> singleDoctest() throws IOException {
        List<Object[]> result = new ArrayList<Object[]>();
        File f = new File(DoctestsTest.baseDirectory, "feature18enabled.doctest");
        String contents = DoctestsTest.loadFile(f);
        result.add(new Object[]{f.getName(), contents, -1});
        return result;
    }

    @Test
    public void runDoctest() {
        ContextFactory contextFactory = new ContextFactory();
        Context context = contextFactory.enterContext();
        try {
            context.setOptimizationLevel(optimizationLevel);
            Global global = new Global(context);
            int testsPassed = global.runDoctest(context, global, source, name, 1);
            assertTrue(testsPassed > 0);
        } finally {
            Context.exit();
        }
    }
}
