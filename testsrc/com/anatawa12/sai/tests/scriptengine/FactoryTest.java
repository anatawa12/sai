package com.anatawa12.sai.tests.scriptengine;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import org.junit.Test;
import com.anatawa12.sai.engine.SaiScriptEngine;
import com.anatawa12.sai.engine.SaiScriptEngineFactory;

import static org.junit.Assert.*;

/*
 * A series of tests that depend on us having our engine registered with the
 * ScriptEngineManager by default.
 */
public class FactoryTest {

  @Test
  public void findSaiFactory() {
    ScriptEngineManager manager = new ScriptEngineManager();
    for (ScriptEngineFactory factory : manager.getEngineFactories()) {
      if (factory instanceof SaiScriptEngineFactory) {
        assertEquals("sai", factory.getEngineName());
        assertEquals("sai", factory.getParameter(ScriptEngine.ENGINE));
        assertEquals("sai", factory.getParameter(ScriptEngine.NAME));
        // This could be "unknown" if we're not running from a regular JAR
        assertFalse(factory.getEngineVersion().isEmpty());
        assertEquals("javascript", factory.getLanguageName());
        assertEquals("javascript", factory.getParameter(ScriptEngine.LANGUAGE));
        assertEquals("200", factory.getLanguageVersion());
        assertEquals("200", factory.getParameter(ScriptEngine.LANGUAGE_VERSION));
        assertNull(factory.getParameter("THREADING"));
        assertTrue(factory.getExtensions().contains("js"));
        assertTrue(factory.getMimeTypes().contains("application/javascript"));
        assertTrue(factory.getMimeTypes().contains("application/ecmascript"));
        assertTrue(factory.getMimeTypes().contains("text/javascript"));
        assertTrue(factory.getMimeTypes().contains("text/ecmascript"));
        assertTrue(factory.getNames().contains("sai"));
        assertTrue(factory.getNames().contains("sai"));
        assertTrue(factory.getNames().contains("javascript"));
        assertTrue(factory.getNames().contains("JavaScript"));
        return;
      }
    }
    fail("Expected to find Sai script engine");
  }

  @Test
  public void testSaiFactory() {
    // This will always uniquely return our engine.
    // In Java 8, other ways to find it may return Nashorn.
    ScriptEngine engine = new ScriptEngineManager().getEngineByName("sai");
    assertTrue(engine instanceof SaiScriptEngine);

  }
}
