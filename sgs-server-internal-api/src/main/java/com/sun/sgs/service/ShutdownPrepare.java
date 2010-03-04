/*
 * Copyright 2010 The RedDwarf Authors.  All rights reserved
 * Portions of this file have been modified as part of RedDwarf
 * The source code is governed by a GPLv2 license that can be found
 * in the LICENSE file.
 */

package com.sun.sgs.service;

/**
 * Optional service interface. A service implementing this interface will have
 * its {@code prepareToShutdown} method called before {@code Service.shutdown} is
 * invoked, and before any other service is shutdown. This allows the service
 * to perform any shutdown activity that may depend on other services.
 */
public interface ShutdownPrepare {

    /**
     * Prepare the service to shutdown. Any call to this method will block
     * until the preperation has completed. If a prepare has been completed
     * already, this method will return immediately.<p>
     *
     * This method does not require a transaction, and should not be called
     * from one because this method will typically not succeed if there are
     * outstanding transactions. <p>
     *
     * Callers should assume that, in a worst case, this method may block
     * indefinitely, and so should arrange to take other action (for example,
     * calling {@link System#exit System.exit}) if the call fails to complete
     * successfully in a certain amount of time.
     */
    void prepareToShutdown();

}
