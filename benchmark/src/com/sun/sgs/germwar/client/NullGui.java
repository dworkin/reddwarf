/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.client;

import java.net.PasswordAuthentication;

import com.sun.sgs.germwar.shared.Location;

/**
 * A bare-bones implementation of GameGui; most methods do nothing at all.  This
 * is typically suitable only for clients that do not provide a UI at all (such
 * as AI-driven clients).
 */
public class NullGui implements GameGui {
    /** Username and password to use when logging in. */
    private final String login, password;

    // Constructor

    /**
     * Creates a new {@code NullGui}.
     */
    public NullGui(String login, String password) {
        this.login = login;
        this.password = password;
    }

    // implement GameGui

    /**
     * {@inheritDoc}
     */
    public void mapUpdate(Location loc) {
        // empty
    }

    /**
     * {@inheritDoc}
     */
    public void newChatMessage(String sender, String message) {
        System.out.println(sender + ": " + message);
    }

    /**
     * {@inheritDoc}
     */
    public void newTurn() {
        // empty
    }

    /**
     * {@inheritDoc}
     */
    public void popup(String message, int messageType) {
        System.out.println("Alert: " + message);
    }

    /**
     * {@inheritDoc}
     */
    public PasswordAuthentication promptForLogin() {
        return new PasswordAuthentication(login, password.toCharArray());
    }

    /**
     * {@inheritDoc}
     */
    public void setLoginStatus(boolean loggedIn) {
        // empty
    }

    /**
     * {@inheritDoc}
     */
    public void setStatusMessage(String message) {
        // empty
    }

    /**
     * {@inheritDoc}
     */
    public void setVisible(boolean b) {
        // empty
    }
}
