package com.sun.sgs.tutorial.server.lesson1;

import java.io.Serializable;
import java.util.Properties;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;

/**
 * Hello World example for the Sun Game Server.
 * Prints {@code "Hello World!"} to the console the first time it's started.
 */
public class HelloWorld
    implements AppListener, // to get called during application startup.
               Serializable // since all AppListeners are ManagedObjects.
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /**
     * {@inheritDoc}
     * <p>
     * Prints our well-known greeting during application startup.
     */
    public void initialize(Properties props) {
        System.out.println("Hello World!");
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
