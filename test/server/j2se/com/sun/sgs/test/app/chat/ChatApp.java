package com.sun.sgs.test.app.chat;

import java.io.Serializable;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;

/**
 * A simple example chat application for the Sun Game Server.
 * <p>
 * Note that this {@link AppListener} has no mutable state, so it doesn't
 * need to implement {@link com.sun.sgs.app.ManagedObject}.
 */
public class ChatApp
    implements Serializable, AppListener
{
    private static final long serialVersionUID = 1L;

    /** The logger for this class. */
    private static final Logger logger =
        Logger.getLogger(ChatApp.class.getName());
    /**
     * {@inheritDoc}
     * <p>
     * Since {@code ChatApp} creates its {@linkplain com.sun.sgs.app.Channel
     * Channels} as they are needed, it has no initialization to do here.
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

        return new ChatClientSessionListener(session);
    }
}
