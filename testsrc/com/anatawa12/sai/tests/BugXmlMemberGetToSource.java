/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package com.anatawa12.sai.tests;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;
import com.anatawa12.sai.CompilerEnvirons;
import com.anatawa12.sai.Parser;
import com.anatawa12.sai.ast.AstRoot;
import com.anatawa12.sai.ast.XmlMemberGet;

/**
 * Test resembles issue #483 with {@link XmlMemberGet#toSource()} implementation.
 * {@code toSource()} implementation calls {@link com.anatawa12.sai.ast.AstNode#operatorToString(int)}
 * passing in node's type, which is {@link com.anatawa12.sai.Token#DOT} or
 * {@link com.anatawa12.sai.Token#DOTDOT} by documentation. This causes {@link IllegalArgumentException}, as
 * {@code DOT} and {@code DOTDOT} are not treated as operators in {@code AstNode}.
 */
public class BugXmlMemberGetToSource {
    private CompilerEnvirons environment;

    @Before
    public void setUp() {
        environment = new CompilerEnvirons();
    }

    @Test
    public void testXmlMemberGetToSourceDotAt() {
        String script = "a.@b;";
        Parser parser = new Parser(environment);
        AstRoot root = parser.parse(script, null, 1);
        /* up to 1.7.9 following will throw IllegalArgumentException */
        assertEquals("a.@b;", root.toSource().trim());
    }

    @Test
    public void testXmlMemberGetToSourceDotDot() {
        String script = "a..b;";
        Parser parser = new Parser(environment);
        AstRoot root = parser.parse(script, null, 1);
        /* up to 1.7.9 following will throw IllegalArgumentException */
        assertEquals("a..b;", root.toSource().trim());
    }
}