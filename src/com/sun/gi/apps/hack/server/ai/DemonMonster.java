
/*
 * DemonMonster.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Wed Mar  8, 2006	 1:32:26 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.ai;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.level.Level;
import com.sun.gi.apps.hack.server.level.LevelBoard.ActionResult;

import com.sun.gi.apps.hack.server.Character;
import com.sun.gi.apps.hack.server.CharacterManager;
import com.sun.gi.apps.hack.server.NSidedDie;

import com.sun.gi.apps.hack.share.KeyMessages;

import com.sun.gi.apps.hack.share.CharacterStats;


/**
 * This is an implementation of <code>MonsterCharacter</code> that models
 * a demon creature that is strong, retaliatory, but only somewhat mobile.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class DemonMonster extends MonsterCharacter
{

    // our statistics
    private CharacterStats stats;

    /**
     * Creates an instance of <code>DemonMonster</code> using the default
     * identifier.
     *
     * @param mgrRef a reference to this character's manager
     */
    public DemonMonster(GLOReference<AICharacterManager> mgrRef) {
        this(68, mgrRef);
    }

    /**
     * Creates an instance of <code>DemonMonster</code>.
     *
     * @param id the identifier for this character
     * @param mgrRef a reference to this character's manager
     */
    public DemonMonster(int id, GLOReference<AICharacterManager> mgrRef) {
        super(id, "demon", mgrRef);

        regenerate();
    }

    /**
     * Returns this character's statistics.
     *
     * @return the statistics
     */
    public CharacterStats getStatistics() {
        return stats;
    }

    /**
     * Called when a character collides into us.
     *
     * @param character the character that hit us
     *
     * @return <code>ActionResult.FALSE</code>
     */
    public ActionResult collidedFrom(Character character) {
        // call back the other character to let them take their action...a
        // demon with enough power might be able to pre-empt this attack (in
        // a future version of the code)
        if (character.collidedInto(this))
            notifyStatsChanged();

        // if we're still alive, then retaliate
        if (stats.getHitPoints() > 0) {
            attack(character);
            character.notifyStatsChanged();
        }

        return ActionResult.FAIL;
    }

    /**
     * Called when you collide with the character
     *
     * @param character the character we hit.
     *
     * @return boolean if any statistics changed
     */
    public boolean collidedInto(Character character) {
        attack(character);

        return true;
    }

    /**
     * Private helper that calculates damage we do to another party, and
     * actually inflicts that damage.
     */
    private void attack(Character character) {
        // FIXME: for now, we just extract a bunch of hp
        int damage = NSidedDie.roll10Sided();
        CharacterStats theirStats = character.getStatistics();
        int newHp = (damage > theirStats.getHitPoints()) ? 0 :
            theirStats.getHitPoints() - damage;
        theirStats.setHitPoints(newHp);
    }

    /**
     * Sends a message to this character.
     *
     * @param message the message
     */
    public void sendMessage(String message) {
        // we just ignore these messages
    }

    /**
     * Calls the character to make a move, which may result in moving or
     * attacking.
     */
    public void run() {
        // there's a 1-in-4 chance that we'll decide to move
        if (NSidedDie.roll4Sided() == 4) {
            // get the level we're on now
            SimTask task = SimTask.getCurrent();
            AICharacterManager mgr = getManagerRef().get(task);
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
     * Re-generates the character by creating a new set of statistics.
     */
    public void regenerate() {
        stats = new CharacterStats("demon", 12 + NSidedDie.roll6Sided(),
                                   10 + NSidedDie.roll6Sided(),
                                   10 + NSidedDie.roll6Sided(),
                                   10 + NSidedDie.roll6Sided(),
                                   12 + NSidedDie.roll6Sided(),
                                   6 + NSidedDie.roll6Sided(),
                                   20 + NSidedDie.roll10Sided(), 30);
    }

}
