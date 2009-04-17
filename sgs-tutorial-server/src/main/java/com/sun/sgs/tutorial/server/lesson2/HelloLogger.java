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

package com.sun.sgs.tutorial.server.lesson2;

import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;

/**
 * Hello World with Logging example for the Project Darkstar Server.
 * It logs {@code "Hello World!"} at level {@link Level#INFO INFO}
 * when first started.
 */
public class HelloLogger
    implements AppListener, // to get called during application startup.
               Serializable // since all AppListeners are ManagedObjects.
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@link Logger} for this class. */
    private static final Logger logger =
        Logger.getLogger(HelloLogger.class.getName());

    /**
     * {@inheritDoc}
     * <p>
     * Logs our well-known greeting during application startup.
     */
    public void initialize(Properties props) {
        logger.log(Level.INFO, "Hello World!");
    }

    /**
     * {@inheritDoc}
     * <p>
     * Prevents client logins by returning {@code null}.
     */
    public ClientSessionListener loggedIn(ClientSession session) {
        return null;
    }
}
