/*
 * Copyright 2007 Sun Microsystems, Inc.
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


package com.sun.sgs.example.hack.client;

//import com.sun.gi.comm.users.client.ClientConnectionManager;

import com.sun.sgs.client.simple.SimpleClient;

import com.sun.sgs.example.hack.share.CharacterStats;

import java.nio.ByteBuffer;

import java.util.HashSet;


/**
 * This manager handles all messages from and to the creator on the server.
 */
public class CreatorManager implements CreatorListener
{

    // the connection manager, used to send messages to the server
    //private ClientConnectionManager connManager = null;
    private SimpleClient client = null;

    // the listeners
    private HashSet<CreatorListener> listeners;

    /**
     * Creates a new instance of <code>CreatorManager</code>.
     */
    public CreatorManager() {
        listeners = new HashSet<CreatorListener>();
    }

    /**
     *
     */
    public void addCreatorListener(CreatorListener listener) {
        listeners.add(listener);
    }

    /**
     * Sets the connection manager that this class uses for all communication
     * with the game server. This method may only be called once during
     * the lifetime of the client.
     *
     * @param connManager the connection manager
     */
    public void setConnectionManager(SimpleClient simpleClient) {
        if (client == null)
            client = simpleClient;
    }

    /**
     * Requests new statistics be rolled for the given character
     *
     * @param charClass the type of character
     */
    public void rollForStats(int charClass) {
        ByteBuffer bb = ByteBuffer.allocate(5);

        bb.put((byte)1);
        bb.putInt(charClass);
        bb.rewind();

        byte [] bytes = new byte[5];
        bb.get(bytes);
        try {
            client.send(bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Requests that the server create the current character as one owned
     * by the player. This ends the character creation.
     *
     * @param name the character's name
     */
    public void createCurrentCharacter(String name) {
        ByteBuffer bb = ByteBuffer.allocate(1 + name.length());

        bb.put((byte)2);
        bb.put(name.getBytes());
        bb.rewind();
        
        byte [] bytes = new byte[1 + name.length()];
        bb.get(bytes);
        try {
            client.send(bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Requests that character creation finish without creating a character.
     */
    public void cancelCreation() {
        ByteBuffer bb = ByteBuffer.allocate(1);

        bb.put((byte)3);
        bb.rewind();

        byte [] bytes = new byte[1];
        bb.get(bytes);
        try {
            client.send(bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Notifies the listener of new character statistics.
     *
     * @param id the character's identifier
     * @param stats the new statistics
     */
    public void changeStatistics(int id, CharacterStats stats) {
        for (CreatorListener listener : listeners)
            listener.changeStatistics(id, stats);
    }

}
