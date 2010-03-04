/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */
package com.sun.sgs.test.impl.profile;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.profile.ProfileReport;
import java.util.concurrent.Exchanger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Helper class for counter tests.  This runnable is run during
 * the profile listener's report method.  It checks for a known
 * task owner to know if a counter should have been incremented,
 * otherwise the counter should not be in the profile report.
 * <p>
 * Synchronization with the test case is performed through an
 * Exchanger. If an AssertionError is thrown, it is assumed to
 * have come from the JUnit framework and is passed back to the
 * test thread so it can be reported there.  Otherwise, JUnit
 * does not note that the test has failed.
 */
class CounterReportRunnable implements Runnable {
    final String name;
    final Identity negativeOwner;
    final Identity positiveOwner;
    final Exchanger<AssertionError> errorExchanger;
    final int incrementValue;

    public CounterReportRunnable(String name, Identity negativeOwner, 
                                 Identity positiveOwner,
                                 Exchanger<AssertionError> errorExchanger, 
                                 int incrementValue) 
    {
        super();
        this.name = name;
        this.negativeOwner = negativeOwner;
        this.positiveOwner = positiveOwner;
        this.errorExchanger = errorExchanger;
        this.incrementValue = incrementValue;
    }

    public void run() {
        AssertionError error = null;
        ProfileReport report = SimpleTestListener.report;

        // Check to see if this is a report we care about by checking
        // the owner of this task.
        Identity owner = report.getTaskOwner();
        boolean update = owner.equals(positiveOwner);
        if (!update && !owner.equals(negativeOwner)) {
            return;
        }
        
        if (update) {
            try {
                Long value = report.getUpdatedTaskCounters().get(name);
                System.err.println("got counter value of " + value);
                assertEquals(incrementValue, value.intValue());
            } catch (AssertionError e) {
                error = e;
            }
        } else {
            try {
                Long value = report.getUpdatedTaskCounters().get(name);
                assertNull("expected no value", value);
            } catch (AssertionError e) {
                error = e;
            }
        }
        try {
            errorExchanger.exchange(error);
        } catch (InterruptedException ignored) {
        }
    }
}
