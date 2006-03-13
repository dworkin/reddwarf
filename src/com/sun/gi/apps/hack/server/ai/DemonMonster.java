/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */


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
