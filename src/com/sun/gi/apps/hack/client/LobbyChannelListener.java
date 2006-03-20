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
 * LobbyChannelListener.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Feb 26, 2006	 3:42:17 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client;

import com.sun.gi.apps.hack.share.CharacterStats;
import com.sun.gi.apps.hack.share.GameMembershipDetail;

import java.io.IOException;

import java.nio.ByteBuffer;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;


/**
 * This class listens for all messages from the lobby.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class LobbyChannelListener extends GameChannelListener
{

    // the listener that gets notified on incoming messages
    private LobbyListener llistener;

    /**
     * Creates an instance of <code>LobbyListener</code>.
     *
     * @param lobbyListener the listener for all lobby messages
     * @param chatListsner the listener for all chat messages
     */
    public LobbyChannelListener(LobbyListener lobbyListener,
                                ChatListener chatListener) {
        super(chatListener);

        this.llistener = lobbyListener;
    }

    /**
     * Notifies this listener that some data has arrived from a given
     * player. This should only be called with messages that pertain to
     * the lobby.
     *
     * @param from the ID of the sending player.
     * @param data the packet data
     * @param reliable true if this packet was sent reliably
     */
    public void dataArrived(byte[] from, ByteBuffer data, boolean reliable) {
        if (Arrays.equals(from, Client.SERVER_UID)) {
            // if this is a message from the server, then it's some
            // command that we need to process, so get the command code
            int command = (int)(data.get());

            // FIXME: this should really be an enumeration
            try {
                switch (command) {
                case 0:
                    // we got some uid to player name mapping
                    addUidMappings(data);
                    break;
                case 1:
                    // we were sent game membership updates
                    Collection<GameMembershipDetail> details =
                        (Collection<GameMembershipDetail>)(getObject(data));
                    for (GameMembershipDetail detail : details) {
                        // for each update, see if it's about the lobby
                        // or some specific dungeon
                        if (! detail.getGame().equals("game:lobby")) {
                            // it's a specific dungeon, so add the game and
                            // set the initial count
                            llistener.gameAdded(detail.getGame());
                            llistener.playerCountUpdated(detail.getGame(),
                                                         detail.getCount());
                        } else {
                            // it's the lobby, so update the count
                            llistener.playerCountUpdated(detail.getCount());
                        }
                    }
                    break;
                case 2: {
                    // we got a membership count update for some game
                    int count = data.getInt();
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    String name = new String(bytes);
                    
                    // see if it's the lobby or some specific dungeon, and
                    // update the count appropriately
                    if (name.equals("game:lobby"))
                        llistener.playerCountUpdated(count);
                    else
                        llistener.playerCountUpdated(name, count);
                    break; }
                case 3: {
                    // we heard about a new game
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    llistener.gameAdded(new String(bytes));
                    break; }
                case 4: {
                    // we heard that a game was removed
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    llistener.gameRemoved(new String(bytes));
                    break; }
                case 5: {
                    // we got updated with some character statistics...these
                    // are characters that the client is allowed to play
                    Collection<CharacterStats> characters =
                        (Collection<CharacterStats>)(getObject(data));
                    llistener.setCharacters(characters);
                    break; }
                default:
                    // FIXME: we should handle this more gracefully
                    System.out.println("Unexpected lobby message: " + command);
                }
            } catch (IOException ioe) {
                // FIXME: this should probably handle the error a little more
                // gracefully, but it's unclear what the right approach is
                System.out.println("Failed to handle incoming Lobby object");
                ioe.printStackTrace();
            }
        } else {
            // this isn't a message from the server, so it came from some
            // other player on our channel...in this game, that can only
            // mean that we got a chat message
            notifyChatMessage(from, data);
        }
    }

    /**
     * Notifies this listener that the channel has been closed.
     */
    public void channelClosed() {

    }

}
