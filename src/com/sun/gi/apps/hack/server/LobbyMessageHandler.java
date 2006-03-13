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
 * LobbyMessageHandler.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Mon Feb 27, 2006	 9:03:01 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import java.lang.reflect.Method;

import java.nio.ByteBuffer;


/**
 * This <code>MessageHandler</code> is used by <code>Lobby</code> to define
 * and handle all messages sent from the client.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class LobbyMessageHandler implements MessageHandler
{

    /**
     * Creates a new <code>LobbyMessageHandler</code>.
     */
    public LobbyMessageHandler() {

    }

    /**
     * Called when the given <code>Player</code> has a message to handle.
     *
     * @param player the <code>Player</code> who received the message
     * @param data the message to handle
     */
    public void handleMessage(Player player, ByteBuffer data) {
        // the command identifier is always stored in the first byte
        int command = (int)(data.get());

        // FIXME: we should use an enum to define the messages
        try {
            switch (command) {
            case 1:
                moveToGame(player, data);
                break;
            }
        } catch (NoSuchMethodException nsme) {
            // FIXME: we should log this, and send the client some kind of
            // generic "internal error occured" message
            nsme.printStackTrace();
        } catch (Exception e) {
            // FIXME: here what we want to do is either log the error, or
            // send back a generic error response
            e.printStackTrace();
        }
    }

    /**
     * Used to handle a message requesting a move to some named game.
     */
    private void moveToGame(Player player, ByteBuffer data)
        throws NoSuchMethodException
    {
        SimTask task = SimTask.getCurrent();

        // get the length of the game name, and use that to get the game
        int gameLen = data.getInt();
        byte [] bytes = new byte[gameLen];
        data.get(bytes);
        String gameName = new String(bytes);

        // now get the character that we're playing as
        bytes = new byte[data.remaining()];
        data.get(bytes);
        String characterName = new String(bytes);
        
        // lookup the game, making sure it exists
        GLOReference<Game> gameRef = task.findGLO(Game.NAME_PREFIX + gameName);

        // set our current player
        if (! player.getCharacterManager().get(task).
            setCurrentCharacter(characterName)) {
            // an invalid character name was provided
            // FIXME: we should handle this better
            System.out.println("Invalid character: " + characterName);
            return;
        }
        
        if (gameRef != null) {
            // get a reference to the player, and queue a task to move them
            // into another game
            // FIXME: would it be safe to just invoke this directly?
            GLOReference<Player> playerRef = player.getReference();
            Method method =
                Player.class.getMethod("moveToGame", GLOReference.class);
            task.queueTask(playerRef, method, new Object [] {gameRef});
        } else {
            // FIXME: we should send back some kind of error
            System.out.println("Couldn't find game: " +
                               Game.NAME_PREFIX + gameName);
        }
    }

}
