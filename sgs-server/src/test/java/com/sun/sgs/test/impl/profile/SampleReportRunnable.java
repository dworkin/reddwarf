/*
 * Copyright 2008 Sun Microsystems, Inc.
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
 */
package com.sun.sgs.test.impl.profile;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.profile.ProfileReport;
import java.util.List;
import java.util.concurrent.Exchanger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Helper class for sample tests.  This runnable is run during
 * the profile listener's report method.  It checks for a known
 * operation to know if a sample should have been added,
 * otherwise the sample should not be in the profile report.
 * Synchronization with the test case is performed through an
 * Exchanger. If an AssertionError is thrown, it is assumed to
 * have come from the JUnit framework and is passed back to the
 * test thread so it can be reported there.  Otherwise, JUnit
 * does not note that the test has failed.
 * <p>
 * Note that this class assumes the sample will only be updated once.
 */
class SampleReportRunnable implements Runnable {

    final String name;
    final Identity positiveOwner;
    final Exchanger<AssertionError> errorExchanger;
    final List<Long> expectedValues;

    public SampleReportRunnable(String sampleName, 
                                Identity positiveIdentity, 
                                Exchanger<AssertionError> errorExchanger, 
                                List<Long> expectedValues) 
    {
        super();
        this.name = sampleName;
        this.positiveOwner = positiveIdentity;
        this.errorExchanger = errorExchanger;
        this.expectedValues = expectedValues;
    }

    public void run() {
        AssertionError error = null;
        ProfileReport report = SimpleTestListener.report;
        // Check to see if we expected the sample values to be
        // updated in this report.
        boolean update = report.getTaskOwner().equals(positiveOwner);
        List<Long> values = report.getUpdatedTaskSamples().get(name);
        try {
            if (update) {
                assertEquals(expectedValues.size(), values.size());
                for (int i = 0; i < expectedValues.size(); i++) {
                    Long found = values.get(i);
                    System.err.println("found value: " + found);
                    assertEquals(expectedValues.get(i), found);
                }
            } else {
                assertNull(values);
            }
        } catch (AssertionError e) {
            error = e;
        }
        try {
            errorExchanger.exchange(error);
        } catch (InterruptedException ignored) {
        }
    }
}
