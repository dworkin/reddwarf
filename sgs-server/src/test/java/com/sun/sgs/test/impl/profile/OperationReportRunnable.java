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
import java.util.concurrent.Exchanger;
import static org.junit.Assert.assertEquals;

/**
 * Helper class for testing operations in ProfileReports
 */
class OperationReportRunnable implements Runnable {

    final String name;
    final Identity owner;
    final Exchanger<AssertionError> errorExchanger;

    public OperationReportRunnable(String name, 
                                   Identity owner, 
                                   Exchanger<AssertionError> errorExchanger) 
    {
        super();
        this.name = name;
        this.owner = owner;
        this.errorExchanger = errorExchanger;
    }

    public void run() {
        AssertionError error = null;
        ProfileReport report = SimpleTestListener.report;
        // Check to see if we expected the name to be in this report.
        boolean expected = report.getTaskOwner().equals(owner);
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
