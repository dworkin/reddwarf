/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * --
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
