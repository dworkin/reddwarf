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
import com.sun.sgs.impl.service.data.ClassSerialization;
import com.sun.sgs.impl.service.data.NullSerializationHook;
import com.sun.sgs.impl.service.data.SerialUtil;
import com.sun.sgs.service.data.SerializationHook;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(FilteredNameRunner.class)
public class TestSerializationHook extends Assert {

    private SerialUtil serialUtil;

    private void useSerializationHook(SerializationHook hook) {
        serialUtil = new SerialUtil(new SimpleClassSerialization(), hook);
    }

    @Test
    public void all_objects_in_the_object_graph_are_passed_to_the_hook_during_serialization() {
        final List<Object> all = new ArrayList<Object>();

        useSerializationHook(new NullSerializationHook() {
            public Object replaceObject(Object topLevelObject, Object object) {
                all.add(object);
                return object;
            }
        });

        MyObject original = new MyObject("foo");
        serialUtil.serialize(original);

        assertTrue(all.contains(original));
        assertTrue(all.contains("foo"));
        assertEquals(2, all.size());
    }

    @Test
    public void all_objects_in_the_object_graph_are_passed_to_the_hook_during_deserialization() {
        final List<Object> all = new ArrayList<Object>();

        useSerializationHook(new NullSerializationHook() {
            public Object resolveObject(Object object) {
                all.add(object);
                return object;
            }
        });

        MyObject original = new MyObject("foo");
        byte[] data = serialUtil.serialize(original);
        MyObject result = (MyObject) serialUtil.deserialize(data);

        assertTrue(all.contains(result));
        assertTrue(all.contains("foo"));
        assertEquals(2, all.size());
    }

    @Test
    public void objects_can_be_replaced_during_serialization() {
        useSerializationHook(new NullSerializationHook() {
            public Object replaceObject(Object topLevelObject, Object object) {
                if (object.equals("foo")) {
                    return "bar";
                }
                return object;
            }
        });

        MyObject original = new MyObject("foo");
        byte[] data = serialUtil.serialize(original);
        MyObject result = (MyObject) serialUtil.deserialize(data);

        assertEquals("bar", result.getName());
    }

    @Test
    public void objects_can_be_replaced_during_deserialization() {
        useSerializationHook(new NullSerializationHook() {
            public Object resolveObject(Object object) {
                if (object.equals("foo")) {
                    return "bar";
                }
                return object;
            }
        });

        MyObject original = new MyObject("foo");
        byte[] data = serialUtil.serialize(original);
        MyObject result = (MyObject) serialUtil.deserialize(data);

        assertEquals("bar", result.getName());
    }

    @Test
    public void the_top_level_object_is_provided_during_serialization() {
        final MyObject objectBeingSerialized = new MyObject("foo");

        useSerializationHook(new NullSerializationHook() {
            public Object replaceObject(Object topLevelObject, Object object) {
                assertSame(objectBeingSerialized, topLevelObject);
                return object;
            }
        });

        serialUtil.serialize(objectBeingSerialized);
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

        public ObjectStreamClass readClassDescriptor(ObjectInputStream in)
                throws ClassNotFoundException, IOException {
            String name = in.readUTF();
            return ObjectStreamClass.lookup(Class.forName(name));
        }
    }
}
