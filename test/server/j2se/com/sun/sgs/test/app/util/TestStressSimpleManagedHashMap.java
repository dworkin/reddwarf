package com.sun.sgs.test.app.util;

import com.sun.sgs.app.util.SimpleManagedHashMap;

/** Stress test the SimpleManagedHashMap class */
public class TestStressSimpleManagedHashMap extends DataMapStressTest {

    /** Creates the test. */
    public TestStressSimpleManagedHashMap(String name) {
	super(name, new SimpleManagedHashMap<String, Object>());
    }

    public void testStress() {
	stress(true, true, 1000);
    }
}
