/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.test.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.impl.hook.*;
import com.sun.sgs.impl.service.data.*;
import org.junit.*;

import java.io.*;

public class TestSerializationHook extends Assert {

    @After
    public void resetHooks() {
        HookLocator.setSerializationHook(null);
    }

    @Test
    public void the_top_level_object_is_provided_during_serialization() {
        final MyObject objectBeingSerialized = new MyObject("foo");

        HookLocator.setSerializationHook(new NullSerializationHook() {
            public Object replaceObject(Object topLevelObject, Object object) {
                assertSame(objectBeingSerialized, topLevelObject);
                return object;
            }
        });

        SerialUtil.serialize(objectBeingSerialized, new SimpleClassSerialization());
    }

    @Test
    public void objects_can_be_replaced_during_serialization() {
        HookLocator.setSerializationHook(new NullSerializationHook() {
            public Object replaceObject(Object topLevelObject, Object object) {
                if (object.equals("foo")) {
                    return "bar";
                }
                return object;
            }
        });

        MyObject original = new MyObject("foo");
        byte[] data = SerialUtil.serialize(original, new SimpleClassSerialization());
        MyObject result = (MyObject) SerialUtil.deserialize(data, new SimpleClassSerialization());
        assertEquals("bar", result.getName());
    }

    @Test
    public void objects_can_be_replaced_during_deserialization() {
        HookLocator.setSerializationHook(new NullSerializationHook() {
            public Object resolveObject(Object object) {
                if (object.equals("foo")) {
                    return "bar";
                }
                return object;
            }
        });

        MyObject original = new MyObject("foo");
        byte[] data = SerialUtil.serialize(original, new SimpleClassSerialization());
        MyObject result = (MyObject) SerialUtil.deserialize(data, new SimpleClassSerialization());
        assertEquals("bar", result.getName());
    }


    private static class MyObject implements ManagedObject, Serializable {

        private final String name;

        public MyObject(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    private static class SimpleClassSerialization implements ClassSerialization {
        public void writeClassDescriptor(ObjectStreamClass classDesc, ObjectOutputStream out) throws IOException {
            out.writeUTF(classDesc.getName());
        }

        public void checkInstantiable(ObjectStreamClass classDesc) {
        }

        public ObjectStreamClass readClassDescriptor(ObjectInputStream in) throws ClassNotFoundException, IOException {
            String name = in.readUTF();
            return ObjectStreamClass.lookup(Class.forName(name));
        }
    }
}
