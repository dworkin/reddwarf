/*
 * Copyright (c) 2007-2008, Sun Microsystems, Inc.
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
 */

package com.sun.sgs.example.chat.app;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.NameNotBoundException;

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
        logger.log(Level.INFO, "ClientSession logging in: {0}", session);
        // Give the session a binding in the data store
        DataManager dataMgr = AppContext.getDataManager();
        
        final String userName = session.getName();
        String key = sessionIdKey(userName);
        try {
            dataMgr.getBinding(key);
            // If the name is already used, refuse log in.
            String reply = "User " + userName + " already logged in";
            logger.log(Level.WARNING, reply);
            try {
                session.send(ChatClientSessionListener.toMessageBuffer(
                                                   "/loginFailed " + reply));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        } catch (NameNotBoundException e) {
            dataMgr.setBinding(key, session);
        }

        return new ChatClientSessionListener(session);
    }
    
    /**
     * Returns the ClientSession with the given name
     * @param name the user name
     * @return the ClientSession for the given user name
     */
    static ClientSession getSessionFromIdString(String name) {
        String key = sessionIdKey(name);
        return (ClientSession) AppContext.getDataManager().getBinding(key);
    }


    /**
     * Removes the data-store ID binding for the given session.
     * 
     * @param session a session
     */
    static void removeSessionBinding(ClientSession session) {
        DataManager dataMgr = AppContext.getDataManager();
        dataMgr.removeBinding(sessionIdKey(session.getName()));
    }

    /**
     * Returns the data store key for the session with the name.
     * 
     * @param name the name for the session
     * @return the data store key for the session
     */
    private static String sessionIdKey(String name) {
        return SESSION_PREFIX + name;
    }
}
