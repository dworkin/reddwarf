package com.sun.sgs.test.impl.util;

import com.sun.sgs.impl.util.ManagedHashMap;

/** Test the ManagedHashMap class */
public class TestManagedHashMap extends BasicDataHashMapTest {

    /** Creates the test. */
    public TestManagedHashMap(String name) {
	super(name, new ManagedHashMap<Object, Object>());
    }
}
