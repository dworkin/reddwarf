/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.impl.kernel.AccessCoordinatorHandle;
import com.sun.sgs.impl.profile.ProfileCollectorHandle;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.lang.reflect.Constructor;
import java.util.Properties;
import org.junit.runner.RunWith;

/**
 * Tests the {@link TrackingAccessCoordinator} class.
 */
@RunWith(FilteredNameRunner.class)
public class TestTrackingAccessCoordinator
    extends BasicAccessCoordinatorTest<AccessCoordinatorHandle>
{
    /** Creates a {@code TrackingAccessCoordinator}. */
    protected AccessCoordinatorHandle createAccessCoordinator() {
	try {
	    Constructor<? extends AccessCoordinatorHandle> constructor =
		Class.forName(
		    "com.sun.sgs.impl.kernel.TrackingAccessCoordinator")
		.asSubclass(AccessCoordinatorHandle.class)
		.getDeclaredConstructor(Properties.class,
					TransactionProxy.class,
					ProfileCollectorHandle.class);
	    constructor.setAccessible(true);
	    return constructor.newInstance(
		properties, txnProxy, profileCollector);
	} catch (Exception e) {
	    throw new RuntimeException("Unexpected exception: " + e, e);
	}
    }
}
