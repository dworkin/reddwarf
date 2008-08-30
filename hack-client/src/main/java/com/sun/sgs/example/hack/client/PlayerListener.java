/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.client;

import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.CreatureInfo;
import com.sun.sgs.example.hack.share.CreatureInfo.CreatureType;

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
    public void setCharacter(CreatureType characterClassType,
			     CharacterStats stats);

    /**
     * Called to update aspects of the player's currrent character.
     */
    public void updateCharacter();

    /*
     * TODO: we also need some inventory methods
     */

}
