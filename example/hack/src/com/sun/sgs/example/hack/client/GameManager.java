/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.client;

import com.sun.sgs.client.simple.SimpleClient;

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.BoardSpace;
import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.KeyMessages;

import com.sun.sgs.impl.sharedutil.HexDumper;

import java.awt.Image;

import java.awt.event.KeyEvent;

import java.nio.ByteBuffer;

import java.util.HashSet;
import java.util.Map;


/**
 * This class manages all interaction with an interactive game. It is used
 * to listen for incoming messages and aggregate them to listeners, and to
 * send messages to the game server for use in the current game.
 */
public class GameManager implements BoardListener, PlayerListener,
                                    CommandListener
{

    // the registered listeners
    private HashSet<BoardListener> boardListeners;
    private HashSet<PlayerListener> playerListeners;

    // the connection manager, used for sending messages to the server
    private SimpleClient client = null;

    /**
     * Creates an instance of <code>GameManager</code>.
     */
    public GameManager() {
        boardListeners = new HashSet<BoardListener>();
        playerListeners = new HashSet<PlayerListener>();
    }

    /**
     * Registers a listener for board-related events.
     *
     * @param listener the listener
     */
    public void addBoardListener(BoardListener listener) {
        boardListeners.add(listener);
    }

    /**
     * Registers a listener for player-related events.
     *
     * @param listener the listener
     */
    public void addPlayerListener(PlayerListener listener) {
        playerListeners.add(listener);
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
     * This method notifies the game server that the client has pressed some
     * key that signifies an action in the game. The handling of the key
     * is asynchronous, in that this method returns immediately, and at some
     * point in the future the server may come back with updates based on
     * the requested action.
     *
     * @param key the key, as defined in <code>java.awt.event.KeyEvent</code>
     */
    public void action(int key) {
        int messageType;
        short message = KeyMessages.NONE;

        // figure out what key was pressed and whether we care about it, and
        // also what kind of message this requires (move, take, etc.)
        // Note: this could be done on the server, so that each game could
        // define what keys it uses, but then it would be harder to map
        // custom key bindings to each action
        // FIXME: what's the right approach here?
        switch (key) {
        case KeyEvent.VK_J: message = KeyMessages.LEFT;
            messageType = 1;
            break;
        case KeyEvent.VK_K: message = KeyMessages.DOWN;
            messageType = 1;
            break;
        case KeyEvent.VK_L: message = KeyMessages.RIGHT;
            messageType = 1;
            break;
        case KeyEvent.VK_I: message = KeyMessages.UP;
            messageType = 1;
            break;
        case KeyEvent.VK_SEMICOLON: message = KeyMessages.TAKE;
            messageType = 2;
            break;
        default:
            // if we got here, this is a key we don't handle, so ignore it
            return;
        }

        // FIXME: the message code should be enumerated
        ByteBuffer bb = ByteBuffer.allocate(5);
        bb.put((byte)messageType);
        bb.putShort(message);
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
     * Notifies the manager of the sprite map that should be used. This
     * notifies all of the registered listeners.
     *
     * @param spriteSize the size, in pixels, of the sprites
     * @param spriteMap a map from sprite identifier to sprite image
     */
    public void setSpriteMap(int spriteSize, Map<Integer,Image> spriteMap) {
        for (BoardListener listener : boardListeners)
            listener.setSpriteMap(spriteSize, spriteMap);
    }

    /**
     * Notifies the manager that the board has changed. This notifies all
     * of the registered listeners.
     *
     * @param board the new board where the player is playing
     */
    public void changeBoard(Board board) {
        for (BoardListener listener : boardListeners)
            listener.changeBoard(board);
    }

    /**
     * Notifies the manager that some set of spaces on the board have changed.
     * This notifies all of the registered listeners.
     *
     * @param spaces the changed space detail
     */
    public void updateSpaces(BoardSpace [] spaces) {
        for (BoardListener listener : boardListeners)
            listener.updateSpaces(spaces);
    }

    /**
     * Notifies the manager of a message. This notifies all of the registered
     * listeners
     *
     * @param message the message that the player should "hear"
     */
    public void hearMessage(String message) {
        for (BoardListener listener : boardListeners)
            listener.hearMessage(message);
    }

    /**
     * Called to tell the manager about the character that the client is
     * currently using. This notifies all of the registered listeners.
     *
     * @param id the character's identifier, which specifies their sprite
     * @param stats the characters's statistics
     */
    public void setCharacter(int id, CharacterStats stats) {
        for (PlayerListener listener : playerListeners)
            listener.setCharacter(id, stats);
    }

    /**
     * Called to update aspects of the player's currrent character. This
     * notifies all of the registered listeners.
     */
    public void updateCharacter(/*FIXME: define this type*/) {
        for (PlayerListener listener : playerListeners)
            listener.updateCharacter();
    }


}
