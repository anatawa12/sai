/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package com.anatawa12.sai.tests.es6;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.anatawa12.sai.Context;
import com.anatawa12.sai.NativeArrayIterator;
import com.anatawa12.sai.NativeArrayIterator.ARRAY_ITERATOR_TYPE;
import com.anatawa12.sai.Scriptable;
import com.anatawa12.sai.tools.shell.Global;

public class NativeArrayIteratorTest
{
    private Context cx;
    private Scriptable root;

    @Before
    public void init()
    {
        cx = Context.enter();
        cx.setLanguageVersion(Context.VERSION_ES6);
        cx.setGeneratingDebug(true);

        Global global = new Global(cx);
        root = cx.newObject(global);
    }

    @After
    public void terminate()
    {
        Context.exit();
    }

    /**
     * Test serialization of an empty object.
     */
    @Test
    public void testSerialization()
        throws IOException, ClassNotFoundException {

        NativeArrayIterator iter = new NativeArrayIterator(root, null, ARRAY_ITERATOR_TYPE.VALUES);

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oout = new ObjectOutputStream(bos)) {
            oout.writeObject(iter);

            try (ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
                    ObjectInputStream oin = new ObjectInputStream(bis)) {
                NativeArrayIterator result = (NativeArrayIterator)oin.readObject();
                assertEquals(0, result.getIds().length);
            }
        }
    }
}
