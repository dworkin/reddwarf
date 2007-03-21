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
 * GameChannelListener.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Mar 16, 2006	 9:24:30 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client;

import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import java.nio.ByteBuffer;

import java.util.Collections;
import java.util.Map;


/**
 * This abstract class is the base for all game-specific listeners.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public abstract class GameChannelListener implements ClientChannelListener
{

    // the chat listener that accepts all incoming chat messages
    private ChatListener chatListener;

    /**
     * Creates an instance of <code>GameChannelListener</code>.
     */
    protected GameChannelListener(ChatListener chatListener) {
        this.chatListener = chatListener;
    }

    /**
     * A new player has joined the channel this listener is registered on.
     *
     * @param playerID The ID of the joining player.
     */
    public void playerJoined(byte[] playerID) {
        try {
            chatListener.playerJoined(new StatisticalUUID(playerID));
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
            chatListener.playerLeft(new StatisticalUUID(playerID));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Notifies this listener that a chat message arrived from the
     * given player.
     *
     * @param playerID the player's identifier
     * @param data the chat message
     */
    protected void notifyChatMessage(byte [] playerID, ByteBuffer data) {
        try {
            byte [] bytes = new byte[data.remaining()];
            data.get(bytes);
            String message = new String(bytes);
            chatListener.messageArrived(new StatisticalUUID(playerID), message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Notifies this listener of new user identifier mappings.
     *
     * @param data encoded mapping from user identifier to string
     */
    protected void addUidMappings(ByteBuffer data) throws IOException {
        @SuppressWarnings("unchecked")
        Map<SGSUUID,String> map = (Map<SGSUUID,String>)(getObject(data));
        chatListener.addUidMappings(map);
    }

    /**
     * Retrieves a serialized object from the given buffer.
     *
     * @param data the encoded object to retrieve
     */
    protected Object getObject(ByteBuffer data) throws IOException {
        try {
            byte [] bytes = new byte[data.remaining()];
            data.get(bytes);

            ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bin);
            return ois.readObject();
        } catch (ClassNotFoundException cnfe) {
            throw new IOException(cnfe.getMessage());
        }
    }

}
