/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.example.hack.server.ai;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.example.hack.server.level.Level;
import com.sun.sgs.example.hack.server.level.LevelBoard.ActionResult;

import com.sun.sgs.example.hack.server.Character;
import com.sun.sgs.example.hack.server.CharacterManager;
import com.sun.sgs.example.hack.server.NSidedDie;

import com.sun.sgs.example.hack.share.KeyMessages;

import com.sun.sgs.example.hack.share.CharacterStats;

import java.io.Serializable;


/**
 * This is an implementation of <code>MonsterCharacter</code> that models
 * a demon creature that is strong, retaliatory, but only somewhat mobile.
 */
public class DemonMonster extends MonsterCharacter implements Serializable {

    private static final long serialVersionUID = 1;

    // our statistics
    private CharacterStats stats;

    /**
     * Creates an instance of <code>DemonMonster</code> using the default
     * identifier.
     *
     * @param mgrRef a reference to this character's manager
     */
    public DemonMonster(AICharacterManager mgr) {
        this(68, mgr);
    }

    /**
     * Creates an instance of <code>DemonMonster</code>.
     *
     * @param id the identifier for this character
     * @param mgrRef a reference to this character's manager
     */
    public DemonMonster(int id, AICharacterManager mgr) {
        super(id, "demon", mgr);

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
        stats = new CharacterStats("demon", 12 + NSidedDie.roll6Sided(),
                                   10 + NSidedDie.roll6Sided(),
                                   10 + NSidedDie.roll6Sided(),
                                   10 + NSidedDie.roll6Sided(),
                                   12 + NSidedDie.roll6Sided(),
                                   6 + NSidedDie.roll6Sided(),
                                   20 + NSidedDie.roll10Sided(), 30);
    }

}
