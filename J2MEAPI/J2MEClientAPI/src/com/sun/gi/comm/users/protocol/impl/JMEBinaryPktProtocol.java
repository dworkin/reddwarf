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

package com.sun.gi.comm.users.protocol.impl;

import com.sun.gi.comm.users.client.impl.JMEClientManager;
import com.sun.gi.utils.jme.ByteBuffer;
import java.io.*;
import com.sun.gi.utils.jme.Callback;
import com.sun.gi.utils.jme.UnsupportedCallbackException;

public class JMEBinaryPktProtocol {

    private static final boolean TRACEON = false;
    private JMEClientManager client;
    private JMEHttpTransportProtocolTransmitter xmitter;
    
    private final static int SEND_MULTICAST = 0; // client to server multicast msg
    private final static int RCV_MULTICAST = 1; // serv to client multicast msg
    private final static int SEND_BROADCAST = 2; // client to server broadcast msg
    private final static int RCV_BROADCAST = 3;  // serv to client broadcast msg
    private final static int SEND_UNICAST = 4; // client to server unicast msg
    private final static int RCV_UNICAST = 5; // server to client unicast msg
    private final static int SEND_SERVER_MSG = 6; // client to GLE
    private final static int CONNECT_REQ = 7; // client to server login
    private final static int RECONNECT_REQ = 8; //client to server fail-over login
    private final static int DISCONNECT_REQ = 9; //client to server logout request
    private final static int VALIDATION_REQ = 10; // server to client req for validation cb data
    private final static int VALIDATION_RESP = 11; // client to server validation cb data
    private final static int USER_ACCEPTED = 12; // server to client successful login
    private final static int USER_REJECTED = 13; //server to client failed login
    private final static int USER_JOINED = 14; // server to client notification of user logging in
    private final static int USER_LEFT = 15; //server to client notification of user logging out
    private final static int USER_DISCONNECTED = 16; //this user is being disconnected
    private final static int USER_JOINED_CHAN = 17; // server to client notification of other user joining
    private final static int USER_LEFT_CHAN = 18; //server to client notification of other user leaving channel
    private final static int RCV_RECONNECT_KEY = 19; //server to client reconnect key notification
    private final static int REQ_JOIN_CHAN = 20;  // client to server request to join a channel
    private final static int JOINED_CHAN = 21; // Server to client notification of user joining a channel
    private final static int REQ_LEAVE_CHAN = 22; // client to server req to leave a channel
    private final static int LEFT_CHAN = 23; // server to client notification of user leaving a channel
    private final static int SERVER_ID = 24; // used to send the bit pattern to identify a packet from the GLE
    /**
     * This is the default constructor
     *
     */
    
    public JMEBinaryPktProtocol(){
        
        xmitter = new JMEHttpTransportProtocolTransmitter();
        
    }
    
    public void setGameName(String gameName) {
        xmitter.setGameName(gameName);
        
    }
    
    public void setPollInterval(long interval) {
        xmitter.setPollInterval(interval);
    }
    
    public void setHost(String host) {
        xmitter.setHost(host);
        
    }
    
    public void setPort(String port) {
        xmitter.setPort(port);
        
    }
    public void deliverServerID(byte[] server_userid){
        if (TRACEON){
            System.out.println("sending server userID to client");
        }
        ByteBuffer hdr = ByteBuffer.allocate(2048);
        hdr.put((byte)SERVER_ID);
        hdr.put((byte) server_userid.length);
        hdr.put(server_userid);
        sendBuffers(hdr);
        
    }
    
    
    /**
     * Call this method from the client to indicate that the user wishes to join a channel
     */
    
    public void sendJoinChannelReq(String chanName) {
        ByteBuffer hdr = ByteBuffer.allocate(2048);
        hdr.put((byte)REQ_JOIN_CHAN);
        byte[] namebytes = chanName.getBytes();
        hdr.put((byte) namebytes.length);
        hdr.put(namebytes);
        sendBuffers(hdr);
        
    }
    
    
    
    /**
     * Call this method from the client to send a unicast message
     */
    
    public void sendUnicastMsg(byte[] chanID,
            byte[] to,
            ByteBuffer data) {
        if (TRACEON){
            System.out.println("Unicast Sending data of size: " + data.position());
        }
        ByteBuffer hdr = ByteBuffer.allocate(2048);
        ByteBuffer[] sendArray = new ByteBuffer[2];
        hdr.put((byte)SEND_UNICAST);
        hdr.put( (byte)1);
        hdr.put((byte) chanID.length);
        hdr.put(chanID);
        hdr.put( (byte) to.length);
        hdr.put(to);
        //set the position to the end so that we can figure out how big it is
        data.position(data.capacity());
        sendArray[0] = hdr;
        sendArray[1] = data;        
        sendBuffers(sendArray);
        
    }
    
    
    
    /**
     * Call this method from the client to send a multicast message
     */
    
    public void sendMulticastMsg(byte[] chanID,
            byte[][] to,
            ByteBuffer data) {
        if (TRACEON){
            System.out.println("Multicast Sending data of size: " + data.position());
        }
        ByteBuffer hdr = ByteBuffer.allocate(2048);
        ByteBuffer[] sendArray = new ByteBuffer[2];
        hdr.put((byte)SEND_MULTICAST);
        hdr.put( (byte)1);
        hdr.put((byte)chanID.length);
        hdr.put(chanID);
        hdr.put( (byte) to.length);
        for (int i = 0; i < to.length; i++) {
            hdr.put( (byte) (to[i].length));
            hdr.put(to[i]);
        }
        //set the position to the end so that we can figure out how big it is
        data.position(data.capacity());
        sendArray[0] = hdr;
        sendArray[1] = data;
        sendBuffers(sendArray);
    }
    
    
    
    /**
     * Call this method from the client to send a multicast message
     */
    
    public void sendBroadcastMsg(byte[] chanID,
            ByteBuffer data) {
        ByteBuffer hdr = ByteBuffer.allocate(2048);
        ByteBuffer[] sendArray = new ByteBuffer[2];
        hdr.put((byte)SEND_BROADCAST);
        hdr.put( (byte)1);
        hdr.put((byte)chanID.length);
        hdr.put(chanID);
        //set the position to the end of the buffer
        //we use this to know how much data is actually in the buffer
        data.position(data.capacity());
        sendArray[0] = hdr;
        sendArray[1] = data;
        sendBuffers(sendArray);
    }
    
    
    public void sendServerMsg(ByteBuffer data) {
        ByteBuffer hdr = ByteBuffer.allocate(2048);
        ByteBuffer[] sendArray = new ByteBuffer[2];
        hdr.put((byte)SEND_SERVER_MSG);
        hdr.put( (byte)1);
        //set the position to the end so that we can figure out how big it is
        data.position(data.capacity());
        sendArray[0] = hdr;
        sendArray[1] = data;
        sendBuffers(sendArray);
    }
    
    /**
     * Call this method from the client to start a login
     */
    
    public void sendLoginRequest() {
        ByteBuffer hdr = ByteBuffer.allocate(2048);
        hdr.put((byte)CONNECT_REQ);
        sendBuffers(hdr);
    }
    
    
    
    
    /**
     * Call this method from the client to attempt a fail-over reconnect
     */
    
    public void sendReconnectRequest(byte[] from,
            byte[] reconnectionKey) throws
            IOException {
        ByteBuffer hdr = ByteBuffer.allocate(2048);
        hdr.put((byte)RECONNECT_REQ);
        hdr.put((byte)from.length);
        hdr.put(from);
        hdr.put((byte)reconnectionKey.length);
        hdr.put(reconnectionKey);
        sendBuffers(hdr);
        
    }
    
    
    
    /**
     * Call this method from the client to send fileld out validation callbacks to the server
     */
    
    public void sendValidationResponse(Callback[] cbs) throws UnsupportedCallbackException {
        ByteBuffer hdr = ByteBuffer.allocate(2048);
        ByteBuffer[] sendArray = new ByteBuffer[2];
        hdr.put((byte)VALIDATION_RESP);
        JMEValidationDataProtocol.makeRequestData(hdr, cbs);
        sendBuffers(hdr);
    }
    
    /**
     * Call this method from the client to leave a channel
     */
    
    public void sendLeaveChannelReq(byte[] chanID) throws IOException {
        ByteBuffer hdr = ByteBuffer.allocate(2048);       
        hdr.put((byte)REQ_LEAVE_CHAN);
        hdr.put((byte)chanID.length);
        sendBuffers(hdr);
        
    }
    
    
    
    private void sendBuffers(ByteBuffer buff){
        xmitter.sendBuffers(new ByteBuffer[] {buff});
    }
    
    private void sendBuffers(ByteBuffer[] buffs){
        xmitter.sendBuffers(buffs);
    }
    
    /**
     * packetReceived
     *
     * @param conn NIOTCPConnection
     * @param inputBuffer ByteBuffer
     */
    public void packetReceived(ByteBuffer buff) {
        int op = buff.get();
        if (TRACEON){
            System.out.println("Recieved op: " + op);
            //System.out.println("DataSize: " + buff.remaining());
        }
        switch (op) {
            
            case RCV_UNICAST:
                boolean reliable = (buff.get() == 1);
                byte chanIDlen = buff.get();
                byte[] chanID = new byte[chanIDlen];
                buff.get(chanID);
                int fromlen = buff.get();
                byte[] from = new byte[fromlen];
                buff.get(from);
                byte tolen = buff.get();
                byte[] to = new byte[tolen];
                buff.get(to);
                ByteBuffer databuff = buff.slice();
                client.rcvUnicastMsg(chanID, from, to, databuff);
                break;
            case SEND_BROADCAST:
                reliable = (buff.get() == 1);
                chanIDlen = buff.get();
                chanID = new byte[chanIDlen];
                buff.get(chanID);
                databuff = buff.slice();
                //server.rcvBroadcastMsg(reliable, chanID,  databuff);
            case RCV_MULTICAST:
                reliable = (buff.get() == 1);
                chanIDlen = buff.get();
                chanID = new byte[chanIDlen];
                buff.get(chanID);
                fromlen = buff.get();
                from = new byte[fromlen];
                buff.get(from);
                byte tocount = buff.get();
                byte[][] tolist = new byte[tocount][];
                for (int i = 0; i < tocount; i++) {
                    tolen = buff.get();
                    tolist[i] = new byte[tolen];
                    buff.get(tolist[i]);
                }
                databuff = buff.slice();
                client.rcvMulticastMsg(chanID, from, tolist, databuff);
                break;
                
            case RCV_BROADCAST:
                reliable = (buff.get() == 1);
                chanIDlen = buff.get();
                chanID = new byte[chanIDlen];
                buff.get(chanID);
                fromlen = buff.get();
                from = new byte[fromlen];
                buff.get(from);
                databuff = buff.slice();
                client.rcvBroadcastMsg(chanID, from, databuff);
                break;
                
            case VALIDATION_REQ:
                Callback[] cbs = JMEValidationDataProtocol.unpackRequestData(buff);
                client.rcvValidationReq(cbs);
                break;
                
            case USER_ACCEPTED:
                int usrlen = buff.get();
                byte[] user = new byte[usrlen];
                buff.get(user);
                client.rcvUserAccepted(user);
                break;
            case USER_REJECTED:
                int bytelen = buff.getInt();
                byte[] msgbytes = new byte[bytelen];
                buff.get(msgbytes);
                client.rcvUserRejected(new String(msgbytes));
                break;
            case USER_JOINED:
                usrlen = buff.get();
                user = new byte[usrlen];
                buff.get(user);
                client.rcvUserJoined(user);
                break;
            case USER_LEFT:
                usrlen = buff.get();
                user = new byte[usrlen];
                buff.get(user);
                client.rcvUserLeft(user);
                break;
            case USER_JOINED_CHAN:
                chanIDlen = buff.get();
                chanID = new byte[chanIDlen];
                buff.get(chanID);
                usrlen = buff.get();
                user = new byte[usrlen];
                buff.get(user);
                client.rcvUserJoinedChan(chanID, user);
                break;
            case USER_LEFT_CHAN:
                chanIDlen = buff.get();
                chanID = new byte[chanIDlen];
                buff.get(chanID);
                usrlen = buff.get();
                user = new byte[usrlen];
                buff.get(user);
                client.rcvUserLeftChan(chanID, user);
                break;
            case JOINED_CHAN:
                chanIDlen = buff.get();
                chanID = new byte[chanIDlen];
                buff.get(chanID);
                int namelen = buff.getInt();
                byte[] namebytes = new byte[namelen];
                buff.get(namebytes);
                client.rcvJoinedChan(new String(namebytes),chanID);
                break;
            case LEFT_CHAN:
                chanIDlen = buff.get();
                chanID = new byte[chanIDlen];
                buff.get(chanID);
                client.rcvLeftChan(chanID);
                break;
            case RCV_RECONNECT_KEY:
                usrlen = buff.get();
                user = new byte[usrlen];
                buff.get(user);
                int keylen = buff.get();
                byte[] key = new byte[keylen];
                buff.get(key);
                long ttl = buff.getLong();
                client.rcvReconnectKey(user,key,ttl);
                break;
                
            case DISCONNECT_REQ:
                break;
            case USER_DISCONNECTED:
                usrlen = buff.get();
                user = new byte[usrlen];
                buff.get(user);
                client.rcvUserDisconnected(user);
                break;
            case SERVER_ID:
                usrlen = buff.get();
                user = new byte[usrlen];
                buff.get(user);
                client.recvServerID(user);
                break;
            default:
                System.out.println("WARNING:Invalid op recieved: " + op +
                        " ignored.");
                break;
        }
    }
    
    
    public void setClient(JMEClientManager client) {
        this.client = client;
        
    }
    
    
    public void sendLogoutRequest() {
        xmitter.closeConnection();
    }
    
    public void stopPolling() {
        xmitter.stopPolling();
    }
    
    
    
    
}
