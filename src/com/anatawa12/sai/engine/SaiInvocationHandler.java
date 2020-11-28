/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.anatawa12.sai.engine;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class SaiInvocationHandler
    implements InvocationHandler {

  private final Object thiz;
  private final SaiScriptEngine engine;

  SaiInvocationHandler(SaiScriptEngine engine, Object thiz) {
    this.engine = engine;
    this.thiz = thiz;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    return engine.invokeMethodRaw(thiz, method.getName(), method.getReturnType(), args);
  }
}
