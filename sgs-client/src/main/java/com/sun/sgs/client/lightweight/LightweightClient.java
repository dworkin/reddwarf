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
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.ServerSession;
import com.sun.sgs.client.simple.SimpleClientListener;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;
import java.io.IOException;
import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;

/**
 * Lightweight implementation that manages a single client connection.
 * Note that this implements a singleton connector, so that all connections
 * from this VM will be routed through the same {@code LightweightConnector},
 * but this would be easy to change for a more robust stress-testing client.
 */
public final class LightweightClient implements ServerSession {

    // the connector and the actual channel used to send messages
    private static LightweightConnector connector = null;
    
    private SocketChannel channel = null;

    // the map of joined channels
    private final HashMap<IdWrapper,IdWrapper> channelMap =
        new HashMap<IdWrapper,IdWrapper>();

    // the listener used for notification
    private final SimpleClientListener listener;

    // flag that tracks whether we think we're currently connected
    private volatile boolean connected = false;

    /**  Creates an instance of {@code LightweightClient}. */
    public LightweightClient(SimpleClientListener listener) {
        if (listener == null)
            throw new NullPointerException("Listener cannot be null");
        this.listener = listener;

        // create the single connector if it doesn't yet exist
        if (connector == null) {
            synchronized (getClass()) {
                if (connector == null) {
                    try {
                        connector = new LightweightConnector();
                    } catch (IOException ioe) {
                        throw new RuntimeException("Couldn't create connector");
                    }
                }
            }
        }
    }

    /* Implement ServerSession. */

    public void login(Properties p) throws IOException {
        if (isConnected())
            throw new IllegalStateException("Already connected");

        try {
            // try to establish a connection based on standard properties
            String host = p.getProperty("host");
            int port = Integer.valueOf(p.getProperty("port"));
            channel = connector.addConnection(host, port, this);
        } catch (Exception e) {
            listener.disconnected(true, e.getMessage());
            return;
        }

        PasswordAuthentication creds = listener.getPasswordAuthentication();
        byte [] name = creds.getUserName().getBytes();
        byte [] pass = String.valueOf(creds.getPassword()).getBytes();

        if (name.length == 0) {
            listener.disconnected(true, "missing required credentials");
            return;
        }

        short payloadLength = (short)(name.length + pass.length + 6);
        ByteBuffer buffer = ByteBuffer.allocate(payloadLength + 2);
        buffer.putShort(payloadLength);
        buffer.put((byte)(SimpleSgsProtocol.LOGIN_REQUEST));
        buffer.put((byte)(0x04));
        buffer.putShort((short)(name.length));
        buffer.put(name);
        buffer.putShort((short)(pass.length));
        buffer.put(pass);

        buffer.rewind();
        channel.write(buffer);
    }

    public void send(ByteBuffer message) throws IOException {
        if (! isConnected())
            throw new IllegalStateException("Not connected");

        ByteBuffer buffer = ByteBuffer.allocate(message.remaining() + 3);
        buffer.putShort((short)(message.remaining() + 1));
        buffer.put((byte)(SimpleSgsProtocol.SESSION_MESSAGE));
        buffer.put(message);
        buffer.rewind();
        channel.write(buffer);
    }

    public boolean isConnected() {
        return connected;
    }

    public void logout(boolean force) {
        if (! isConnected())
            throw new IllegalStateException("Not connected");

        if (force) {
            disconnect(false, null);
            
            // FIXME: we're not tracking the channel itself
            for (IdWrapper id : channelMap.keySet())
                id.ccl.leftChannel(id.cc);
            channelMap.clear();
        } else {
            ByteBuffer buffer = ByteBuffer.allocate(3);
            buffer.putShort((short)1);
            buffer.put((byte)(SimpleSgsProtocol.LOGOUT_REQUEST));
            buffer.rewind();
            try {
                channel.write(buffer);
            } catch (IOException ioe) {
                logout(true);
            }
        }
    }

    /* Package-private methods used by the connector. */

    SimpleClientListener getListener() {
        return listener;
    }

    void loginSucceeded() {
        connected = true;
        listener.loggedIn();
    }

    void logoutSucceeded() {
        disconnect(true, "logout succeeded");
    }
    
    void disconnect(boolean graceful, String reason) {
        try {
            channel.close();
        } catch (IOException ioe) {}
        
        if (connected) {
            connected = false;
            listener.disconnected(graceful, reason);
        }
    }

    void joinChannel(byte [] chid, ClientChannel cc) {
        if (! isConnected())
            return;
        IdWrapper id = new IdWrapper(chid, cc, listener.joinedChannel(cc));
        channelMap.put(id, id);
    }

    void leaveChannel(byte [] chid) {
        IdWrapper id = channelMap.remove(new IdWrapper(chid));
        if (id != null)
            id.ccl.leftChannel(id.cc);
    }

    void receivedChannelMessage(byte [] chid, ByteBuffer message) {
        if (! isConnected())
            return;
        IdWrapper id = channelMap.get(new IdWrapper(chid));
        if (id != null)
            id.ccl.receivedMessage(id.cc, message);
    }

    void sendChannelMessage(ByteBuffer message, String name, byte [] id)
        throws IOException
    {
        // FIXME: check what we're actually supposed to do if not connected
        if (! isConnected())
            return;
        int curPos = message.position();
        short payloadSize = (short)(3 + id.length + message.remaining());
        ByteBuffer buffer = ByteBuffer.allocate(2 + payloadSize);
        buffer.putShort(payloadSize);
        buffer.put((byte)(SimpleSgsProtocol.CHANNEL_MESSAGE));
        buffer.putShort((short)(id.length));
        buffer.put(id);
        buffer.put(message);
        buffer.rewind();
        channel.write(buffer);
        message.position(curPos);
    }

    /** Utility for tracking id to channel/listener mappings. */
    private static final class IdWrapper {
        final byte [] id;
        final ClientChannel cc;
        final ClientChannelListener ccl;
        /** Creates an instance just for comparison purpose. */
        IdWrapper(byte [] id) {
            this(id, null, null);
        }
        /** Creates an instance to track a mapping. */
        IdWrapper(byte [] id, ClientChannel cc, ClientChannelListener ccl) {
            this.id = id;
            this.cc = cc;
            this.ccl = ccl;
        }
        public boolean equals(Object o) {
            if ((o != null) && (o instanceof IdWrapper))
                return Arrays.equals(id, ((IdWrapper)o).id);
            return false;
        }
        public int hashCode() {
            return Arrays.hashCode(id);
        }
    }
}