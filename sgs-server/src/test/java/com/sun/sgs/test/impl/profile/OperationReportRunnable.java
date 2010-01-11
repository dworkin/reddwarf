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
package com.sun.sgs.test.impl.profile;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.profile.ProfileReport;
import java.util.concurrent.Exchanger;
import static org.junit.Assert.assertEquals;

/**
 * Helper class for operation tests.  This runnable is run during
 * the profile listener's report method.  It checks for a known
 * task positiveOwner to know if a operation should have been added,
 * otherwise the operation should not be in the profile report.
 * <p>
 * Synchronization with the test case is performed through an
 * Exchanger. If an AssertionError is thrown, it is assumed to
 * have come from the JUnit framework and is passed back to the
 * test thread so it can be reported there.  Otherwise, JUnit
 * does not note that the test has failed.
 */
class OperationReportRunnable implements Runnable {

    final String name;
    final Identity negativeOwner;
    final Identity positiveOwner;
    final Exchanger<AssertionError> errorExchanger;

    public OperationReportRunnable(String name, 
                                   Identity negativeOwner,
                                   Identity positiveOwner, 
                                   Exchanger<AssertionError> errorExchanger) 
    {
        super();
        this.name = name;
        this.negativeOwner = negativeOwner;
        this.positiveOwner = positiveOwner;
        this.errorExchanger = errorExchanger;
    }

    public void run() {
        AssertionError error = null;
        ProfileReport report = SimpleTestListener.report;
        // Check to see if we expected the name to be in this report.
        Identity owner = report.getTaskOwner();
        boolean expected = owner.equals(positiveOwner);
        if (!expected && !owner.equals(negativeOwner)) {
            return;
        }
        boolean found = report.getReportedOperations().contains(name);
        try {
            assertEquals(expected, found);
        } catch (AssertionError e) {
            error = e;
            for (String op : report.getReportedOperations()) {
                System.out.println(op);
            }
        }
        try {
            errorExchanger.exchange(error);
        } catch (InterruptedException ignored) {
        }
    }
}
