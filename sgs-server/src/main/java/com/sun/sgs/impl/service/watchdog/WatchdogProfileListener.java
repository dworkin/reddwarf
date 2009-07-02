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
import com.sun.sgs.service.Node.Health;
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
    private Health health;
    private Health lastSeen;
    private int lastSeenCount;

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
        System.out.println("CONFIG: ready queue limit= " + readyLimit +
                           " noName= " + noName);
        health = Health.GREEN;
        lastSeen = Health.GREEN;
        lastSeenCount = 0;
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
    }

    @Override
    public synchronized void report(ProfileReport profileReport) {
        Health thisTime = determineHealth(profileReport);

        if (thisTime != lastSeen) {
            lastSeenCount = 0;
            lastSeen = thisTime;
        } else {
            lastSeenCount++;

            if ((lastSeenCount == noName) &&
                (thisTime != health))
            {
                health = thisTime;
                service.reportHealth(service.getLocalNodeId(),
                                     health, CLASSNAME);
            }
        }
    }

    private Health determineHealth(ProfileReport profileReport) {
        if (profileReport.getReadyCount() > readyLimit) {
            return Health.YELLOW;
        }
        return Health.GREEN;
    }

    @Override
    public void shutdown() {
    }
}