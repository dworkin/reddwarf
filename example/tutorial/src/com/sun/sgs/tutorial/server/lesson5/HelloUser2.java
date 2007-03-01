package com.sun.sgs.tutorial.server.lesson5;

import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;

/**
 * Simple example of listening for user {@linkplain AppListener#loggedIn login}
 * in the Sun Game Server.
 * <p>
 * Logs each time a user logs in, and sets their listener to a
 * new {@link HelloUserSessionListener}.
 */
public class HelloUser2
    implements AppListener, // to get called during startup and login.
               Serializable // since all AppListeners are ManagedObjects.
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@link Logger} for this class. */
    private static final Logger logger =
        Logger.getLogger(HelloUser2.class.getName());

    //  implement AppListener

    /** {@inheritDoc} */
    public void initialize(Properties props) {
        // empty
    }

    /**
     * {@inheritDoc}
     * <p>
     * Logs a message each time a new session logs in.
     */
    public ClientSessionListener loggedIn(ClientSession session) {
        // User has logged in
        logger.log(Level.INFO, "User {0} has logged in", session.getName());

        // Return a valid listener
        return new HelloUserSessionListener(session);
    }
}

