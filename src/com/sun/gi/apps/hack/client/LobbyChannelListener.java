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
 * LobbyChannelListener.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Feb 26, 2006	 3:42:17 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client;

import com.sun.gi.comm.routing.UserID;

import com.sun.gi.apps.hack.share.CharacterStats;
import com.sun.gi.apps.hack.share.GameMembershipDetail;

import java.nio.ByteBuffer;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import com.sun.gi.comm.users.client.ClientChannelListener;


/**
 *
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class LobbyChannelListener implements ClientChannelListener
{

    //
    private LobbyListener llistener;

    //
    private ChatListener clistener;

    /**
     *
     */
    public LobbyChannelListener(LobbyListener llistener,
                                ChatListener clistener) {
        this.llistener = llistener;
        this.clistener = clistener;
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
        if (Arrays.equals(from, Client.SERVER_UID)) {
            int command = (int)(data.get());
            System.out.println("Lobby Command = " + command);
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
                try {
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    java.io.ByteArrayInputStream bin =
                        new java.io.ByteArrayInputStream(bytes);
                    java.io.ObjectInputStream ois =
                        new java.io.ObjectInputStream(bin);
                    Collection<GameMembershipDetail> details =
                        (Collection<GameMembershipDetail>)(ois.readObject());
                    for (GameMembershipDetail detail : details) {
                        if (! detail.getGame().equals("game:lobby")) {
                            llistener.gameAdded(detail.getGame());
                            llistener.playerCountUpdated(detail.getGame(),
                                                         detail.getCount());
                        } else {
                            llistener.playerCountUpdated(detail.getCount());
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Object stuff failed");
                    e.printStackTrace();
                }
                break; }
            case 2: {
                int count = data.getInt();
                byte [] bytes = new byte[data.remaining()];
                data.get(bytes);
                String name = new String(bytes);

                if (name.equals("game:lobby"))
                    llistener.playerCountUpdated(count);
                else
                    llistener.playerCountUpdated(name, count);
                break; }
            case 3: {
                byte [] bytes = new byte[data.remaining()];
                data.get(bytes);
                llistener.gameAdded(new String(bytes));
                break; }
            case 4: {
                byte [] bytes = new byte[data.remaining()];
                data.get(bytes);
                llistener.gameRemoved(new String(bytes));
                break; }
            case 5: {
                try {
                    byte [] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    java.io.ByteArrayInputStream bin =
                        new java.io.ByteArrayInputStream(bytes);
                    java.io.ObjectInputStream ois =
                        new java.io.ObjectInputStream(bin);
                    Collection<CharacterStats> characters =
                        (Collection<CharacterStats>)(ois.readObject());
                    llistener.setCharacters(characters);
                } catch (Exception e) {
                    System.out.println("Object stuff failed");
                    e.printStackTrace();
                }
                break; }
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
     * Notifies this listener that the channel has been closed.
     */
    public void channelClosed() {
        
    }

}
