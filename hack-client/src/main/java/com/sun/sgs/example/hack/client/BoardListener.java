/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.client;

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.BoardSpace;

import java.awt.Image;

import java.util.Map;


/**
 * This interface defines a class that listenens for updates to the game
 * board and associated state.
 */
public interface BoardListener
{

    /**
     * Notifies the listener that the board has changed.
     *
     * @param board the new board where the player is playing
     */
    public void changeBoard(Board board);

    /**
     * Notifies the listener that some set of spaces on the board have changed.
     *
     * @param spaces the changed space detail
     */
    public void updateSpaces(BoardSpace [] spaces);

    /**
     * Notifies the listener of a message. Like a chat message, this is a
     * simple string that should be displayed to the user. Unlike a chat
     * message, this message comes directly from the server, not another
     * player.
     *
     * @param message the message that the player should "hear"
     */
    public void hearMessage(String message);

}
