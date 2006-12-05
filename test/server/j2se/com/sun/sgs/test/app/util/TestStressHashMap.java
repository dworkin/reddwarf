package com.sun.sgs.test.app.util;

import java.util.HashMap;

public class TestStressHashMap extends BasicMapStressTest {

    public TestStressHashMap(String name) {
	super(name, new HashMap<String, Object>());
    }

    public void testStress() {
	stress(true, true, 1000);
    }
}
