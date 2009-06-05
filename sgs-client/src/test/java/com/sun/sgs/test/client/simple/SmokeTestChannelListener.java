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
 * An implementation of the channel client listener for the smoke test client.
 * This class contains implementations of the <code>receivedMessage</code> and
 * <code>leftChannel</code> callbacks. The class also keeps track of whether or
 * not those callbacks have been tested, and supplied methods that allow the
 * {@link SmokeTestClient} object to determine if the tests have been made. The
 * actual results of the test are printed out as part of the smoketest server.
 */
public class SmokeTestChannelListener implements ClientChannelListener {

    private SimpleClient client;
    private boolean receiveMsgPass, leftChannelPass;
    private static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(SmokeTestChannelListener.class.getName()));

    public SmokeTestChannelListener(SimpleClient withClient) {
        client = withClient;
        receiveMsgPass = leftChannelPass = false;
    }

    /**
     * Callback for when a message is received over a channel. The callback will
     * construct a message made up of "receivedChannelMessage:", the name of the
     * channel, and the message content that was sent, and send this back on the
     * channel itself. It will also set the receiveMsgPass flag to true, showing that
     * this part of the test has been triggered.
     * @param channel The channel on which the message was received, and on
     *      which the reply will be sent
     * @param message the message sent over the channel
     */
    public void receivedMessage(ClientChannel channel, ByteBuffer message) {
        String sendMsg = "receivedChannelMessage:" + channel.getName() + " ";
        String msg = SmokeTestClient.bufferToString(message);
        sendMsg = sendMsg + msg;
        receiveMsgPass = true;
        try {
            channel.send(ByteBuffer.wrap(sendMsg.getBytes()));
        } catch (IOException e) {
            logger.log(Level.WARNING, "Unable to send response to received channel message on channel " + channel.getName());
        }
    }

    /**
     * Callback triggered when the client leaves a channel. The client will respond
     * by sending a message of the form "leftChannel:" with the name of the channel
     * back to the server, on the client session for this client. Calling this will set the
     * leftChannel flag to true, showing that this part of the smoke test has been
     * triggered.
     *
     * @param channel The channel that the client has left.
     */
    public void leftChannel(ClientChannel channel) {
        String msg = "leftChannel:" + channel.getName();
        leftChannelPass = true;
        try {
            client.send(ByteBuffer.wrap(msg.getBytes()));
        } catch (IOException e) {
            logger.log(Level.WARNING, "unable to send channel leave confirmation");
        }
    }

    /**
     * Returns a boolean indicating whether or not the {@link receivedMessage} callback
     * has been called.
     * @return true if the callback has been called; false otherwise.
     */
    boolean getReceiveStatus() {
        return receiveMsgPass;
    }

    /**
     * Returns a boolean indicating whether or not the {@link leftChannel} callback has
     * been called.
     * @return true if the callback has been called; false otherwise.
     */
    boolean getChannelLeftStatus() {
        return leftChannelPass;
    }
}
