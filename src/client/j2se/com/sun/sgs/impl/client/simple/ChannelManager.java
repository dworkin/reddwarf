package com.sun.sgs.impl.client.simple;

import java.util.Collection;
import java.util.HashMap;
import java.util.concurrent.Callable;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.ServerSessionListener;
import com.sun.sgs.client.SessionId;
import com.sun.sgs.impl.client.comm.ClientConnection;

import static com.sun.sgs.impl.client.simple.ProtocolMessage.*;

/**
 * The ChannelManager handles all the channel related functions of the client.
 * It interprets all server messages from the Channel Service, which are 
 * forwarded from SimpleClient.  
 * <p>
 * Specifically, the ChannelManager:
 * <ul>
 * <li>Handles the creation and removal of {@link ClientChannel}s</li>
 * <li>Interprets incoming channel messages from the server and forwards
 * them to the correct {@code ClientChannel}</li>
 * <li>Sends channel messages to the server</li> 
 * </ul>
 * <p>
 * The {@code ChannelManager} keeps track of a sequence number for incoming
 * channel messages.  If a message with a lower or equal sequence number 
 * than the current sequence number arrives, it is discarded.  In this way,
 * old and duplicate messages are not delivered to the client.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class ChannelManager {
    
    private final Callable<ClientConnection> connectionAccess;
    private ServerSessionListener ssl;
    private HashMap<String, SimpleClientChannel> channelMap;
    private long currentSequenceNumber;
    
    public ChannelManager(Callable<ClientConnection> connectionAccess,
            ServerSessionListener ssl)
    {
        this.connectionAccess = connectionAccess;
        this.ssl = ssl;
        channelMap = new HashMap<String, SimpleClientChannel>();
    }
    
    /**
     * Client channels use this to send messages to the server.  The message
     * is wrapped with the channel's id and intended recipients before being
     * sent out via {@code SimpleClient}.
     * 
     * @param channelName       the name of the channel on which to send the 
     *                          message
     * @param recipients        a collection of recipients that will receive
     *                          the message
     * @param message           the message itself
     */
    void sendMessage(String channelName, Collection<SessionId> recipients, 
                                                            byte[] message) {
        ProtocolMessageEncoder messageEncoder = new ProtocolMessageEncoder(
                CHANNEL_SERVICE, CHANNEL_SEND_REQUEST);
        
        messageEncoder.writeString(channelName);
        messageEncoder.writeShort(recipients.size());
        for (SessionId curId : recipients) {
            messageEncoder.writeSessionId(curId);
        }
        messageEncoder.writeBytes(message);
        
        try {
            connectionAccess.call().sendMessage(messageEncoder.getMessage());
        } catch (Exception e) {
            throw (e instanceof RuntimeException)
                    ? RuntimeException.class.cast(e)
                    : new RuntimeException(e);
        }
    }
    
    /**
     * Called by {@code SimpleClient} when a message from the server arrives
     * that originated from the channel service.
     * 
     * @param messageDecoder    the message decoder, which is positioned at
     *                          the op code
     */
    public void receivedMessage(ProtocolMessageDecoder messageDecoder) {
        int command = messageDecoder.readCommand();
        String channelName = messageDecoder.readString();
        if (command == CHANNEL_JOIN) {
            SimpleClientChannel newChannel = new SimpleClientChannel(this, 
                                                                channelName);
            
            channelMap.put(channelName, newChannel);
            
            ClientChannelListener listener = ssl.joinedChannel(newChannel);
            newChannel.setListener(listener);
        }
        else if (command == CHANNEL_LEAVE) {
            SimpleClientChannel channel = channelMap.get(channelName);
            channel.getListener().leftChannel(channel);
            
        }
        else if (command == CHANNEL_MESSAGE) {
            processChannelMessage(channelName, messageDecoder);
        }
    }
    
    /**
     * Processes an incoming channel message.  The message is forwarded to the 
     * appropriate {@code ClientChannel}.  Old or duplicate messages are 
     * discarded.
     * 
     * @param channelName       the name of the destination channel 
     * @param messageDecoder    the message decoder
     */
    private void processChannelMessage(String channelName, 
                                        ProtocolMessageDecoder messageDecoder) {
        
        long sequenceNumber = messageDecoder.readLong();
        
        // drop old or duplicate messages and return
        if (currentSequenceNumber >= sequenceNumber) {
            return;
        }
        currentSequenceNumber = sequenceNumber;
        
        SimpleClientChannel channel = channelMap.get(channelName);
        
        byte[] senderBytes = messageDecoder.readBytes();
        SessionId sender = null;
        
        // if the message is from the server, the sender is null.
        if (senderBytes.length > 0) {
            sender = SessionId.fromBytes(senderBytes);
        }
        channel.getListener().receivedMessage(channel, sender, 
                                            messageDecoder.readBytes());
    }
    

}
