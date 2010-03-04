/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.sun.sgs.app;

import com.sun.sgs.internal.InternalContext;

/**
 * Provides access to facilities available in the current application context.
 * The {@code AppContext} uses the {@link InternalContext} to retrieve
 * managers via its {@link com.sun.sgs.internal.ManagerLocator ManagerLocator}.
 * This class should not be instantiated.
 */
public final class AppContext {
    
    /** This class should not be instantiated. */
    private AppContext() { }

    /**
     * Returns the {@code ChannelManager} for use by the current
     * application.  The object returned is not serializable, and should not be
     * stored as part of a managed object.
     *
     * @return	the {@code ChannelManager} for the current application
     * @throws	ManagerNotFoundException if the {@code ChannelManager} cannot
     *          be located
     */
    public static ChannelManager getChannelManager() {
        try {
            return InternalContext.getManagerLocator().getChannelManager();
        } catch (IllegalStateException ise) {
            throw new ManagerNotFoundException("ManagerLocator is " +
                                               "unavailable", ise);
        }
    }

    /**
     * Returns the {@code DataManager} for use by the current application.
     * The object returned is not serializable, and should not be stored as
     * part of a managed object.
     *
     * @return	the {@code DataManager} for the current application
     * @throws	ManagerNotFoundException if the {@code DataManager} cannot
     *          be located
     */
    public static DataManager getDataManager() {
        try {
            return InternalContext.getManagerLocator().getDataManager();
        } catch (IllegalStateException ise) {
            throw new ManagerNotFoundException("ManagerLocator is " +
                                               "unavailable", ise);
        }
    }

    /**
     * Returns the {@code TaskManager} for use by the current application.
     * The object returned is not serializable, and should not be stored as
     * part of a managed object.
     *
     * @return	the {@code TaskManager} for the current application
     * @throws	ManagerNotFoundException if the {@code TaskManager} cannot
     *          be located
     */
    public static TaskManager getTaskManager() {
        try {
            return InternalContext.getManagerLocator().getTaskManager();
        } catch (IllegalStateException ise) {
            throw new ManagerNotFoundException("ManagerLocator is " +
                                               "unavailable", ise);
        }
    }

    /**
     * Returns a manager of the specified type for use by the current
     * application.  The object returned is not serializable, and should not be
     * stored as part of a managed object.
     *
     * @param	<T> the type of the manager
     * @param	type a class representing the type of the manager
     * @return	the manager of the specified type for the current application
     * @throws	ManagerNotFoundException if no manager is found for the
     *		specified type
     */
    public static <T> T getManager(Class<T> type) {
        try {
            return InternalContext.getManagerLocator().getManager(type);
        } catch (IllegalStateException ise) {
            throw new ManagerNotFoundException("ManagerLocator is " +
                                               "unavailable", ise);
        }
    }

}
