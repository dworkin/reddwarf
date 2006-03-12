
/*
 * NPCharacter.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Mon Mar  6, 2006	 9:33:54 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.ai;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.Character;
import com.sun.gi.apps.hack.server.CharacterManager;
import com.sun.gi.apps.hack.server.NSidedDie;

import com.sun.gi.apps.hack.server.level.Level;
import com.sun.gi.apps.hack.server.level.LevelBoard.ActionResult;

import com.sun.gi.apps.hack.share.KeyMessages;

import com.sun.gi.apps.hack.share.CharacterStats;


/**
 * This is an implementation of <code>AICharacter</code> that supports
 * Non-Player Characters. As a simple approximation, NPCs are characters
 * that wander around dungeons, and when hit, say one of a series of
 * phrases.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class NPCharacter extends AICharacter
{

    // the current message they'll say when they're hit
    private int currentMessage;

    // the set of messages the NPC cycles through
    private String [] messages;

    // a reference to our manager
    private GLOReference<AICharacterManager> mgrRef;

    // our stats, which are used only as a placeholder
    private CharacterStats stats;

    /**
     * Creates an instance of <code>NPCharacter</code>.
     *
     * @param id the identifier for this NPC
     * @param name the name for this NPC
     * @param messages the set of messages that this NPC says when hit
     * @param mgrRef a reference to the contolling manager
     */
    public NPCharacter(int id, String name, String [] messages,
                       GLOReference<AICharacterManager> mgrRef) {
        super(id, name);

        this.currentMessage = 0;
        this.messages = messages;
        this.mgrRef = mgrRef;

        this.stats = new CharacterStats(name, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * Returns the statistics associated with this character.
     *
     * @return the character's statistics
     */
    public CharacterStats getStatistics() {
        return stats;
    }

    /**
     * This method tells the character that their stats have changed. We
     * ignore this, since NPCs don't have statistics.
     */
    public void notifyStatsChanged() {
        // we ignore this method
    }

    /**
     * Called when the given character collides with us. Rather than let
     * the other character attack us, we simply say something in response,
     * based on the list of messages. We always return <code>FAIL</code>
     * since we never yield our position
     *
     * @parma character the character that collided with us
     *
     * @return <code>ActionResult.FAIL</code>
     */
    public ActionResult collidedFrom(Character character) {
        character.sendMessage(messages[currentMessage++]);

        // wrap around to the start
        if (currentMessage == messages.length)
            currentMessage = 0;

        return ActionResult.FAIL;
    }

    /**
     * Called when you collide with the character. This always returns false,
     * since we do nothing when we hit other characters.
     *
     * @param character the character that we collided with
     *
     * @return <code>false</code>
     */
    public boolean collidedInto(Character character) {
        return false;
    }

    /**
     * Sends a text message to the character's manager.
     *
     * @param message the message to send
     */
    public void sendMessage(String message) {
        // we just ignore these messages
    }

    /**
     * Called periodically to give this character a chance to walk around
     * the level.
     */
    public void run() {
        // there's a 1-in-2 chance that we'll decide to move
        if (NSidedDie.rollNSided(2) == 1) {
            // get the level we're on now
            SimTask task = SimTask.getCurrent();
            AICharacterManager mgr = mgrRef.get(task);
            Level level = mgr.getCurrentLevel().get(task);

            // pick a direction, and try to move in that direction
            switch (NSidedDie.roll4Sided()) {
            case 1: level.move(mgr, KeyMessages.UP);
                break;
            case 2: level.move(mgr, KeyMessages.DOWN);
                break;
            case 3: level.move(mgr, KeyMessages.LEFT);
                break;
            case 4: level.move(mgr, KeyMessages.RIGHT);
                break;
            }
        }
    }

    /**
     * This has no affect on <code>NPCharacter</code> since NPCs have no
     * meaningful statistics.
     */
    public void regenerate() {
        // we don't need to do anything with this
    }

}
