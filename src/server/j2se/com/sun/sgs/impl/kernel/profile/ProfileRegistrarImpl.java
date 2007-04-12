/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel.profile;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import com.sun.sgs.kernel.ProfileConsumer;
import com.sun.sgs.kernel.ProfileProducer;
import com.sun.sgs.kernel.ProfileRegistrar;

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
