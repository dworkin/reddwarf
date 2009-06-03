/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.sgs.test.client.simple;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author waldo
 */
public class smokeTestChannelListener implements ClientChannelListener{
    private SimpleClient client;
    private boolean receiveMsgPass, leftChannelPass;

    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(smokeTestChannelListener.class.getName()));
    
    public smokeTestChannelListener(SimpleClient withClient){
        client = withClient;
        receiveMsgPass = leftChannelPass = false;
    }

    public void receivedMessage(ClientChannel channel, ByteBuffer message){
        String sendMsg = "receivedChannelMessage" + channel.getName() + "" + message.toString();
        receiveMsgPass = true;
        try {
                    channel.send(ByteBuffer.wrap(sendMsg.getBytes()));
        } catch (IOException e){
            logger.log(Level.WARNING, "Unable to send response to received channel message on channel "
                    + channel.getName());
        }
    }

    public void leftChannel(ClientChannel channel){
        String msg = "leftChannel:" + channel.getName();
        leftChannelPass = true;
        try {
            client.send(ByteBuffer.wrap(msg.getBytes()));
        } catch (IOException e){
            logger.log(Level.WARNING, "unable to send channel leave confirmation");
        }
    }

    boolean getReceiveStatus(){
        return receiveMsgPass;
    }

    boolean getChannelLeftStatus(){
        return leftChannelPass;
    }
}
