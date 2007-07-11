/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel.schedule;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.util.Properties;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Simple utility class used to load <code>ApplicationScheduler</code>s based
 * on the well-known property key <code>APPLICATION_SCHEDULER_PROPERTY</code>.
 */
class LoaderUtil {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(LoaderUtil.class.getName()));

    /**
     * Used to load a new <code>ApplicationScheduler</code> instance.
     *
     * @param properties the application <code>Properties</code>
     *
     * @return a new <code>ApplicationScheduler</code>, or <code>null</code>
     *         if no scheduler was specified or the specified scheduler
     *         cannot be loaded
     */
    static ApplicationScheduler getScheduler(Properties properties) {
        String appSchedulerName =
            properties.getProperty(ApplicationScheduler.
                                   APPLICATION_SCHEDULER_PROPERTY);
        if (appSchedulerName == null)
            return null;

        Constructor<?> schedulerConstructor = null;
        try {
            Class<?> schedulerClass =
                Class.forName(appSchedulerName);
            schedulerConstructor =
                schedulerClass.getConstructor(Properties.class);
        } catch (Exception e) {
            if (logger.isLoggable(Level.CONFIG))
                logger.logThrow(Level.CONFIG, e, "Scheduler {0} unavailable",
                                appSchedulerName);
            return null;
        }

        try {
            return (ApplicationScheduler)(schedulerConstructor.
                                          newInstance(properties));
        } catch (InvocationTargetException e) {
            if (logger.isLoggable(Level.CONFIG))
                logger.logThrow(Level.CONFIG, e.getCause(), "Scheduler {0} " +
                                "failed to initialize", appSchedulerName);
        } catch (Exception e) {
            if (logger.isLoggable(Level.CONFIG))
                logger.logThrow(Level.CONFIG, e, "Scheduler {0} unavailable",
                                appSchedulerName);
        }

        return null;
    }

}
