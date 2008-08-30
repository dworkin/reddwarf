/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import com.sun.sgs.example.hack.share.CharacterStats;

import com.sun.sgs.example.hack.share.CreatureInfo.CreatureType;


/**
 * The is the <code>Character</code> interface. All interactive things
 * in a game (players, NPCs, and monsters) are
 * <code>Character</code>s.
 */
public interface Character {

    /**
     * Returns this entity's creature type.  Typically this maps to
     * the sprite used on the client-side to render this entity.
     *
     * @return the identifier
     */
    public CreatureType getCreatureType();

    /**
     * Returns the unique Id for this character
     */
    public long getCharacterId();

    /**
     * Returns the name of this entity.
     *
     * @return the name
     */
    public String getName();

    /**
     * Returns the statistics associated with this character.
     *
     * @return the character's statistics
     */
    public CharacterStats getStatistics();

    /**
     * This method tells the character that their stats have changed. This
     * could be the result of any number of actions, but is often the
     * result of a retaliatory attack.
     */
    public void notifyStatsChanged();

    /**
     * Called when the given character collides with us. This typically
     * uses a double-dispatch model where we call back the other
     * character through their <code>collidedInto</code> method.
     *
     * @param character the character that collided with us
     *
     * @return the result of processing our interaction
     */
    public ActionResult collidedFrom(Character character);

    /**
     * Called when you collide with the character.
     *
     * @param character the character that we collided with
     *
     * @return true if this caused our stats to change, false otherwise
     */
    public boolean collidedInto(Character character);

    /**
     * Sends a text message to the character's manager.
     *
     * @param message the message to send
     */
    public void sendMessage(String message);

}
