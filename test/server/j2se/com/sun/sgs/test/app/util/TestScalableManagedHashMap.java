package com.sun.sgs.test.app.util;

import com.sun.sgs.app.util.ScalableManagedHashMap;

/** Test the ScalableManagedHashMap class */
public class TestScalableManagedHashMap extends BasicDataHashMapTest {

    /** Creates the test. */
    public TestScalableManagedHashMap(String name) {
	super(name, new ScalableManagedHashMap<Object, Object>());
    }
}
