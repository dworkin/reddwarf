/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;
import java.beans.PropertyChangeEvent;

/** A simple profile listener that notes calls to the public APIs */
class SimpleTestListener implements ProfileListener {
    int propertyChangeCalls = 0;
    long reportedNodeId = -1L;
    int reportCalls = 0;
    int shutdownCalls = 0;
    final Runnable doReport;
    // Make the profile report available to the doReport runnable.
    static ProfileReport report;

    SimpleTestListener() {
        this.doReport = null;
    }
    SimpleTestListener(Runnable doReport) {
        this.doReport = doReport;
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
        propertyChangeCalls++;
        if (event.getPropertyName().equals("com.sun.sgs.profile.nodeid")) {
            reportedNodeId = (Long) event.getNewValue();
        }
    }

    @Override
    public void report(ProfileReport profileReport) {
        reportCalls++;
        if (doReport != null) {
            SimpleTestListener.report = profileReport;
            doReport.run();
        }
    }

    @Override
    public void shutdown() {
        shutdownCalls++;
    }
}
