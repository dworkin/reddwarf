/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.test.impl.service.data.store.net;

import com.sun.sgs.test.impl.service.data.BasicDataServiceMultiTest;
import com.sun.sgs.test.util.SgsTestNode;
import java.util.Properties;

/**
 * Perform multi-node tests on the {@code DataService} using the network data
 * store.
 */
public class TestDataServiceClientMulti extends BasicDataServiceMultiTest {

    @Override
    protected Properties getServerProperties() throws Exception {
	return SgsTestNode.getDefaultProperties(appName, null, null);
    }
}
