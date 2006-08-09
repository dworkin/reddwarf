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

package com.sun.gi.comm.users.client.impl;

import com.sun.gi.comm.discovery.impl.JMEDiscoveredGameImpl;
import com.sun.gi.comm.discovery.impl.JMEDiscoveredUserManagerImpl;
import com.sun.gi.comm.discovery.JMEDiscoverer;
import com.sun.gi.comm.users.client.JMEClientListenerInterface;
import com.sun.gi.comm.users.protocol.impl.JMEBinaryPktProtocol;
import com.sun.gi.comm.users.protocol.impl.JMEHttpTransportProtocolTransmitter;
import com.sun.gi.utils.jme.ByteBuffer;
import com.sun.gi.utils.jme.Callback;
import com.sun.gi.utils.jme.UnsupportedCallbackException;
import java.io.IOException;
import java.util.Random;
import java.util.Vector;

/**
 * Manager for JME Clients. Sends events to the client listener and receives 
 * requests from clients
 * @author as93050
 */
public class JMEClientManager {
    
    private JMEDiscoverer discoverer;
    private JMEBinaryPktProtocol protocol;
    private String gameName;
    private String userManagerClassName;
    private byte[] reconnectionKey = null;
    private boolean connected,reconnecting;
    private JMEClientListenerInterface listener;
    private static JMEClientManager clientManager;
    private long keyTimeout = 0;
    private byte[] myID;
    private Random random;
    private static final Object PRESENT = new Object();
    
    /** Creates a new instance of JMEClientManager */
    public JMEClientManager(String gameName, JMEDiscoverer disco, String userManagerClassName) {
        clientManager = this;
        discoverer = disco;
        this.userManagerClassName = userManagerClassName;
        discoverer.setListener(this);
        this.gameName = gameName;
        protocol = new JMEBinaryPktProtocol();
        protocol.setClient(this);
        random = new Random();
    }
    
    /**
     * Add a listener to receive events from the client manager
     * @param l listener
     */
    public void setListener(JMEClientListenerInterface l) {
        listener = l;
    }
    
    private JMEDiscoveredGameImpl discoverGame(String gameName) {
        JMEDiscoveredGameImpl[] games = discoverer.games();
        for (int i = 0; i < games.length; i++) {
            if (games[i].getName().equals(gameName)) {
                return games[i];
            }
        }
        System.out.println("Discovery Error: No games discovered!");
        return null;
    }
    
    /**
     * Connect to the SGS Server. Note because we are using a connectionless protocol
     * this really means log in to the server.
     */
    public boolean connect() throws IllegalArgumentException {
        JMEDiscoveredGameImpl game = discoverGame(gameName);
        JMEDiscoveredUserManagerImpl[] userManagers = game.getUserManagers();
        JMEDiscoveredUserManagerImpl userManager = null;
        Vector httpUserManagers = new Vector();
        for (int i = 0;i < userManagers.length;i++) {
            //if (userManagers[i].getClientClass().equals(userManagerClassName)) {
            //    userManager = userManagers[i];
            //    break;
            if (userManagers[i].getClientClass().equals(userManagerClassName)) {
                httpUserManagers.addElement(userManagers[i]);
            }
        }
        userManager = getRandomUsrMgr(httpUserManagers);
        if (userManager == null) {
            throw new IllegalArgumentException("No matching user manager found");
        }
        protocol.setGameName(game.getName());
        protocol.setPort(userManager.getParameter("port"));
        protocol.setHost(userManager.getParameter("host"));
        String pollInterval = userManager.getParameter("pollInterval");
        if (pollInterval != null) {
            protocol.setPollInterval(Long.parseLong(pollInterval));
        }
        protocol.sendLoginRequest();
        return true;
    }

    private JMEDiscoveredUserManagerImpl getRandomUsrMgr(final Vector httpUserManagers) {
        int numUsrMgrs = httpUserManagers.size();
        System.out.println("getting random user manager " + numUsrMgrs);
        if (numUsrMgrs == 1) {
            return (JMEDiscoveredUserManagerImpl)httpUserManagers.firstElement();
        } else if (numUsrMgrs > 1) {
            return (JMEDiscoveredUserManagerImpl)httpUserManagers.elementAt(random.nextInt(numUsrMgrs -1));
        }
        return null;
    }
    
    /**
     * Logout from the SGS server see comment related to connect
     */
    public void disconnect() {
        protocol.sendLogoutRequest();
    }
    
    /**
     * Send the response to a validation request
     */
    public void sendValidationResponse(Callback[] cbs) throws UnsupportedCallbackException {
        protocol.sendValidationResponse(cbs);
        
    }
    
    /**
     * Send a broadcast message
     * param channelID the channel id to send the message to
     *
     */
    public void sendBroadcastMessage(byte[] channelID, ByteBuffer buff) {
        
        protocol.sendBroadcastMsg(channelID,buff);
        
    }
    
    /**
     * Open a channel to the server
     * @param channelName the name of the channel to open
     */
    public void openChannel(String channelName) {
        protocol.sendJoinChannelReq(channelName);
        
    }
    
    /**
     * Receive notification that a user logged in
     * @param user the user id that logged in
     */
    protected void userAdded(byte[] userID) {
        listener.userLoggedIn(userID);
    }
    
    /**
     * Not sure what this does
     * @param user
     */
    public void recvServerID(byte[] user) {
        listener.recvServerID(user);
    }
    
    public void dataArrived(ByteBuffer[] data) {
        for (int i = 0;i < data.length;i++) {
            protocol.packetReceived(data[i]);
        }
    }
    
    /**
     * Receive a message from another user
     * @param chanID the channel that the message was sent on
     * @param from the id of the sender
     * @param to the id of the receiver
     * @param databuff the message data
     */
    public void rcvUnicastMsg(byte[] chanID, byte[] from, byte[] to, ByteBuffer databuff) {
        listener.dataReceived(chanID,from,databuff);
    }
    
    /**
     * Receive a mutlicast message from another user
     * @param chanID the channel that the message was sent on
     * @param from the id of the sender
     * @param tolist the ids of all the receivers
     * @param databuff the message data
     */
    public void rcvMulticastMsg(byte[] chanID, byte[] from, byte[][] tolist, ByteBuffer databuff){
        listener.dataReceived(chanID,from,databuff);
    }
    
    /**
     * Receive a broadcast message from another user
     * @param chanID the channel that the message was sent on
     * @param from the id of the sender
     * @param databuff the message data
     */
    public void rcvBroadcastMsg(byte[] chanID, byte[] from, ByteBuffer databuff){
        listener.dataReceived(chanID,from,databuff);
    }
    
    /**
     * Receive a request for validation
     * Note: right now we only support name validation
     * @param cbs the array of callbacks to be filled in
     */
    public void rcvValidationReq(Callback[] cbs){
        listener.validationDataRequest(cbs);
    }
    
    /**
     * The user has successfully logged in
     * @param user the userid
     */
    public void rcvUserAccepted(byte[] user){
        listener.loginAccepted(user);
    }
    
    /**
     * The user logon attempt has been rejected
     * @param message describes why the logon was rejected
     */
    public void rcvUserRejected(String message){
        listener.loginRejected(message);
        protocol.stopPolling();
    }
    
    /**
     * Receive notification that a user logged in
     * @param user the user id that logged in
     */
    public void rcvUserJoined(byte[] user){
        listener.userLoggedIn(user);
    }
    
    /**
     * A user has logged out
     * @param user the user id that logged out
     */
    public void rcvUserLeft(byte[] user){
        listener.userLoggedOut(user);
    }
    
    /**
     * A user joined a channel that we have open
     * @param chanID the channel id
     * @param user the userid
     */
    public void rcvUserJoinedChan(byte[] chanID, byte[] user){
        listener.userJoinedChannel(chanID,user);
    }
    
    /**
     * A user left a channel that we have open
     * @param chanID the channel id
     * @param user the userid
     */
    public void rcvUserLeftChan(byte[] chanID, byte[] user){
        listener.userLeftChannel(chanID,user);
    }
    
    /**
     * Not relevent for JME clients as we have no persistent connection
     */
    public void rcvReconnectKey(byte[] user, byte[] key, long ttl){
    }
    /**
     * We have opened a channel
     * @param chanID the channel id
     */
    public void rcvJoinedChan(String chanName, byte[] chanID){
        listener.joinedChannel(chanName,chanID);
    }
    
    /**
     * We closed a channel
     * @param chanID the channel id
     */
    public void rcvLeftChan(byte[] chanID){
        listener.leftChannel(chanID);
    }
    
    /**
     * We logged out, not relevat for JME as we will never receive this message
     * @param chanID the channel id
     * @param user the userid
     */
    public void rcvUserDisconnected(byte[] userId){
        listener.userLoggedOut(userId);
    }
    
    /**
     * send a message to a specific user
     * @param chanID the channel to send the message on
     * @param to the user to send the message to 
     * @param data the data to send
     */
    public void sendUnicastMsg(byte[] chanID, byte[] to, ByteBuffer data) {
        protocol.sendUnicastMsg(chanID,to,data);
    }
    
    /**
     * send a message to a group of users
     * @param chanID the channel to send the message on
     * @param to the users to send the message to 
     * @param data the data to send
     */
    public void sendMulticastData(byte[] chanID,byte[][] to, ByteBuffer data) {
        protocol.sendMulticastMsg(chanID,to,data);
    }
    
    /**
     * send a message to the server
     * @param data the data to send
     */
    public void sendServerMessage(ByteBuffer data) {
        protocol.sendServerMsg(data);
    }
    /**
     * Goes out to the server and brings back all the available games
     */
    public void discoverGames() {
        discoverer.discoverGames();
    }
    /**
     * callback when we have finished getting the games
     */
    public void discoveredGames() {
        listener.discoveredGames();
    }
    /**
     * Callback if an error occurred when sending or receiving data
     */
    public void exceptionOccurred(Exception ex) {
        listener.exceptionOccurred(ex);
    }
    
    /**
     * Stop the polling thread
     */
    public void stopPolling() {
        protocol.stopPolling();
    }
    
    public static JMEClientManager getClientManager() {
        return clientManager;
    }
    
}
