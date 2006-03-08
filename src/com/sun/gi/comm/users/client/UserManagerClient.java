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

package com.sun.gi.comm.users.client;

import java.nio.ByteBuffer;
import java.util.Map;

import javax.security.auth.callback.Callback;

import com.sun.gi.comm.discovery.DiscoveredUserManager;

/**
 * UserManagerClient defines the client side of an SGS UserManager.
 * 
 * The UserManagerClient on the client side, and the UserManager on the
 * server side together encapsulate a transport strategy.
 * 
 * UserManagers are pluggable on the server side on a per-game and a
 * per-slice basis. They announce their presence through the Discovery
 * mechanism and rendezvous with the appropriate UserManagerClient on
 * the client side.
 * 
 * Note that the method descriptions talk in terms of actiosn taken.
 * This is from the point of view of the client side code that calls
 * those methods. In actuality all the UserManagerClient/UserManager
 * pair are responsible for is transmitting the request across the
 * connection. The logic that they plug into handles the details of the
 * actual commands.
 * 
 * @author Jeffrey P. Kesselman
 * @version 1.0
 */
public interface UserManagerClient {

    /**
     * This method is called to initiate connection to the UserManager
     * in the SGS backend. Connection does not imply login. First a
     * UserManagerClient must report a sucessful connection and only
     * then can login be attempted using the login() method below.
     * 
     * @param params a Map of parameters returned from the Discovery
     * system that provide connection settings for a unique instance of
     * a UserManager
     * 
     * @param listener the receiver of communication events
     * 
     * @return true if connection started, false if it fails
     * 
     * @see UserManagerClientListener
     */
    public boolean connect(Map<String, String> params,
            UserManagerClientListener listener);

    /**
     * This method is called to initiate connection to the UserManager
     * in the SGS backend.
     * 
     * @param choice
     * @param listener the receiver of communication events
     * 
     * @return true if connection started, false if it fails
     */
    public boolean connect(DiscoveredUserManager choice,
            UserManagerClientListener listener);

    /**
     * Initiates the login procedure
     */
    public void login();

    /**
     * A login() request may result in a validationRequest to the
     * UserManagerClientListener. This method is used to return the
     * filled out Callback structures to the server for validation.
     * 
     * @param cbs The filled out JAAS Callback structures.
     * 
     * @see UserManagerClientListener
     */
    public void validationDataResponse(Callback[] cbs);

    /**
     * Log the user out of the system and disconnect them from the
     * SGS back-end.
     */
    public void logout();

    /**
     * Send a request to join a channel. (The ClientConnectionManager
     * calls this "opening" a channel. The terms in this case are
     * synonymous.)
     * 
     * @param channelName The name of the channel to open.
     */
    public void joinChannel(String channelName);

    /**
     * Send a request to leave a channel.
     * 
     * @param channelID
     */
    public void leaveChannel(byte[] channelID);

    /**
     * Send a data packet to the game installed in the SGS
     * back-end. It will be handled by whatever Game Logic Object (GLO)
     * has been registered to handle data packets arriving from this
     * particualr user.
     * 
     * @param buff the data itself.
     * @param reliable
     */
    public void sendToServer(ByteBuffer buff, boolean reliable);

    /**
     * Send a packet to another user on the given channel
     * 
     * @param chanID The comm channel to put the packet on
     * @param to The user the apcket is destined for
     * @param data The packet itself
     * @param reliable Whether delivery gaurantees are required
     */
    public void sendUnicastMsg(byte[] chanID, byte[] to, ByteBuffer data,
            boolean reliable);

    /**
     * Send a packet to a list of users on the given channel
     * 
     * @param chanID The comm channel to put the packet on
     * @param to An array of user IDs that the packet is destined for
     * @param data The packet itself
     * @param reliable Whether delivery gaurantees are required
     */
    public void sendMulticastMsg(byte[] chanID, byte[][] to, ByteBuffer data,
            boolean reliable);

    /**
     * Send a packet to all other users on the given channel
     * 
     * @param chanID The comm channel to put the packet on
     * @param data The packet itself
     * @param reliable Whether delivery gaurantees are required
     */
    public void sendBroadcastMsg(byte[] chanID, ByteBuffer data,
            boolean reliable);

    /**
     * Reconnect after having been dropped from a connection point.
     * (This could be due to slice failure or rebalancing of load.) This
     * method allows a user to reconnect and skip the validation phase
     * so long as their time-limited reconnection key is still valid.
     * 
     * @param userID The ID of the user trying to reconnect.
     * @param reconnectionKey A time-limited key used to revalidate a
     * previously-validated user.
     */
    public void reconnectLogin(byte[] userID, byte[] reconnectionKey);
}
