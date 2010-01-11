/*
 * Copyright (c) 2007-2010, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * --
 */

package com.sun.sgs.test.impl.sharedutil;

import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.util.List;
import java.util.Properties;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for the PropertiesWrapper class. */
@RunWith(FilteredNameRunner.class)
public class TestPropertiesWrapper extends Assert {
    private Properties props;
    private PropertiesWrapper wrapper;

    @Before
    public void setUp() {
	props = new Properties();
	wrapper = new PropertiesWrapper(props);
    }
    
    /* -- Tests -- */

    /* -- Test getIntProperty -- */

    @Test
    public void testGetIntNullArgs() {
	try {
	    wrapper.getIntProperty(null, 3);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testGetIntNumberFormatException() {
	props.setProperty("p", "");
	try {
	    wrapper.getIntProperty("p", 1);
	    fail("Expected NumberFormatException");
	} catch (NumberFormatException e) {
	    System.err.println(e);
	}
	props.setProperty("p", "hello");
	try {
	    wrapper.getIntProperty("p", 1);
	    fail("Expected NumberFormatException");
	} catch (NumberFormatException e) {
	    System.err.println(e);
	}
	props.setProperty("p", "12345678901234567");
	try {
	    wrapper.getIntProperty("p", 1);
	    fail("Expected NumberFormatException");
	} catch (NumberFormatException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testGetIntBoundsFailures() {
	/* min > max */
	try {
	    wrapper.getIntProperty("p", 99, 100, 99);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	/* min > default */
	try {
	    wrapper.getIntProperty("p", 99, 100, 200);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	/* default > max */
	try {
	    wrapper.getIntProperty("p", 100, 0, 99);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	/* result > max */
	props.setProperty("p", "100");
	try {
	    wrapper.getIntProperty("p", 0, 0, 99);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	/* min > result */
	props.setProperty("p", "-1");
	try {
	    wrapper.getIntProperty("p", 0, 0, 99);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testGetRequiredIntFailures() {
        try {
            wrapper.getRequiredIntProperty("p");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            System.err.println(e);
        }
        props.setProperty("p", "50");
        /* min > max */
        try {
            wrapper.getRequiredIntProperty("p", 100, 99);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            System.err.println(e);
        }
        /* result > max */
        props.setProperty("p", "100");
        try {
            wrapper.getRequiredIntProperty("p", 0, 99);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            System.err.println(e);
        }
        /* min > result */
        props.setProperty("p", "-1");
        try {
            wrapper.getRequiredIntProperty("p", 0, 99);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            System.err.println(e);
        }
    }

    @Test
    public void testGetIntSuccess() {
	assertEquals(10, wrapper.getIntProperty("p", 10));
	assertEquals(0, wrapper.getIntProperty("p", 0, 0, 100));
	assertEquals(100, wrapper.getIntProperty("p", 100, 0, 100));
	assertEquals(-100, wrapper.getIntProperty("p", -100, -100, -100));
	props.setProperty("p", "100");
	assertEquals(100, wrapper.getIntProperty("p", 10));
	assertEquals(100, wrapper.getIntProperty("p", 10, 0, 100));
	assertEquals(100, wrapper.getIntProperty("p", 199, 100, 200));
	assertEquals(100, wrapper.getIntProperty("p", 100, 100, 100));
        assertEquals(100, wrapper.getRequiredIntProperty("p"));
    }

    /* -- Test getLongProperty -- */

    @Test
    public void testGetLongNullArgs() {
	try {
	    wrapper.getLongProperty(null, 3);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testGetLongNumberFormatException() {
	props.setProperty("p", "");
	try {
	    wrapper.getLongProperty("p", 1);
	    fail("Expected NumberFormatException");
	} catch (NumberFormatException e) {
	    System.err.println(e);
	}
	props.setProperty("p", "hello");
	try {
	    wrapper.getLongProperty("p", 1);
	    fail("Expected NumberFormatException");
	} catch (NumberFormatException e) {
	    System.err.println(e);
	}
	props.setProperty("p", "123456789012345678901234567890");
	try {
	    wrapper.getLongProperty("p", 1);
	    fail("Expected NumberFormatException");
	} catch (NumberFormatException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testGetLongBoundsFailures() {
	/* min > max */
	try {
	    wrapper.getLongProperty("p", 99, 100, 99);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	/* min > default */
	try {
	    wrapper.getLongProperty("p", 99, 100, 200);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	/* default > max */
	try {
	    wrapper.getLongProperty("p", 100, 0, 99);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	/* result > max */
	props.setProperty("p", "100");
	try {
	    wrapper.getLongProperty("p", 0, 0, 99);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	/* min > result */
	props.setProperty("p", "-1");
	try {
	    wrapper.getLongProperty("p", 0, 0, 99);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testGetLongSuccess() {
	assertEquals(1234567890123456L,
		     wrapper.getLongProperty("p", 1234567890123456L));
	assertEquals(1234567890123456L,
		     wrapper.getLongProperty(
			 "p", 1234567890123456L, 0, 1234567890123456L));
	assertEquals(100L, wrapper.getLongProperty("p", 100, 0, 100));
	assertEquals(-100L, wrapper.getLongProperty("p", -100, -100, -100));
	props.setProperty("p", "2345678901234567");
	assertEquals(2345678901234567L, wrapper.getLongProperty("p", 10));
	assertEquals(2345678901234567L,
		     wrapper.getLongProperty("p", 10, 0, 2345678901234567L));
	props.setProperty("p", "100");
	assertEquals(100L, wrapper.getLongProperty("p", 199, 100, 200));
	assertEquals(100L, wrapper.getLongProperty("p", 100, 100, 100));
    }

    /* -- Test getEnumProperty -- */

    @Test
    public void testGetEnumNullArgs() {
	try {
	    wrapper.getEnumProperty(null, Fruit.class, Fruit.APPLE);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
	try {
	    wrapper.getEnumProperty("fruit", null, Fruit.APPLE);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
	try {
	    wrapper.getEnumProperty("fruit", Fruit.class, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testGetEnumUnknownValue() {
	props.setProperty("fruit", "");
	try {
	    wrapper.getEnumProperty("fruit", Fruit.class, Fruit.APPLE);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	props.setProperty("fruit", "null");
	try {
	    wrapper.getEnumProperty("fruit", Fruit.class, Fruit.APPLE);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	props.setProperty("fruit", "DOG");
	try {
	    wrapper.getEnumProperty("fruit", Fruit.class, Fruit.APPLE);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	props.setProperty("fruit", "apple");
	try {
	    wrapper.getEnumProperty("fruit", Fruit.class, Fruit.APPLE);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testGetEnumSuccess() {
	assertSame(Fruit.APPLE,
		   wrapper.getEnumProperty("fruit", Fruit.class, Fruit.APPLE));
	props.setProperty("fruit", "ORANGE");
	assertSame(Fruit.ORANGE,
		   wrapper.getEnumProperty("fruit", Fruit.class, Fruit.APPLE));
    }
    
    @Test(expected=NullPointerException.class)
    public void testGetListPropertyNullName() {
        wrapper.getListProperty(null, String.class, "");
    }
    
    @Test(expected=NullPointerException.class)
    public void testGetListPropertyNullType() {
        wrapper.getListProperty("values", null, "");
    }

    @Test
    public void testGetListPropertyNoProperty() {
        List<String> list = wrapper.getListProperty(
                "values", String.class, null);
        assertEquals(0, list.size());
    }

    @Test
    public void testGetListPropertySingleValue() {
        props.setProperty("values", "1");
        List<Integer> list = wrapper.getListProperty(
                "values", Integer.class, null);
        assertEquals(1, list.size());
        assertEquals(Integer.valueOf(1), list.get(0));
    }

    @Test
    public void testGetListPropertyMultipleValues() {
        props.setProperty("values", "1:2");
        List<Integer> list = wrapper.getListProperty(
                "values", Integer.class, null);
        assertEquals(2, list.size());
        assertEquals(Integer.valueOf(1), list.get(0));
        assertEquals(Integer.valueOf(2), list.get(1));
    }

    @Test
    public void testGetListPropertyDefaultValue() {
        props.setProperty("values", "1::2");
        List<Integer> list = wrapper.getListProperty(
                "values", Integer.class, 3);
        assertEquals(3, list.size());
        assertEquals(Integer.valueOf(1), list.get(0));
        assertEquals(Integer.valueOf(3), list.get(1));
        assertEquals(Integer.valueOf(2), list.get(2));
    }

    @Test
    public void testGetListPropertyNullDefaultValue() {
        props.setProperty("values", "1::2");
        List<Integer> list = wrapper.getListProperty(
                "values", Integer.class, null);
        assertEquals(3, list.size());
        assertEquals(Integer.valueOf(1), list.get(0));
        assertNull(list.get(1));
        assertEquals(Integer.valueOf(2), list.get(2));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetListPropertyInvalidValue() {
        props.setProperty("values", "1:2:invalid");
        wrapper.getListProperty("values", Integer.class, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetListNoStringConstructor() {
        props.setProperty("values", "1:2:3");
        wrapper.getListProperty("values", Object.class, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetListAbstractClass() {
        props.setProperty("values", "1:2:3");
        wrapper.getListProperty("values", AbstractClass.class, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetListPrivateConstructor() {
        props.setProperty("values", "1:2:3");
        wrapper.getListProperty("values", PrivateConstructor.class, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetListPrivateClass() {
        props.setProperty("values", "1:2:3");
        wrapper.getListProperty("values", PrivateClass.class, null);
    }

    @Test(expected=NullPointerException.class)
    public void testGetEnumListPropertyNullName() {
        wrapper.getEnumListProperty(null, Fruit.class, Fruit.APPLE);
    }

    @Test(expected=NullPointerException.class)
    public void testGetEnumListPropertyNullType() {
        wrapper.getEnumListProperty("values", null, Fruit.APPLE);
    }

    @Test
    public void testGetEnumListPropertyNoProperty() {
        List<Fruit> list = wrapper.getEnumListProperty(
                "values", Fruit.class, Fruit.APPLE);
        assertEquals(0, list.size());
    }

    @Test
    public void testGetEnumListPropertySingleValue() {
        props.setProperty("values", "APPLE");
        List<Fruit> list = wrapper.getEnumListProperty(
                "values", Fruit.class, Fruit.PEAR);
        assertEquals(1, list.size());
        assertEquals(Fruit.APPLE, list.get(0));
    }

    @Test
    public void testGetEnumListPropertyMultipleValues() {
        props.setProperty("values", "APPLE:ORANGE");
        List<Fruit> list = wrapper.getEnumListProperty(
                "values", Fruit.class, Fruit.PEAR);
        assertEquals(2, list.size());
        assertEquals(Fruit.APPLE, list.get(0));
        assertEquals(Fruit.ORANGE, list.get(1));
    }

    @Test
    public void testGetEnumListPropertyDefaultValue() {
        props.setProperty("values", "APPLE::ORANGE");
        List<Fruit> list = wrapper.getEnumListProperty(
                "values", Fruit.class, Fruit.PEAR);
        assertEquals(3, list.size());
        assertEquals(Fruit.APPLE, list.get(0));
        assertEquals(Fruit.PEAR, list.get(1));
        assertEquals(Fruit.ORANGE, list.get(2));
    }

    @Test
    public void testGetEnumListPropertyNullDefaultValue() {
        props.setProperty("values", "APPLE::ORANGE");
        List<Fruit> list = wrapper.getEnumListProperty(
                "values", Fruit.class, null);
        assertEquals(3, list.size());
        assertEquals(Fruit.APPLE, list.get(0));
        assertNull(list.get(1));
        assertEquals(Fruit.ORANGE, list.get(2));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetEnumListPropertyInvalidValue() {
        props.setProperty("values", "APPLE:ORANGE:invalid");
        wrapper.getEnumListProperty("values", Fruit.class, Fruit.PEAR);
    }

    @Test(expected=NullPointerException.class)
    public void testGetClassListPropertyNullName() {
        wrapper.getClassListProperty(null);
    }

    @Test
    public void testGetClassListPropertyNoProperty() {
        List<Class<?>> list = wrapper.getClassListProperty("values");
        assertEquals(0, list.size());
    }

    @Test
    public void testGetClassListPropertySingleValue() {
        props.setProperty(
                "values",
                "com.sun.sgs.test.impl.sharedutil.TestPropertiesWrapper$One");
        List<Class<?>> list = wrapper.getClassListProperty("values");
        assertEquals(1, list.size());
        assertEquals(One.class, list.get(0));
    }

    @Test
    public void testGetClassListPropertyMultipleValues() {
        props.setProperty(
                "values",
                "com.sun.sgs.test.impl.sharedutil.TestPropertiesWrapper$One:" +
                "com.sun.sgs.test.impl.sharedutil.TestPropertiesWrapper$Two");
        List<Class<?>> list = wrapper.getClassListProperty("values");
        assertEquals(2, list.size());
        assertEquals(One.class, list.get(0));
        assertEquals(Two.class, list.get(1));
    }

    @Test
    public void testGetClassListPropertyDefaultValue() {
        props.setProperty(
                "values",
                "com.sun.sgs.test.impl.sharedutil.TestPropertiesWrapper$One::" +
                "com.sun.sgs.test.impl.sharedutil.TestPropertiesWrapper$Two");
        List<Class<?>> list = wrapper.getClassListProperty("values");
        assertEquals(3, list.size());
        assertEquals(One.class, list.get(0));
        assertEquals(null, list.get(1));
        assertEquals(Two.class, list.get(2));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetClassListPropertyInvalidValue() {
         props.setProperty(
                "values",
                "com.sun.sgs.test.impl.sharedutil.TestPropertiesWrapper$One:" +
                "com.sun.sgs.test.impl.sharedutil.TestPropertiesWrapper$Two:" +
                "this.is.not.a.class");
        wrapper.getClassListProperty("values");
    }

    @Test
    public void testGetClassListPropertyPrivateClass() {
         props.setProperty(
                "values",
                "com.sun.sgs.test.impl.sharedutil.TestPropertiesWrapper$One:" +
                "com.sun.sgs.test.impl.sharedutil.TestPropertiesWrapper$Two:" +
                "com.sun.sgs.test.impl.sharedutil.TestPropertiesWrapper$" +
                "PrivateClass");
        List<Class<?>> list = wrapper.getClassListProperty("values");
        assertEquals(One.class, list.get(0));
        assertEquals(Two.class, list.get(1));
        assertEquals(PrivateClass.class, list.get(2));
    }


    /* -- Other classes and methods -- */

    enum Fruit { APPLE, ORANGE, PEAR; }

    public class One {}
    public class Two {}
    public class Three {}

    public abstract class AbstractClass {
        public AbstractClass(String s) {}
    }

    public class PrivateConstructor {
        private PrivateConstructor(String s) {}
    }

    private class PrivateClass {
        public PrivateClass(String s) {}
    }
}
