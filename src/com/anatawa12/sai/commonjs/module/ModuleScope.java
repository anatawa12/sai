/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.anatawa12.sai.commonjs.module;

import java.net.URI;

import com.anatawa12.sai.Scriptable;
import com.anatawa12.sai.TopLevel;

/**
 * A top-level module scope. This class provides methods to retrieve the
 * module's source and base URIs in order to resolve relative module IDs
 * and check sandbox constraints.
 */
public class ModuleScope extends TopLevel {
    private static final long serialVersionUID = 1L;
    private final URI uri;
    private final URI base;

    public ModuleScope(Scriptable prototype, URI uri, URI base) {
        this.uri = uri;
        this.base = base;
        setPrototype(prototype);
        cacheBuiltins(prototype, false);
    }

    public URI getUri() {
        return uri;
    }

    public URI getBase() {
        return base;
    }
}
