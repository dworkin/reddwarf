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
 * DungeonChannelListener.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Feb 26, 2006	12:44:45 AM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client;

import com.sun.gi.apps.hack.share.Board;
import com.sun.gi.apps.hack.share.BoardSpace;
import com.sun.gi.apps.hack.share.CharacterStats;

import java.awt.Image;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import java.nio.ByteBuffer;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.imageio.ImageIO;


/**
 * This class listens for all nessages from a dungeon.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class DungeonChannelListener extends GameChannelListener
{

    // the listener that gets notified on incoming board-related messages
    private BoardListener blistener;

    // the listener that gets notified on incoming player-related messages
    private PlayerListener plistener;

    /**
     * Creates an instance of <code>DungeonChannelListener</code>.
     *
     * @param boardListener the listener for all board messages
     * @param chatListener the listener for all chat messages
     * @param playerListener the listener for all player messages
     */
    public DungeonChannelListener(BoardListener boardListener,
                                  ChatListener chatListener,
                                  PlayerListener playerListener) {
        super(chatListener);

        this.blistener = boardListener;
        this.plistener = playerListener;
    }

    /**
     * Notifies this listener that some data has arrived from a given
     * player. This should only be called with messages that pertain to
     * a dungeon.
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
                    int spriteSize = data.getInt();
                    Map<Integer,byte[]> spriteMap =
                        (Map<Integer,byte[]>)(getObject(data));
                    blistener.setSpriteMap(spriteSize, convertMap(spriteMap));
                break;
                case 2:
                    // we got a complete board update
                    Board board = (Board)(getObject(data));
                    blistener.changeBoard(board);
                break;
                case 3:
                    // we got some selective space updates
                    Collection<BoardSpace> spaces =
                        (Collection<BoardSpace>)(getObject(data));
                    BoardSpace [] s = new BoardSpace[spaces.size()];
                    blistener.updateSpaces(spaces.toArray(s));
                break;
                case 4:
                    // we heard some message from the server
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    String message = new String(bytes);
                    blistener.hearMessage(message);
                    break;
                case 64:
                    // we were sent updated character statistics
                    int id = data.getInt();
                    CharacterStats stats = (CharacterStats)(getObject(data));
                    plistener.setCharacter(id, stats);
                    break;
                default:
                    // FIXME: we should handle this more gracefully
                    System.out.println("Unexpected dungeon message: "
                                       + command);
                }
            } catch (IOException ioe) {
                // FIXME: this should probably handle the error a little more
                // gracefully, but it's unclear what the right approach is
                System.out.println("Failed to handle incoming Dungeon object");
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
     * A private helper that converts the map from the server (that maps
     * integers to byte arrays) into the form needed on the clie (that
     * maps integers to images). The server sends the byte array form
     * because images aren't serializable.
     */
    private Map<Integer,Image> convertMap(Map<Integer,byte[]> map) {
        Map<Integer,Image> newMap = new HashMap<Integer,Image>();

        // for each of the identified sprites, try to load the bytes
        // as a recognizable image format and store in the new map
        for (int identifier : map.keySet()) {
            try {
                ByteArrayInputStream in =
                    new ByteArrayInputStream(map.get(identifier));
                newMap.put(identifier, ImageIO.read(in));
            } catch (IOException ioe) {
                System.out.println("Failed to convert image: " + identifier);
                ioe.printStackTrace();
            }
        }

        return newMap;
    }

    /**
     * Notifies this listener that the channel has been closed.
     */
    public void channelClosed() {
        
    }

}
