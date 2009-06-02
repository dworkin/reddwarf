/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.sgs.test.client.simple;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.simple.SimpleClient;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 *
 * @author waldo
 */
public class smokeTestChannelListener implements ClientChannelListener{
    private SimpleClient client;
    
    public smokeTestChannelListener(SimpleClient withClient){
        client = withClient;
    }

    public void receivedMessage(ClientChannel channel, ByteBuffer message){
        String sendMsg = "receivedChannelMessage" + channel.getName() + "" + message.toString();
        try {
                    channel.send(ByteBuffer.wrap(sendMsg.getBytes()));
        } catch (IOException e){
            System.out.println("Unable to send response to received channel message on channel "
                    + channel.getName());
        }
    }

    public void leftChannel(ClientChannel channel){
        String msg = "leftChannel:" + channel.getName();
        try {
            client.send(ByteBuffer.wrap(msg.getBytes()));
        } catch (IOException e){
            System.out.println("unable to send channel leave confirmation");
        }
    }
}
