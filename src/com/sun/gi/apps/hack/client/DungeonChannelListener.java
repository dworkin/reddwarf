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

import com.sun.gi.comm.routing.UserID;

import com.sun.gi.comm.users.client.ClientChannelListener;

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
 *
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class DungeonChannelListener implements ClientChannelListener
{

    //
    private BoardListener blistener;

    //
    private ChatListener clistener;

    //
    private PlayerListener plistener;

    /**
     *
     */
    public DungeonChannelListener(BoardListener blistener,
                                  ChatListener clistener,
                                  PlayerListener plistener) {
        this.blistener = blistener;
        this.clistener = clistener;
        this.plistener = plistener;
    }

    /**
     * A new player has joined the channel this listener is registered on.
     *
     * @param playerID The ID of the joining player.
     */
    public void playerJoined(byte[] playerID) {
        try {
            clistener.playerJoined(new UserID(playerID));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * A player has left the channel this listener is registered on.
     *
     * @param playerID The ID of the leaving player.
     */
    public void playerLeft(byte[] playerID) {
        try {
            clistener.playerLeft(new UserID(playerID));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * A packet has arrived for this listener on this channel.
     *
     * @param from     the ID of the sending player.
     * @param data     the packet data
     * @param reliable true if this packet was sent reliably
     */
    public void dataArrived(byte[] from, ByteBuffer data, boolean reliable) {
        // FIXME: what we should do at this point is figure out if it's
        // from the server or another client, and act on this...
        // ...for now, assume it's just chat data

        if (Arrays.equals(from, Client.SERVER_UID)) {
            int command = (int)(data.get());
            switch (command) {
            case 0: {
                try {
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    java.io.ByteArrayInputStream bin =
                        new java.io.ByteArrayInputStream(bytes);
                    java.io.ObjectInputStream ois =
                        new java.io.ObjectInputStream(bin);
                    Map<UserID,String> uidMap =
                        (Map<UserID,String>)(ois.readObject());
                    clistener.addUidMappings(uidMap);
                } catch (Exception e) {
                    System.out.println("Object stuff failed");
                    e.printStackTrace();
                }
                break;
            }
            case 1: {
                int spriteSize = data.getInt();
                try {
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    java.io.ByteArrayInputStream bin =
                        new java.io.ByteArrayInputStream(bytes);
                    java.io.ObjectInputStream ois =
                        new java.io.ObjectInputStream(bin);
                    Map<Integer,byte[]> spriteMap =
                        (Map<Integer,byte[]>)(ois.readObject());
                    blistener.setSpriteMap(spriteSize, convertMap(spriteMap));
                } catch (Exception e) {
                    System.out.println("Object stuff failed");
                    e.printStackTrace();
                }
                break;
            }
            case 2:
                try {
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    java.io.ByteArrayInputStream bin =
                        new java.io.ByteArrayInputStream(bytes);
                    java.io.ObjectInputStream ois =
                        new java.io.ObjectInputStream(bin);
                    com.sun.gi.apps.hack.share.Board board =
                        (com.sun.gi.apps.hack.share.Board)(ois.readObject());
                    blistener.changeBoard(board);
                } catch (Exception e) {
                    System.out.println("Object stuff failed");
                    e.printStackTrace();
                }
                break;
            case 3:
                try {
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    java.io.ByteArrayInputStream bin =
                        new java.io.ByteArrayInputStream(bytes);
                    java.io.ObjectInputStream ois =
                        new java.io.ObjectInputStream(bin);
                    Collection<BoardSpace> spaces =
                        (Collection<BoardSpace>)(ois.readObject());
                    BoardSpace [] s = new BoardSpace[spaces.size()];
                    blistener.updateSpaces(spaces.toArray(s));
                } catch (Exception e) {
                    System.out.println("Object stuff failed");
                    e.printStackTrace();
                }
                break;
            case 4: {
                byte [] bytes = new byte[data.remaining()];
                data.get(bytes);
                String message = new String(bytes);
                blistener.hearMessage(message);
                break;
            }
            case 64:
                System.out.println("got character data");
                int id = data.getInt();
                try {
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    java.io.ByteArrayInputStream bin =
                        new java.io.ByteArrayInputStream(bytes);
                    java.io.ObjectInputStream ois =
                        new java.io.ObjectInputStream(bin);
                    CharacterStats stats =
                        (CharacterStats)(ois.readObject());
                    plistener.setCharacter(id, stats);
                } catch (Exception e) {
                    System.out.println("Object stuff failed");
                    e.printStackTrace();
                }
                break;
            default:
                System.out.println("Unexpected command!");
            }
        } else {
            try {
                // for now, we assume this is the client
                byte [] bytes = new byte[data.remaining()];
                data.get(bytes);
                clistener.messageArrived(new UserID(from), new String(bytes));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     *
     */
    private Map<Integer,Image> convertMap(Map<Integer,byte[]> map) {
        Map<Integer,Image> newMap = new HashMap<Integer,Image>();

        try {
            for (int identifier : map.keySet()) {
                ByteArrayInputStream in =
                    new ByteArrayInputStream(map.get(identifier));
                newMap.put(identifier, ImageIO.read(in));
            }
        } catch (IOException ioe) {
            System.out.println("Failed to convert images");
            ioe.printStackTrace();
        }

        return newMap;
    }

    /**
     * Notifies this listener that the channel has been closed.
     */
    public void channelClosed() {
        
    }

}
