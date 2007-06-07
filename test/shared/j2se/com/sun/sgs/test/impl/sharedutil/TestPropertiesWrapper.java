/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.sharedutil;

import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import java.util.Properties;
import junit.framework.TestCase;

public class TestPropertiesWrapper extends TestCase {
    private Properties props;
    private PropertiesWrapper wrapper;

    public TestPropertiesWrapper(String name) {
	super(name);
    }

    protected void setUp() {
	System.err.println("Testcase: " + getName());
	props = new Properties();
	wrapper = new PropertiesWrapper(props);
    }
    
    /* -- Tests -- */

    /* -- Test getIntProperty -- */

    public void testGetIntNullArgs() {
	try {
	    wrapper.getIntProperty(null, 3);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

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

    public void testGetIntBoundsFailures() {
	try {
	    wrapper.getIntProperty("p", 0, 200, 100);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	try {
	    wrapper.getIntProperty("p", 200, 0, 100);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	props.setProperty("p", "100");
	try {
	    wrapper.getIntProperty("p", 0, 0, 99);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	props.setProperty("p", "-100");
	try {
	    wrapper.getIntProperty("p", 0, 0, 99);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testGetIntSuccess() {
	assertEquals(10, wrapper.getIntProperty("p", 10));
	assertEquals(10, wrapper.getIntProperty("p", 10, 0, 100));
	props.setProperty("p", "100");
	assertEquals(100, wrapper.getIntProperty("p", 10));
	assertEquals(100, wrapper.getIntProperty("p", 10, 0, 100));
    }

    /* -- Test getLongProperty -- */

    public void testGetLongNullArgs() {
	try {
	    wrapper.getLongProperty(null, 3);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

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

    public void testGetLongBoundsFailures() {
	try {
	    wrapper.getLongProperty("p", 0, 200, 100);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	try {
	    wrapper.getLongProperty("p", 200, 0, 100);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	props.setProperty("p", "100");
	try {
	    wrapper.getLongProperty("p", 0, 0, 99);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	props.setProperty("p", "-100");
	try {
	    wrapper.getLongProperty("p", 0, 0, 99);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testGetLongSuccess() {
	assertEquals(1234567890123456L,
		     wrapper.getLongProperty("p", 1234567890123456L));
	assertEquals(1234567890123456L,
		     wrapper.getLongProperty(
			 "p", 1234567890123456L, 0, 1234567890123456L));
	props.setProperty("p", "2345678901234567");
	assertEquals(2345678901234567L, wrapper.getLongProperty("p", 10));
	assertEquals(2345678901234567L,
		     wrapper.getLongProperty("p", 10, 0, 2345678901234567L));
    }
}
	
