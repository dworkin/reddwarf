
/*
 * LobbyManager.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Feb 26, 2006	 9:51:41 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client;

import com.sun.gi.comm.users.client.ClientConnectionManager;

import com.sun.gi.apps.hack.share.CharacterStats;

import java.nio.ByteBuffer;

import java.util.Collection;
import java.util.HashSet;


public class LobbyManager implements LobbyListener
{

    //
    private HashSet<LobbyListener> listeners;

    //
    private ClientConnectionManager connManager = null;

    /**
     *
     */
    public LobbyManager() {
        listeners = new HashSet<LobbyListener>();
    }

    /**
     *
     */
    public void setConnectionManager(ClientConnectionManager connManager) {
        if (this.connManager == null)
            this.connManager = connManager;
    }

    /**
     *
     */
    public void addLobbyListener(LobbyListener listener) {
        listeners.add(listener);
    }

    /**
     * FIXME: this needs to take some action info, but since there's only
     * one command to take right now, this is just a string
     */
    public void action(String gameName, String characterName) {
        // FIXME: for now there's only one command, so there aren't any
        // option here
        ByteBuffer bb = ByteBuffer.allocate(5 + gameName.length() +
                                            characterName.length());
        bb.put((byte)1);
        bb.putInt(gameName.length());
        bb.put(gameName.getBytes());
        bb.put(characterName.getBytes());
        connManager.sendToServer(bb, true);
    }

    /**
     *
     */
    public void gameAdded(String game) {
        for (LobbyListener listener : listeners)
            listener.gameAdded(game);
    }

    /**
     *
     */
    public void gameRemoved(String game) {
        for (LobbyListener listener : listeners)
            listener.gameRemoved(game);
    }

    /**
     *
     */
    public void playerCountUpdated(int count) {
        for (LobbyListener listener : listeners)
            listener.playerCountUpdated(count);
    }

    /**
     *
     */
    public void playerCountUpdated(String game, int count) {
        for (LobbyListener listener : listeners)
            listener.playerCountUpdated(game, count);
    }

    /**
     *
     */
    public void setCharacters(Collection<CharacterStats> characters) {
        for (LobbyListener listener : listeners)
            listener.setCharacters(characters);
    }

}
