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

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.share.CharacterStats;


/**
 * The creator is where all players can create new characters. It maintains
 * a list of who is currently creating characters, so those players can
 * chat with each other. Beyond this, there is no interactivity, and nothing
 * that the creator game pushes out players.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class Creator implements Game
{

    /**
     * The identifier for the creator
     */
    public static final String IDENTIFIER = NAME_PREFIX + "creator";

    // the channel used for all players currently in the lobby
    private ChannelID channel;

    // the number of players interacting with the creator
    private int playerCount = 0;

    /**
     * Creates an instance of <code>Creator</code>. In practice there should
     * only ever be one of these, so we don't all direct access to the
     * constructor. Instead, you get access through <code>getInstance</code>
     * and that enforces the singleton.
     *
     * @param task the task this is running in
     */
    private Creator(SimTask task) {
        // create a channel for all clients in the creator, but lock it so
        // that we control who can enter and leave the channel
        channel = task.openChannel(IDENTIFIER);
        task.lock(channel, true);
    }

    /**
     * Provides access to the single instance of <code>Creator</code>. If
     * the creator hasn't already been created, then a new instance is
     * created and added as a registered <code>GLO</code>. If the creator
     * already exists then nothing new is created.
     * <p>
     * See the comments in <code>Lobby</code> for details on this pattern.
     *
     * @return a reference to the single <code>Creator</code>
     */
    public static GLOReference<Creator> getInstance() {
        SimTask task = SimTask.getCurrent();

        // try to get an existing reference
        GLOReference<Creator> creatorRef = task.findGLO(IDENTIFIER);

        // if we couldn't find a reference, then create it
        if (creatorRef == null) {
            creatorRef = task.createGLO(new Creator(task), IDENTIFIER);

            // if doing the create returned null then someone beat us to
            // it, so get their already-registered reference
            if (creatorRef == null)
                creatorRef = task.findGLO(IDENTIFIER);
        }

        // return the reference
        return creatorRef;
    }

    /**
     * Joins a player to the creator. This is done when a player logs into
     * the game app for the very first time, and each time that they want
     * to manage their characters.
     *
     * @param player the <code>Player</code> joining the creator
     */
    public void join(Player player) {
        SimTask task = SimTask.getCurrent();

        playerCount++;

        // add the player to the channel
        UserID uid = player.getCurrentUid();
        task.join(uid, channel);

        // NOTE: the idea of this "game" is that it should be used to
        // manage existing characters, create new ones, and delete ones
        // you don't want any more ... for the present, however, it's
        // just used to create characters one at a time, so we don't
        // actually need to send anything to the user now
    }

    /**
     * Removes a player from the creator.
     *
     * @param player the <code>Player</code> leaving the creator
     */
    public void leave(Player player) {
        playerCount--;

        // remove the player from the channel
        SimTask.getCurrent().leave(player.getCurrentUid(), channel);
    }

    /**
     * Creates a new instance of a <code>CreatorMessageHandler</code>.
     *
     * @return a <code>CreatorMessageHandler</code>
     */
    public MessageHandler createMessageHandler() {
        return new CreatorMessageHandler();
    }

    /**
     * Returns the name of the creator. This is also specified by the local
     * field <code>IDENTIFIER</code>.
     *
     * @return the name
     */
    public String getName() {
        return IDENTIFIER;
    }

    /**
     * Returns the number of players currently in the creator.
     *
     * @return the number of players in the creator
     */
    public int numPlayers() {
        return playerCount;
    }

}
