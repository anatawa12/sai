package com.anatawa12.sai.tests.es5;

import static org.junit.Assert.assertEquals;
import static com.anatawa12.sai.tests.Evaluator.eval;

import org.junit.Test;
import com.anatawa12.sai.NativeObject;

/**
 * Test for the String.prototype.substr() functions.
 */
public class StringSubstrTest {

  @Test
  public void testLengthUndefined() {
    NativeObject object = new NativeObject();

    Object result = eval("'1234'.substr(1, undefined);", "obj", object);
    assertEquals("234", result.toString());
  }

  /**
   * Some samples from https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/String/substr.
   */
  @Test
  public void testVarious() {
    NativeObject object = new NativeObject();

    assertEquals("M", eval("var aString = 'Mozilla'; aString.substr(0, 1);", "obj", object));
    assertEquals("", eval("var aString = 'Mozilla'; aString.substr(1, 0);", "obj", object));
    assertEquals("a", eval("var aString = 'Mozilla'; aString.substr(-1, 1);", "obj", object));
    assertEquals("", eval("var aString = 'Mozilla'; aString.substr(1, -1);", "obj", object));
    assertEquals("lla", eval("var aString = 'Mozilla'; aString.substr(-3);", "obj", object));
    assertEquals("ozilla", eval("var aString = 'Mozilla'; aString.substr(1);", "obj", object));
    assertEquals("Mo", eval("var aString = 'Mozilla'; aString.substr(-20, 2);", "obj", object));
    assertEquals("", eval("var aString = 'Mozilla'; aString.substr(20, 2);", "obj", object));
  }
}
