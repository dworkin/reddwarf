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

package com.sun.sgs.example.chat.app;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedReference;

import java.math.BigInteger;
import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple chat application.  The application logic for this example
 * is mostly contained in {@link ChatClientSessionListener}.
 */
public class ChatApp
    implements Serializable, AppListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The logger for this class. */
    private static final Logger logger =
        Logger.getLogger(ChatApp.class.getName());

    /** The prefix for storing sessions by ID in the data store. */
    private static final String SESSION_PREFIX =
        "com.sun.sgs.example.chat.app.ChatApp.";
    
    /**
     * The default constructor.
     */
    public ChatApp() {
        // empty
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@code ChatApp} creates its {@linkplain com.sun.sgs.app.Channel
     * Channels} as they are needed, so it has no initialization to perform
     * on startup.
     */
    public void initialize(Properties props) {
        logger.log(Level.CONFIG, "ChatApp starting up");
    }

    /**
     * {@inheritDoc}
     * <p>
     * {@code ChatApp} returns a new {@link ChatClientSessionListener}
     * for the given {@code session}.
     */
    public ClientSessionListener loggedIn(ClientSession session) {
        logger.log(Level.INFO, "ClientSession joined: {0}", session);
        // Give the session a binding in the data store
        DataManager dataMgr = AppContext.getDataManager();
        ManagedReference<ClientSession> sessionRef =
                dataMgr.createReference(session);
        String key = sessionIdKey(sessionRef.getId());
        dataMgr.setBinding(key, session);

        return new ChatClientSessionListener(session);
    }
    
    /**
     * Must be called in a transaction.
     * @param id
     * @return
     */
    static ClientSession getSessionFromIdString(String id) {
        String key = sessionIdKey(id);
        return (ClientSession) AppContext.getDataManager().getBinding(key);
    }


    /**
     * Removes the data-store ID binding for the given session.
     * 
     * @param session a session
     */
    static void removeSessionBinding(ClientSession session) {
        DataManager dataMgr = AppContext.getDataManager();
        dataMgr.removeBinding(
            sessionIdKey(dataMgr.createReference(session).getId()));
    }

    /**
     * Returns the data store key for the session with the given id.
     * 
     * @param sessionRefId the id for the session
     * @return the data store key for the session
     */
    private static String sessionIdKey(BigInteger sessionRefId) {
        return sessionIdKey(sessionRefId.toString(16));
    }
    
    private static String sessionIdKey(String sessionRefId) {
        return SESSION_PREFIX + sessionRefId;
    }
}
