/*
 * Copyright (c) 2007-2008, Sun Microsystems, Inc.
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
 */

package com.sun.sgs.test.impl.sharedutil;

import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import java.util.Properties;
import junit.framework.TestCase;

/** Tests for the PropertiesWrapper class. */
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
}
	
