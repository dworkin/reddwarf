/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.hack.server.ai;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.Character;
import com.sun.sgs.example.hack.server.CharacterManager;
import com.sun.sgs.example.hack.server.NSidedDie;

import com.sun.sgs.example.hack.server.level.Level;
import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.KeyMessages;

import java.io.Serializable;


/**
 * This is an implementation of <code>MonsterCharacter</code> that supports
 * behavior for a rodent. It scurries around, occasionally attacking, but
 * never doing substantial damage.
 */
public class RodentMonster extends MonsterCharacter implements Serializable {

    private static final long serialVersionUID = 1;

    // our statistics
    private CharacterStats stats;

    /**
     * Creates an instance of <code>RodentMonster</code> with the default
     * identifier.
     *
     * @param mgrRef a reference to the manager
     */
    public RodentMonster(AICharacterManager mgr) {
        this(59, mgr);
    }

    /**
     * Creates an instance of <code>RodentMonster</code>.
     *
     * @param id the rodent's identifier
     * @param mgrRef a reference to the manager
     */
    public RodentMonster(int id, AICharacterManager mgr) {
        super(id, "rodent", mgr);

        regenerate();
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
     * Called when a character collides into us. There is some chance that
     * we will retaliate.
     *
     * @param character the character that hit us
     *
     * @return <code>ActionResult.FAIL</code>
     */
    public ActionResult collidedFrom(Character character) {
        // let them interact with us first
        if (character.collidedInto(this))
            notifyStatsChanged();

        // if we're still alive, there's a small chance that we'll strike
        // back at the attacker
        if(stats.getHitPoints() > 0) {
            if (NSidedDie.roll8Sided() == 8) {
                attack(character);
                character.notifyStatsChanged();
            }
        }

        return ActionResult.FAIL;
    }

    /**
     * Called when you collide with the character.
     *
     * @param character the character we hit
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
        int damage = NSidedDie.roll4Sided();
        CharacterStats theirStats = character.getStatistics();
        int newHp = (damage > theirStats.getHitPoints()) ? 0 :
            theirStats.getHitPoints() - damage;
        theirStats.setHitPoints(newHp);
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
     * There is a good chance that we'll move 
     */
    public void run() {
        // there's a 5-in-6 chance that we'll decide to move
        if (NSidedDie.roll6Sided() != 6) {
            // get the level we're on now
            AICharacterManager mgr = getManager();
            Level level = mgr.getCurrentLevel();

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
        stats = new CharacterStats("rodent", NSidedDie.roll6Sided(),
                                   NSidedDie.roll6Sided(),
                                   8 + NSidedDie.roll6Sided(),
                                   NSidedDie.roll4Sided(),
                                   8 + NSidedDie.roll6Sided(),
                                   4 + NSidedDie.roll6Sided(),
                                   6 + NSidedDie.roll6Sided(), 12);
    }

}
