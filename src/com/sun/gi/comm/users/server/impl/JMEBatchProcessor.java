/*
 * JMEBatchProcessor.java
 *
 * Created on January 10, 2006, 9:00 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.sun.gi.comm.users.server.impl;

import com.sun.gi.comm.users.protocol.TransportProtocol;
import com.sun.gi.comm.users.protocol.TransportProtocolTransmitter;
import com.sun.gi.comm.users.protocol.impl.BinaryPktProtocol;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/**
 *
 * @author as93050
 */
public class JMEBatchProcessor  implements TransportProtocolTransmitter {
    
    private TransportProtocol transportProtocol;
    private SGSUserImpl user;
    private ByteBuffer[] packetsToBeSent;
    /** Creates a new instance of JMEBatchProcessor */
    public JMEBatchProcessor() {
        transportProtocol = new BinaryPktProtocol();
    }
    
    public void setUser(SGSUserImpl user) {
        this.user = user;      
        //this should always happen
        //once the user is created send the server id back to 
        //the client
        if (packetsToBeSent != null) {
            sendBuffers(packetsToBeSent,true);
            packetsToBeSent = null;
        }
    }
        
    public void sendBuffers(ByteBuffer[] buffs,boolean reliable) {
        //the user creation process sends a message back to the user
        //this happens before the user is actually completed and therefore
        //here when we try to send the message we can't get the queue we don't
        //have the user, therefore we need to store the message until the user 
        //is created
        if (user == null) {
            packetsToBeSent = buffs;
        } else {        
            Queue<byte[]> outgoingMessageQueue = ((JMESGSUserImpl)user).getOutgoingMessageQueue();
            byte[] tempPacket = new byte[8096];
            byte[] packetArray;
            byte[] packetToQueue;
            int packetsSize = 0;
            int position = 0;
            for (ByteBuffer packet : buffs) {
                packet.flip();
                int packetSize = packet.remaining();
                packetArray = new byte[packetSize];
                packet.get(packetArray);
                packetsSize += packetSize;
                System.arraycopy(packetArray,0,tempPacket,position,packetSize);            
                position += packetSize;
            }
            packetToQueue = new byte[packetsSize + 2];
            packetToQueue[0] = short1((short)packetsSize);
            packetToQueue[1] = short0((short)packetsSize);
            System.arraycopy(tempPacket,0,packetToQueue,2,packetsSize);            
            outgoingMessageQueue.add(packetToQueue);
        }
    }
    
    private byte short1(short x) { return (byte)(x >>  8); }
    private byte short0(short x) { return (byte)(x >>  0); }
    private int makeShort(byte b1, byte b0) {
	return (int)((((b1 & 0xff) <<  8) |
		      ((b0 & 0xff) <<  0)));
    }
    
    public void closeConnection() {
    }
    
    public void packetsReceived(byte[] data) {
        List<ByteBuffer> packetsReceived = extractPackets(data);
        for (ByteBuffer b : packetsReceived) {
            user.packetReceived(b);
        }
        
    }
    
    /**
     * Because we are doing batch processing, we will receive a bunch of packets
     * from a given client as a byte[]. We need to extract each packet and wrap
     * it into a ByteBuffer before it can be processed.
     * Note: the first byte of every request received by the client will be the
     * number of packets sent. Also note: the first 2 bytes of every packet are it's 
     * size in bytes
     */     
    private List<ByteBuffer> extractPackets(byte[] data) {
        int numberOfPackets = data[0];
        //logout message will be a packet whose size is 0
        if (numberOfPackets == 0) {
            System.out.println("disconnecting");
            user.disconnected();
        }
        int packetSize = 0;
        int position = 1;
        List<ByteBuffer> packetList = new ArrayList<ByteBuffer>();
        for (int i = 0;i < numberOfPackets;i++) {
            //extract the packet size
            byte packetSize1 = data[position++];
            byte packetSize0 = data[position++];
            packetSize = makeShort(packetSize1,packetSize0);
            byte[] packet = new byte[packetSize];
            System.arraycopy(data,position,packet,0,packetSize);
            position += packetSize;
            packetList.add(ByteBuffer.wrap(packet));
        }
        return packetList;
    }
}
