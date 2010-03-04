/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.test.impl.service.data.store;

import com.sun.sgs.test.util.UtilDataStoreDb;
import com.sun.sgs.test.util.UtilDataStoreDb.EnvironmentType;
import com.sun.sgs.tools.test.FilteredNameRunner;
import org.junit.runner.notification.RunNotifier;

/**
 * A subclass of {@code FilteredNameRunner} that does not run the test if BDB
 * Java edition is not in use.
 */
public class JeOnlyFilteredNameRunner extends FilteredNameRunner {

    /** Whether BDB Java edition is in use. */
    private boolean usingJe;

    /**
     * Creates an instance for running tests in the given class.
     *
     * @param	c the class which needs its tests run
     * @throws	Exception if an error occurs initializing the runner
     */
    public JeOnlyFilteredNameRunner(Class<?> c) throws Exception {
	super(c);
	EnvironmentType env =
	    UtilDataStoreDb.getEnvironmentType(System.getProperties());
	usingJe = (env == EnvironmentType.JE);
    }

    /**
     * {@inheritDoc}
     * 
     * Skips running the tests if BDB Java edition is not in use.
     */
    public void run(RunNotifier runNotifier) {
	if (usingJe) {
	    super.run(runNotifier);
	}
    }
}	
