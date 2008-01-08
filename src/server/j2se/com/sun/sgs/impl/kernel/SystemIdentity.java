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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * Package private class that represents the identity of the system. This
 * class enforces a singleton pattern, so that there is only ever one instance
 * of <code>SystemIdentity</code>. This identity is stateless, and available
 * through the <code>IDENTITY</code> field.
 * <p>
 * Note that it would always be an error to persist or otherwise externalize
 * any system tasks. Because of this, this class does not implement
 * <code>Serializable</code>, so we can catch any such occurences as errors.
 */
final class SystemIdentity implements Identity
{

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(SystemIdentity.class.getName()));

    /**
     * The single instance of the system's identity.
     */
    static final Identity IDENTITY = new SystemIdentity();

    /**
     * Creates an instance of <code>SystemIdentity</code>
     */
    private SystemIdentity() {
        
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return "System";
    }

    /**
     * This should never be called, as the system identity cannot login.
     */
    public void notifyLoggedIn() {
        logger.log(Level.SEVERE, "System identity notified of login");
    }

    /**
     * This should never be called, as the system identity cannot logout.
     */
    public void notifyLoggedOut() {
        logger.log(Level.SEVERE, "System identity notified of logout");
    }

}
