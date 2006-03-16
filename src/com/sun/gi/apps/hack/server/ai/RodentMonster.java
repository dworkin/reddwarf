/*
 Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 Clara, California 95054, U.S.A. All rights reserved.
 
 Sun Microsystems, Inc. has intellectual property rights relating to
 technology embodied in the product that is described in this document.
 In particular, and without limitation, these intellectual property rights
 may include one or more of the U.S. patents listed at
 http://www.sun.com/patents and one or more additional patents or pending
 patent applications in the U.S. and in other countries.
 
 U.S. Government Rights - Commercial software. Government users are subject
 to the Sun Microsystems, Inc. standard license agreement and applicable
 provisions of the FAR and its supplements.
 
 This distribution may include materials developed by third parties.
 
 Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered
 trademarks of Sun Microsystems, Inc. in the U.S. and other countries.
 
 UNIX is a registered trademark in the U.S. and other countries, exclusively
 licensed through X/Open Company, Ltd.
 
 Products covered by and information contained in this service manual are
 controlled by U.S. Export Control laws and may be subject to the export
 or import laws in other countries. Nuclear, missile, chemical biological
 weapons or nuclear maritime end uses or end users, whether direct or
 indirect, are strictly prohibited. Export or reexport to countries subject
 to U.S. embargo or to entities identified on U.S. export exclusion lists,
 including, but not limited to, the denied persons and specially designated
 nationals lists is strictly prohibited.
 
 DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS,
 REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT,
 ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE
 LEGALLY INVALID.
 
 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 California 95054, Etats-Unis. Tous droits réservés.
 
 Sun Microsystems, Inc. détient les droits de propriété intellectuels
 relatifs à la technologie incorporée dans le produit qui est décrit dans
 ce document. En particulier, et ce sans limitation, ces droits de
 propriété intellectuelle peuvent inclure un ou plus des brevets américains
 listés à l'adresse http://www.sun.com/patents et un ou les brevets
 supplémentaires ou les applications de brevet en attente aux Etats -
 Unis et dans les autres pays.
 
 Cette distribution peut comprendre des composants développés par des
 tierces parties.
 
 Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique
 ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans
 d'autres pays.
 
 UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
 licenciée exlusivement par X/Open Company, Ltd.
 
 see above Les produits qui font l'objet de ce manuel d'entretien et les
 informations qu'il contient sont regis par la legislation americaine en
 matiere de controle des exportations et peuvent etre soumis au droit
 d'autres pays dans le domaine des exportations et importations.
 Les utilisations finales, ou utilisateurs finaux, pour des armes
 nucleaires, des missiles, des armes biologiques et chimiques ou du
 nucleaire maritime, directement ou indirectement, sont strictement
 interdites. Les exportations ou reexportations vers des pays sous embargo
 des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion
 d'exportation americaines, y compris, mais de maniere non exclusive, la
 liste de personnes qui font objet d'un ordre de ne pas participer, d'une
 facon directe ou indirecte, aux exportations des produits ou des services
 qui sont regi par la legislation americaine en matiere de controle des
 exportations et la liste de ressortissants specifiquement designes, sont
 rigoureusement interdites.
 
 LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
 DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES,
 DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE
 GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE
 UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.
*/


/*
 * RodentMonster.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Mar  9, 2006	11:35:56 AM
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
 * This is an implementation of <code>MonsterCharacter</code> that supports
 * behavior for a rodent. It scurries around, occasionally attacking, but
 * never doing substantial damage.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class RodentMonster extends MonsterCharacter
{

    // our statistics
    private CharacterStats stats;

    /**
     * Creates an instance of <code>RodentMonster</code> with the default
     * identifier.
     *
     * @param mgrRef a reference to the manager
     */
    public RodentMonster(GLOReference<AICharacterManager> mgrRef) {
        this(59, mgrRef);
    }

    /**
     * Creates an instance of <code>RodentMonster</code>.
     *
     * @param id the rodent's identifier
     * @param mgrRef a reference to the manager
     */
    public RodentMonster(int id, GLOReference<AICharacterManager> mgrRef) {
        super(id, "rodent", mgrRef);

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
     * @param the character we hit
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
        stats = new CharacterStats("rodent", NSidedDie.roll6Sided(),
                                   NSidedDie.roll6Sided(),
                                   8 + NSidedDie.roll6Sided(),
                                   NSidedDie.roll4Sided(),
                                   8 + NSidedDie.roll6Sided(),
                                   4 + NSidedDie.roll6Sided(),
                                   6 + NSidedDie.roll6Sided(), 12);
    }

}
