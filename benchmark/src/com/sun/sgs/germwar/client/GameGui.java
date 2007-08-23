/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.client;

import java.net.PasswordAuthentication;

import com.sun.sgs.germwar.client.GameLogic;
import com.sun.sgs.germwar.shared.Location;

/**
 * The interface for GUIs for GermWar client applications.  It abstracts all of
 * the GUI-specific operations from the actual application logic.  Typically,
 * implementations of {@code GameGui} will communicate with an instance of
 * {@link GameLogic}.  Implementations should have no knowledge of SGS classes.
 */
public interface GameGui {
    /**
     * Updates one location on the map.
     */
    void mapUpdate(Location loc);

    /**
     * Updates the GUI with a new chat message from a specific sender.  If
     * {@code sender} is {@code null}, the sender is not specified.
     */
    void newChatMessage(String sender, String message);

    /**
     * Notifies the GUI that a new turn has just begun.
     */
    void newTurn();

    /**
     * Causes an alert popup of type {@code messageType} (should be one of the
     * options defined in {@link javax.swing.JOptionPane}).
     */
    void popup(String message, int messageType);

    /** Prompts the user for a login and password. */
    PasswordAuthentication promptForLogin();

    /**
     * Updates the GUI that the user is now logged in/out of the server.
     */
    void setLoginStatus(boolean loggedIn);

    /**
     * Sets the status message, which typically shows the overall client state
     * (e.g. "logged in").
     */
    void setStatusMessage(String message);

    /** Shows/hides the gui. */
    void setVisible(boolean b);
}
