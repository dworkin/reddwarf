package com.sun.sgs.test.app.util;

import com.sun.sgs.app.util.ScalableManagedHashMap;

/** Stress test the ScalableManagedHashMap classes */
public class TestStressScalableManagedHashMap extends DataMapStressTest {

    /** Creates the test. */
    public TestStressScalableManagedHashMap(String name) {
	super(name, new ScalableManagedHashMap<String, Object>());
    }

    public void testStress() {
	stress(true, true, 1000);
    }
}
