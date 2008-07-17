/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.client;

//import com.sun.gi.comm.users.client.ClientConnectionManager;

import com.sun.sgs.client.simple.SimpleClient;

import com.sun.sgs.example.hack.share.CharacterStats;

import java.nio.ByteBuffer;

import java.util.Collection;
import java.util.HashSet;


/**
 * This class manages interaction with the lobby. It listens for incoming
 * messages and aggregates them to all other listeners, and it also accepts
 * and sends all outgoing messages.
 */
public class LobbyManager implements LobbyListener
{

    // the set of listeners subscribed for lobby messages
    private HashSet<LobbyListener> listeners;

    // the connection manager, used to send messages to the server
    private SimpleClient client = null;

    /**
     * Creates a new instance of <code>LobbyManager</code>.
     */
    public LobbyManager() {
        listeners = new HashSet<LobbyListener>();
    }

    /**
     * Sets the client that this class uses for all communication
     * with the game server. This method may only be called once during
     * the lifetime of the client.
     *
     * @param client the client
     */
    public void setClient(SimpleClient simpleClient) {
        if (client == null)
            client = simpleClient;
    }

    /**
     * Adds a listener for lobby events.
     *
     * @param listener the listener to add
     */
    public void addLobbyListener(LobbyListener listener) {
        listeners.add(listener);
    }

    /**
     * This method is used to tell the server that the player wants to
     * join the given game as the given player.
     *
     * @param gameName the name of the game to join
     * @param characterName the name of the character to join as
     */
    public void joinGame(String gameName, String characterName) {

	byte[] gameBytes = gameName.getBytes();
	byte[] charBytes = characterName.getBytes();

        ByteBuffer bb = ByteBuffer.allocate(5 + gameBytes.length +
					    charBytes.length);

        // FIXME: the message codes should be enumerated somewhere
        // the message format is: 1 GameNameLength GameName CharacterName
        bb.put((byte)1);
        bb.putInt(gameBytes.length);
        bb.put(gameBytes);
        bb.put(charBytes);
        bb.rewind();

        try {
            client.send(bb);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Notifies the manager that a game was added. This causes the manager
     * to notify all installed listers.
     *
     * @param game the name of the game
     */
    public void gameAdded(String game) {
        for (LobbyListener listener : listeners)
            listener.gameAdded(game);
    }

    /**
     * Notifies the manager that a game was removed. This causes the manager
     * to notify all installed listers.
     *
     * @param game the name of the game
     */
    public void gameRemoved(String game) {
        for (LobbyListener listener : listeners)
            listener.gameRemoved(game);
    }

    /**
     * Notifies the manager that the membership count of the lobby has
     * changed. This causes the manager to notify all installed listeners.
     *
     * @param count the number of players
     */
    public void playerCountUpdated(int count) {
        for (LobbyListener listener : listeners)
            listener.playerCountUpdated(count);
    }

    /**
     * Notifies the manager that the membership count of some game has
     * changed. This causes the manager to notify all installed listeners.
     *
     * @param game the name of the game where the count changed
     * @param count the number of players
     */
    public void playerCountUpdated(String game, int count) {
        for (LobbyListener listener : listeners)
            listener.playerCountUpdated(game, count);
    }

    /**
     * Notifies the manager of the characters available for the player. This
     * causes the manager to notify all installed listeners.
     *
     * @param characters the characters available to play
     */
    public void setCharacters(Collection<CharacterStats> characters) {
        for (LobbyListener listener : listeners)
            listener.setCharacters(characters);
    }

}
