
/*
 * GameManager.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Feb 25, 2006	10:22:26 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client;

import com.sun.gi.comm.users.client.ClientConnectionManager;

import com.sun.gi.apps.hack.share.Board;
import com.sun.gi.apps.hack.share.BoardSpace;
import com.sun.gi.apps.hack.share.CharacterStats;
import com.sun.gi.apps.hack.share.KeyMessages;

import java.awt.Image;

import java.awt.event.KeyEvent;

import java.nio.ByteBuffer;

import java.util.HashSet;
import java.util.Map;


/**
 *
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class GameManager implements BoardListener, PlayerListener,
                                    CommandListener
{

    //
    private HashSet<BoardListener> boardListeners;
    private HashSet<PlayerListener> playerListeners;

    //
    private ClientConnectionManager connManager = null;

    /**
     *
     */
    public GameManager() {
        boardListeners = new HashSet<BoardListener>();
        playerListeners = new HashSet<PlayerListener>();
    }

    /**
     *
     */
    public void addBoardListener(BoardListener listener) {
        boardListeners.add(listener);
    }

    /**
     *
     */
    public void addPlayerListener(PlayerListener listener) {
        playerListeners.add(listener);
    }

    /**
     *
     */
    public void setConnectionManager(ClientConnectionManager connManager) {
        if (this.connManager == null)
            this.connManager = connManager;
    }

    /**
     * this is called by the gui code ... an action is a key press to move
     * us in some direction (which may be illegal, may be an attack, etc.),
     * a request to take some item, equipping/using some item ... and is
     * there anything else?
     * FIXME: define what this looks like, and what parameters it can take
     * (the current approach of taking a key is temporary)
     */
    public void action(int key) {
        // make sure that this is a key we care about
        int messageType;
        short message = KeyMessages.NONE;

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
            // if we got here, this is a key we don't know how to handle,
            // so just ignore it
            return;
        }

        ByteBuffer bb = ByteBuffer.allocate(5);
        bb.put((byte)messageType);
        bb.putShort(message);
        connManager.sendToServer(bb, true);
    }

    /**
     *
     */
    public void setSpriteMap(int spriteSize, Map<Integer,Image> spriteMap) {
        for (BoardListener listener : boardListeners)
            listener.setSpriteMap(spriteSize, spriteMap);
    }

    /**
     *
     */
    public void changeBoard(Board board) {
        for (BoardListener listener : boardListeners)
            listener.changeBoard(board);
    }

    /**
     *
     */
    public void updateSpaces(BoardSpace [] spaces) {
        for (BoardListener listener : boardListeners)
            listener.updateSpaces(spaces);
    }

    /**
     *
     */
    public void hearMessage(String message) {
        for (BoardListener listener : boardListeners)
            listener.hearMessage(message);
    }

    /**
     *
     */
    public void setCharacter(int id, CharacterStats stats) {
        for (PlayerListener listener : playerListeners)
            listener.setCharacter(id, stats);
    }

    /**
     *
     */
    public void updateCharacter(/*FIXME: define this type*/) {
        for (PlayerListener listener : playerListeners)
            listener.updateCharacter();
    }


}
