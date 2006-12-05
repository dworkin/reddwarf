package com.sun.sgs.test.app.util;

import com.sun.sgs.app.util.SimpleManagedHashMap;

/** Test the SimpleManagedHashMap class */
public class TestSimpleManagedHashMap extends BasicDataHashMapTest {

    /** Creates the test. */
    public TestSimpleManagedHashMap(String name) {
	super(name, new SimpleManagedHashMap<Object, Object>());
    }
}
