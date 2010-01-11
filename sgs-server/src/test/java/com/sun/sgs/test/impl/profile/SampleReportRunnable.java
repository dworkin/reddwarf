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
import java.util.List;
import java.util.concurrent.Exchanger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Helper class for sample tests.  This runnable is run during
 * the profile listener's report method.  It checks for a known
 * task owner to know if a sample should have been added,
 * otherwise the sample should not be in the profile report.
 * <p>
 * Synchronization with the test case is performed through an
 * Exchanger. If an AssertionError is thrown, it is assumed to
 * have come from the JUnit framework and is passed back to the
 * test thread so it can be reported there.  Otherwise, JUnit
 * does not note that the test has failed.
 */
class SampleReportRunnable implements Runnable {

    final String name;
    final Identity negativeOwner;
    final Identity positiveOwner;
    final Exchanger<AssertionError> errorExchanger;
    final List<Long> expectedValues;

    public SampleReportRunnable(String sampleName, 
                                Identity negativeOwner,
                                Identity positiveOwner, 
                                Exchanger<AssertionError> errorExchanger, 
                                List<Long> expectedValues) 
    {
        super();
        this.name = sampleName;
        this.negativeOwner = negativeOwner;
        this.positiveOwner = positiveOwner;
        this.errorExchanger = errorExchanger;
        this.expectedValues = expectedValues;
    }

    public void run() {
        AssertionError error = null;
        ProfileReport report = SimpleTestListener.report;
        // Check to see if we expected the sample values to be
        // updated in this report.
        Identity owner = report.getTaskOwner();
        boolean update = owner.equals(positiveOwner);
        if (!update && !owner.equals(negativeOwner)) {
            return;
        }
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
