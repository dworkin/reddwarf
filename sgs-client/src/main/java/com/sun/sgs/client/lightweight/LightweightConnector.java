/*
 * Copyright (c) 2007-2008, Sun Microsystems, Inc.
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

package com.sun.sgs.client.lightweight;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A simple class that handles connections to the server. This is effectively
 * nothing more than a pool of connections with a handler for incoming
 * messages from the server.
 */
class LightweightConnector {
    
    private static final Logger logger =
            Logger.getLogger(LightweightConnector.class.getName());

    // the selector for all incoming messages
    private final Selector selector;

    /**
     * Creates an instance of {@code LightweightConnector} which in
     * turn will try to create an endpoint and, if successful, start a
     * new thread for handling incoming messages.
     */
    LightweightConnector() throws IOException {
        selector = Selector.open();
        (new Thread(new SocketReadingRunnable(selector))).start();
    }

    /**
     * Tries to establish a connection to the given server, using the
     * given callback interfaces for notifications of incoming messages.
     */
    SocketChannel addConnection(String host, int port,
                                LightweightClient client)
        throws IOException
    {
        SocketChannel channel =
            SocketChannel.open(new InetSocketAddress(host, port));
        channel.configureBlocking(false);
        selector.wakeup();
        channel.register(selector, SelectionKey.OP_READ, client);
        return channel;
    }

    /** Private {@code Runnable} that handles all incoming messages. */
    private static final class SocketReadingRunnable implements Runnable {
        // a re-usable buffer for messages
        private final static int SIZE = 64 * 1024;
        private final ByteBuffer readBuffer = ByteBuffer.allocateDirect(SIZE);
        private final Selector selector;

        /** Creates an instance of {@code SocketReadingRunnable}. */
        SocketReadingRunnable(Selector selector) {
            this.selector = selector;
        }

        /** Runs the select loop until interrupted. */
        public void run() {
            try {
                while (true) {
                    // get the ready-set, or wait up to half a second...the
                    // latter is done to force periodic pauses in case a
                    // new channel is trying to register
                    if ((selector.selectNow() > 0) ||
                        (selector.select(500) > 0)) {
                        Iterator<SelectionKey> it =
                            selector.selectedKeys().iterator();
                        while (it.hasNext()) {
                            SelectionKey key = it.next();
                            it.remove();
                            if (key.isValid()) {
                                readBuffer.clear();
                                SocketChannel channel =
                                    (SocketChannel)(key.channel());
                                LightweightClient client =
                                    ((LightweightClient)(key.attachment()));
                                int bytes = -1; // Will cause disconnect if read fails
                                try {
                                    bytes = channel.read(readBuffer);
                                } catch (IOException ioe) {
                                    logger.log(Level.WARNING,
                                               "Exception from read", ioe);
                                }
                                if (bytes < 0) {
                                    client.disconnect(false, "channel close");
                                    continue;
                                }
                                readBuffer.limit(bytes);
                                readBuffer.rewind();
                                while (readBuffer.hasRemaining())
                                    handleMessage(readBuffer, client);
                            }
                        }
                    }
                }
            } catch (IOException ioe) {
                logger.log(Level.SEVERE, "Exception from selector", ioe);
            }
        }
        
        /** Interprets and reacts to any incoming messages. */
        static void handleMessage(ByteBuffer buffer, LightweightClient client) {
            short size = (short)(buffer.getShort() - 1);
            byte [] idBytes;
            switch (buffer.get()) {
            case SimpleSgsProtocol.LOGIN_SUCCESS:
                // reconnectionKey(byte [])
                byte [] keyBytes = new byte[size];
                buffer.get(keyBytes);
                client.loginSucceeded();
                break;
            case SimpleSgsProtocol.LOGIN_FAILURE:
                // reason(String)
                byte [] reason = new byte[buffer.getShort()];
                buffer.get(reason);
                client.getListener().loginFailed(new String(reason));
                break;
            case SimpleSgsProtocol.SESSION_MESSAGE:
                // message(byte [])
                ByteBuffer message = ByteBuffer.allocate(size);
                byte [] messageBytes = new byte[size];
                buffer.get(messageBytes);
                message.put(messageBytes);
                message.rewind();
                client.getListener().receivedMessage(message);
                break;
            case SimpleSgsProtocol.LOGOUT_SUCCESS:
                // nothing
                client.logoutSucceeded();
                break;
            case SimpleSgsProtocol.CHANNEL_JOIN:
                // channelName(String) channelId(byte [])
                byte [] nameBytes = new byte[buffer.getShort()];
                buffer.get(nameBytes);
                String name = new String(nameBytes);
                idBytes = new byte[(short)(size - 2 - nameBytes.length)];
                buffer.get(idBytes);
                client.joinChannel(idBytes,
                                   new ClientChannelImpl(name, idBytes,
                                                         client));
                break;
            case SimpleSgsProtocol.CHANNEL_LEAVE:
                // channelId(byte [])
                idBytes = new byte[size];
                buffer.get(idBytes);
                client.leaveChannel(idBytes);
                break;
            case SimpleSgsProtocol.CHANNEL_MESSAGE:
                // channelIdSize(short) channelId(byte []) message(byte [])
                idBytes = new byte[buffer.getShort()];
                buffer.get(idBytes);
                short len = (short)(size - 2 - idBytes.length);
                ByteBuffer chMessage = ByteBuffer.allocate(len);
                byte [] chBytes = new byte[len];
                buffer.get(chBytes);
                chMessage.put(chBytes);
                chMessage.rewind();
                client.receivedChannelMessage(idBytes, chMessage);
                break;
            default:
                logger.log(Level.WARNING, "Unknown message id");
                break;
            }
        }
        
    }

    /** Simple, private implementation of {@code ClientChannel}. */
    static final class ClientChannelImpl implements ClientChannel {
        private final String name;
        private final byte [] id;
        private final LightweightClient client;
        ClientChannelImpl(String name, byte [] id, LightweightClient client) {
            this.name = name;
            this.id = id;
            this.client = client;
        }
        public String getName() {
            return name;
        }
        public void send(ByteBuffer message) throws IOException {
            client.sendChannelMessage(message, name, id);
        }
    }
}