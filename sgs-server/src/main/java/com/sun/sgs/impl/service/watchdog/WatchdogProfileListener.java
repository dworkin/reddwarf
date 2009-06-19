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

package com.sun.sgs.impl.service.watchdog;

import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.profile.ProfileListener;
import com.sun.sgs.profile.ProfileReport;
import com.sun.sgs.service.Node.Status;
import com.sun.sgs.service.WatchdogService;
import java.beans.PropertyChangeEvent;
import java.util.Properties;

/**
 */
class WatchdogProfileListener implements ProfileListener {

    private static final String PKG_NAME = "com.sun.sgs.impl.service.watchdog";

    private static final String READY_LIMIT_PROPERTY =
                                                    PKG_NAME + ".ready.limit";

    private static final String NO_NAME_PROPERTY = PKG_NAME + ".no.name";

    private static final String CLASSNAME =
                                    WatchdogProfileListener.class.getName();

    private final WatchdogService service;
    private final int readyLimit;
    private final int noName;
    private Status status;
    private int over;
    private int under;

    public WatchdogProfileListener(Properties properties,
                                   WatchdogService service)
    {
        this.service = service;
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        readyLimit = wrappedProps.getIntProperty(READY_LIMIT_PROPERTY,
                                                 Integer.MAX_VALUE,
                                                 0, Integer.MAX_VALUE);
        noName = wrappedProps.getIntProperty(NO_NAME_PROPERTY,
                                             Integer.MAX_VALUE,
                                             1, Integer.MAX_VALUE);
        System.out.println("ready high water= " + readyLimit +
                           " noName= " + noName);
        status = Status.GREEN;
        over = under = 0;
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
    }

    @Override
    public synchronized void report(ProfileReport profileReport) {
        if (profileReport.getReadyCount() > readyLimit) {
            under = 0;
            over++;
            if (over == noName) {
                setStatus(Status.YELLOW);
            }
        } else {
            over = 0;
            under++;
            if (under == noName) {
                setStatus(Status.GREEN);
            }
        }
    }

    private void setStatus(Status newStatus) {
        if (newStatus == status) return;
//        System.out.println("WPL.setStatus(" + newStatus + ") current status is: " + status);
        status = newStatus;
        service.reportStatus(service.getLocalNodeId(), status, CLASSNAME);
    }

    @Override
    public void shutdown() {
    }
}
