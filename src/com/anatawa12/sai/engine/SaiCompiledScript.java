/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.anatawa12.sai.engine;

import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import com.anatawa12.sai.Script;

public class SaiCompiledScript
  extends CompiledScript {

  private final SaiScriptEngine engine;
  private final Script script;

  SaiCompiledScript(SaiScriptEngine engine, Script script) {
    this.engine = engine;
    this.script = script;
  }

  @Override
  public Object eval(ScriptContext context) throws ScriptException {
    return engine.eval(script, context);
  }

  @Override
  public ScriptEngine getEngine() {
    return engine;
  }
}
