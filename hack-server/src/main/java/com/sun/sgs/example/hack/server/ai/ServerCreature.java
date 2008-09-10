/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server.ai;

import com.sun.sgs.example.hack.server.util.Dice;

import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.CreatureInfo.Creature;
import com.sun.sgs.example.hack.share.CreatureInfo.CreatureType;
import com.sun.sgs.example.hack.share.CreatureInfo.CreatureAttribute;

import java.util.Set;

/**
 * The server-side interface for {@code Creature} instances.  This
 * interface defines additional methods for specifying how a creature
 * should behave and interact.
 */
public interface ServerCreature extends Creature {

    /**
     * Returns the statistics for this creature.
     */
    CharacterStats getStatistics();
    
    /**
     * Returns the {@code Dice} used to determine attack damage
     */
    Dice getDamageDice();
    
    /**
     * Returns the distance around this creature that is able to be
     * seen.
     */
    int getSightAbility();

    /**
     * Returns how likely this creature is to notice players in its
     * area of sight, and to take action based on their presence.
     */
    int getAlertness();

    /**
     * Returns the delay between movement updates for this creature.
     */
    int getMovementDelay();

    /**
     * Returns the set of attributes for this creature.
     */
    Set<CreatureAttribute> getAttributes();

}