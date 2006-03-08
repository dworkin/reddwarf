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

package com.sun.gi.comm.users.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;

public interface TransportProtocol {

    public void packetReceived(ByteBuffer buff);

    public void sendLoginRequest() throws IOException;

    public void sendLogoutRequest() throws IOException;

    /**
     * Call this method from the client to send a unicast message
     */
    public void sendUnicastMsg(byte[] chanID, byte[] to, boolean reliable,
            ByteBuffer data) throws IOException;

    /**
     * Call this method from the server to deliver a unicast message to
     * the client
     */
    public void deliverUnicastMsg(byte[] chanID, byte[] from, byte[] to,
            boolean reliable, ByteBuffer data) throws IOException;

    /**
     * Call this method from the client to send a multicast message
     */
    public void sendMulticastMsg(byte[] chanID, byte[][] to, boolean reliable,
            ByteBuffer data) throws IOException;

    /**
     * Call this method from the server to deliver a multicast message
     * to the client
     */
    public void deliverMulticastMsg(byte[] chanID, byte[] from, byte[][] to,
            boolean reliable, ByteBuffer data) throws IOException;

    public void sendServerMsg(boolean reliable, ByteBuffer data)
            throws IOException;

    /**
     * Call this method from the client to send a multicast message
     */
    public void sendBroadcastMsg(byte[] chanID, boolean reliable,
            ByteBuffer data) throws IOException;

    /**
     * Call this method from the server to deliver a multicast message
     * to the client
     */
    public void deliverBroadcastMsg(byte[] chanID, byte[] from,
            boolean reliable, ByteBuffer data) throws IOException;

    /**
     * Call this method from the server to indcate successful login
     */
    public void deliverUserAccepted(byte[] newID) throws IOException;

    /**
     * Call this method from the server to indcate login failure
     */
    public void deliverUserRejected(String message) throws IOException;

    /**
     * 
     * Call this method from the client to attempt a fail-over reconnect
     */
    public void sendReconnectRequest(byte[] from, byte[] reconnectionKey)
            throws IOException;

    /**
     * Call this method from the server to request validation callback
     * information
     */
    public void deliverValidationRequest(Callback[] cbs)
            throws UnsupportedCallbackException, IOException;

    /**
     * Call this method from the client to send fileld out validation
     * callbacks to the server
     */
    public void sendValidationResponse(Callback[] cbs)
            throws UnsupportedCallbackException, IOException;

    /**
     * Call this method from the server to notify client of newly logged
     * on user
     */
    public void deliverUserJoined(byte[] user) throws IOException;

    /**
     * Call this method from the server to notify client of newly logged
     * off user
     */
    public void deliverUserLeft(byte[] user) throws IOException;

    /**
     * Call this method from the client to req a user be joiend to a
     * channel
     */
    public void sendJoinChannelReq(String channelName) throws IOException;

    /**
     * Call this method from the server to notify client of user joining
     * channel
     */
    public void deliverUserJoinedChannel(byte[] chanID, byte[] user)
            throws IOException;

    /**
     * Call this method from the server to notify client of itself
     * joining channel
     */
    public void deliverJoinedChannel(String name, byte[] chanID)
            throws IOException;

    /**
     * Call this method from the client to leave a channel
     */
    public void sendLeaveChannelReq(byte[] chanID) throws IOException;

    /**
     * Call this method from the server to notify client of user leaving
     * channel
     */
    public void deliverUserLeftChannel(byte[] chanID, byte[] user)
            throws IOException;

    /**
     * Call this method from the server to notify client of itself
     * leaving channel
     */
    public void deliverLeftChannel(byte[] chanID) throws IOException;

    /**
     * Called when the server notifies the client that a request to
     * join/leave a channel failed due to the channel being locked.
     * 
     * @param channelName the name of the channel.
     * @param user the user
     * 
     * @throws IOException
     */
    public void deliverChannelLocked(String channelName, byte[] user)
            throws IOException;

    /**
     * Call this method from the server to send a reconenct key update
     * to the client
     */
    public void deliverReconnectKey(byte[] id, byte[] key, long ttl)
            throws IOException;

    public void setClient(TransportProtocolClient client);

    public void setServer(TransportProtocolServer server);

    public void setTransmitter(TransportProtocolTransmitter xmitter);

    public void deliverUserDisconnected(byte[] bs) throws IOException;

    public boolean isLoginPkt(ByteBuffer inputBuffer);

    public void deliverServerID(byte[] bs);
}
