/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */
package com.sun.sgs.example.hack.client;

import com.sun.sgs.example.hack.share.CharacterStats;

import java.util.Collection;


/**
 * This interface defines a class that listens for events from the lobby.
 */
public interface LobbyListener
{

    /**
     * Notifies the listener that a game was added.
     *
     * @param game the name of the game
     */
    public void gameAdded(String game);

    /**
     * Notifies the listener that a game was removed.
     *
     * @param game the name of the game
     */
    public void gameRemoved(String game);

    /**
     * Notifies the listener that the membership count of the lobby has
     * changed.
     *
     * @param count the number of players
     */
    public void playerCountUpdated(int count);

    /**
     * Notifies the listener that the membership count of some game has
     * changed.
     *
     * @param game the name of the game where the count changed
     * @param count the number of players
     */
    public void playerCountUpdated(String game, int count);

    /**
     * Notifies the listener of the characters available for the player.
     *
     * @param characters the characters available to play
     */
    public void setCharacters(Collection<CharacterStats> characters);

}
