/*
 * Copyright (c) 2007-2009, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
public class SmokeTestChannelListener implements ClientChannelListener{
    private SimpleClient client;
    private boolean receiveMsgPass, leftChannelPass;

    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(SmokeTestChannelListener.class.getName()));
    
    public SmokeTestChannelListener(SimpleClient withClient){
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
