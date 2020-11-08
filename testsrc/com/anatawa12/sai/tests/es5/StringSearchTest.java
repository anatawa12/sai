package com.anatawa12.sai.tests.es5;

import static org.junit.Assert.assertEquals;
import static com.anatawa12.sai.tests.Evaluator.eval;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.anatawa12.sai.Context;
import com.anatawa12.sai.NativeObject;

/**
 * @see <a href="https://github.com/mozilla/rhino/issues/651">https://github.com/mozilla/rhino/issues/651</a>
 */
public class StringSearchTest {
    private Context cx;

    @Before
    public void setUp() {
        cx = Context.enter();
        cx.setLanguageVersion(Context.VERSION_ES6);
        cx.initStandardObjects();
    }

    @After
    public void tearDown() {
        Context.exit();
    }

  @Test
  public void testSearch() {
    NativeObject object = new NativeObject();

    Object result = eval("String.prototype.search(1, 1)", "obj", object);
    assertEquals(-1, result);
  }
}
