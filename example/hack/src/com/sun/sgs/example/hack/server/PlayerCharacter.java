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

package com.sun.gi.apps.hack.server;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.level.LevelBoard.ActionResult;

import com.sun.gi.apps.hack.share.CharacterStats;

import java.lang.reflect.Method;


/**
 * This is an implementation of <code>Character</code> used by all
 * <code>Player</code>s.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class PlayerCharacter implements Character
{

    // a reference to the player that owns this character
    private GLOReference<Player> playerRef;

    // the id of this character
    private int id;

    // the statistics for this character
    private CharacterStats stats;

    /**
     * Creates an instance of <code>PlayerCharacter</code>.
     *
     * @param playerRef a reference to the <code>Player</code> that owns
     *                  this character
     * @param id the identifier for this character
     * @param stats the statistics for this character
     */
    public PlayerCharacter(GLOReference<Player> playerRef, int id,
                           CharacterStats stats) {
        this.playerRef = playerRef;
        this.id = id;
        this.stats = stats;
    }

    /**
     * Returns this entity's identifier. Typically this maps to the sprite
     * used on the client-side to render this entity.
     *
     * @return the identifier
     */
    public int getID() {
        return id;
    }

    /**
     * Returns the name of this entity.
     *
     * @return the name
     */
    public String getName() {
        return stats.getName();
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
     * This method tells the character that their stats have changed. This
     * notifies the player of the change.
     */
    public void notifyStatsChanged() {
        // send the player our updated stats...
        SimTask task = SimTask.getCurrent();
        Player player = playerRef.peek(task);
        player.sendCharacter(task, this);

        // ...and check to see if we're still alive
        if (stats.getHitPoints() == 0) {
            // we were killed, so send a message...
            player.sendTextMessage(task, "you died.");

            // ...remove ourself directly from the level, so there's no
            // confusion about interacting with us before we get called
            // back to do the removal...
            player.leaveCurrentLevel();

            // FIXME: just for testing, we'll reset the hit-points here
            stats.setHitPoints(stats.getMaxHitPoints());

            // NOTE: we could add some message screen here, if we wanted

            // ...and finally, queue up a leaveGame message to get us back to
            // the lobby
            try {
                GLOReference<Lobby> lobbyRef = task.findGLO(Lobby.IDENTIFIER);
                Method method =
                    Player.class.getMethod("moveToGame", GLOReference.class);
                task.queueTask(playerRef, method, new Object [] {lobbyRef});
            } catch (NoSuchMethodException nsme) {
                throw new IllegalStateException(nsme.getMessage());
            }
        }
    }

    /**
     * Called when a character collides into us. We always call back
     * the other character's <code>collidedInto</code> method, and then
     * check on the result. Since the player is interactive, we don't
     * automatically retaliate (although that could easily be added at
     * this point).
     *
     * @param character the character that collided with us
     *
     * @return the result of processing our interaction
     */
    public ActionResult collidedFrom(Character character) {
        // getting hit invokes a simple double-dispatch pattern, where
        // we call the other party and let them know that they hit us, and
        // then we react to this
        
        // remember out current hp count, and then call the other party
        int previousHP = stats.getHitPoints();
        if (character.collidedInto(this)) {
            // our stats were effected, so see if we lost any hit points
            SimTask task = SimTask.getCurrent();
            Player player = playerRef.peek(task);
            int lostHP = previousHP - stats.getHitPoints();
            if (lostHP > 0)
                player.sendTextMessage(task, character.getName() +
                                       " hit you for " + lostHP + "HP");

            // do the general stat notify routine
            notifyStatsChanged();
        }

        // regardless of what happened, we don't yield our ground yet
        return ActionResult.FAIL;
    }

    /**
     * Called when you collide into the character. This will always try
     * to attack the character.
     *
     * @return boolean if any statistics changed
     */
    public boolean collidedInto(Character character) {
        // FIXME: this isn't trying to use any stats at this point, it's
        // just using some testing values
        int damage = NSidedDie.roll6Sided();
        CharacterStats stats = character.getStatistics();
        int newHp = (damage > stats.getHitPoints()) ? 0 :
            stats.getHitPoints() - damage;
        stats.setHitPoints(newHp);

        return true;
    }

    /**
     * Sends a text message to the character's player.
     *
     * @param message the message to send
     */
    public void sendMessage(String message) {
        SimTask task = SimTask.getCurrent();
        playerRef.peek(task).sendTextMessage(task, message);
    }

}
