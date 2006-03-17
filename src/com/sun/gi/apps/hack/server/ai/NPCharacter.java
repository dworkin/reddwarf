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
