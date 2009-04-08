/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.smoketest.server;

import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;

/**
 * General Smoke Test Server for Client API's. This application can be used to
 * trigger all the basic protocol messages for any Darkstar client API and 
 * verify the behaviour. An appropriate example client must be written to 
 * interact with this server.
 * 
 * TODO: Password validation?
 * 
 * @author Justin
 */
public class SmokeTest implements AppListener, Serializable
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@link Logger} for this class. */
    private static final Logger logger = Logger
            .getLogger(SmokeTest.class.getName());

    /**
     * {@inheritDoc}
     */
    public void initialize(Properties props)
    {
        logger.info("Client API Smoke Test Initialized");
    }

    /**
     * {@inheritDoc}
     * 
     * <p>
     * If a user logs in with the username "kickme", a null listener will be
     * returned which should generate a loginFailed() callback on the client.
     * 
     * <p>
     * If a user logs in with the username "discme", it will be disconnected
     * after login which should generate a disconnected(forced) callback on the
     * client.
     * <p>
     * 
     * All other clients are handled by the {@code SmokeTestListener}. 
     */
    public ClientSessionListener loggedIn(ClientSession session)
    {
        logger.log(Level.INFO, "User {0} has logged in", session.getName());

        return session.getName().toLowerCase().equals("kickme") ? 
                null : new SmokeTestListener(session);
    }
}
