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
 * ChatManager.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Feb 25, 2006	 6:11:10 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client;

import com.sun.gi.comm.routing.UserID;

import com.sun.gi.comm.users.client.ClientChannel;

import java.nio.ByteBuffer;

import java.util.HashSet;
import java.util.Map;


/**
 * This class manages chat communications, acting both as a listener notifier
 * for incoming messages and as a broadcast point for outgoing messages.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class ChatManager implements ChatListener
{

    // the set of listeners
    private HashSet<ChatListener> listeners;

    // the chanel for both directions of communications
    private ClientChannel channel;

    /**
     * Creates a <code>ChatManager</code>.
     */
    public ChatManager() {
        listeners = new HashSet<ChatListener>();
        channel = null;
    }

    /**
     * Sets the channel that is used for incoming and outgoing communication.
     *
     * @param channel the communications channel
     */
    public void setChannel(ClientChannel channel) {
        this.channel = channel;
    }

    /**
     * Adds a listener to the set that will be notified when a message
     * arrives at this manager.
     *
     * @param listener the chat message listener
     */
    public void addChatListener(ChatListener listener) {
        listeners.add(listener);
    }

    /**
     * Sends a broadcast chat message to all participants on the current
     * channel.
     * <p>
     * NOTE: at the moment, this does not accept any user info or other
     * meta-data because it's assumed that the server will provide it all,
     * but this may change.
     *
     * @param message the chat message to send
     */
    public void sendMessage(String message) {
        ByteBuffer bb = ByteBuffer.allocate(message.length());
        bb.put(message.getBytes());
        channel.sendBroadcastData(bb, true);
    }

    /**
     *
     */
    public void playerJoined(UserID uid) {
        for (ChatListener listener : listeners)
            listener.playerJoined(uid);
    }

    /**
     *
     */
    public void playerLeft(UserID uid) {
        for (ChatListener listener : listeners)
            listener.playerLeft(uid);
    }

    /**
     * Notify the listener when a message has arrived.
     *
     * @param sender the id of the sender
     * @param message the messsage itself
     */
    public void messageArrived(UserID sender, String message) {
        for (ChatListener listener : listeners)
            listener.messageArrived(sender, message);
    }

    /**
     *
     */
    public void addUidMappings(Map<UserID,String> uidMap) {
        for (ChatListener listener : listeners)
            listener.addUidMappings(uidMap);
    }

}
