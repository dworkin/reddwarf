/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.app.util;

import java.io.Serializable;
import java.util.Properties;

import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.ManagedObject;

/**
 * TODO doc
 * 
 * The idea here is to maintain the default mapping of service IDs to
 * DispatchListeners, and return a new SessionDispatcher for each login
 * that sets their dispatch mapping to the current default (unless
 * overridden in a subclass).
 * <p>
 * The default mapping should be set up in {@code initialize}, probably
 * based on some properties.
 * <p>
 * It would be nice to implement this so that it could be used as either
 * a base class or as a delegate, depending on the application's style.
 */
public class AppDispatcher
    implements AppListener, ManagedObject, Serializable
{
    /** The version of the serialized form. */
    private static final long serialVersionUID = 1L;

    /**
     * TODO doc
     */
    protected AppDispatcher() {
        // TODO
    }

    // implement AppListener

    /**
     * TODO doc
     */
    public void initialize(Properties props) {
        // TODO
    }

    /**
     * TODO doc
     */
    public ClientSessionListener loggedIn(ClientSession session) {
        // TODO
        return null;
    }
}