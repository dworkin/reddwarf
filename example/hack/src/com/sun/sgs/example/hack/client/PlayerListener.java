/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.client;

import com.sun.sgs.example.hack.share.CharacterStats;


/**
 * This interface is used to listen for player character events. Examples
 * are character statistics or inventory changes.
 */
public interface PlayerListener
{

    /**
     * Called to tell listeners about the character that the client is
     * currently using. In this game, a player may only play one character
     * at a time.
     *
     * @param id the character's identifier, which specifies their sprite
     * @param stats the characters's statistics
     */
    public void setCharacter(int id, CharacterStats stats);

    /**
     * Called to update aspects of the player's currrent character.
     */
    public void updateCharacter(/*FIXME: define this type*/);

    /**
     * FIXME: we also need some inventory methods
     */

}
