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

package com.sun.sgs.impl.profile;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileProducer;
import com.sun.sgs.profile.ProfileRegistrar;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This is a simple implementation of <code>ProfileRegistrar</code> used by
 * the kernel to handle registering <code>ProfileProducer</code>s that end
 * up reporting to a single <code>ProfileCollectorImpl</code>.
 */
public class ProfileRegistrarImpl implements ProfileRegistrar {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(ProfileRegistrarImpl.
                                           class.getName()));

    // the backing collector
    private final ProfileCollectorImpl profileCollector;

    /**
     * Creates an instance of <code>ProfileRegistrarImpl</code>.
     *
     * @param profileCollector the backing collector for all producers
     */
    public ProfileRegistrarImpl(ProfileCollectorImpl profileCollector) {
        if (profileCollector == null)
            throw new NullPointerException("Collector must not be null");
        this.profileCollector = profileCollector;
    }

    /**
     * {@inheritDoc}
     */
    public ProfileConsumer registerProfileProducer(ProfileProducer producer) {
        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "Registering profile producer {0}",
                       producer);
        return new ProfileConsumerImpl(producer, profileCollector);
    }

}
