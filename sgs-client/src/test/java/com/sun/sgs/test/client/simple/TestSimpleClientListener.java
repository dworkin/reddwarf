/*
 * Copyright (c) 2007-2010, Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.test.client.simple;

import java.io.IOException;
import java.math.BigInteger;
import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;
import junit.framework.TestCase;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;
import com.sun.sgs.impl.client.comm.ClientConnection;
import com.sun.sgs.impl.client.comm.ClientConnectionListener;
import com.sun.sgs.impl.client.comm.ClientConnector;
import com.sun.sgs.impl.client.comm.ClientConnectorFactory;
import com.sun.sgs.impl.client.simple.SimpleConnectorFactory;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;

public class TestSimpleClientListener
    extends TestCase
{
    MockClientConnection mockConnection;
    MockClientConnector  mockConnector;
    Properties connectionProps;
    SimpleClient client;

    static final String DEFAULT_USER = "alice";
    static final String DEFAULT_PASS = "s3cR37";

    static PasswordAuthentication getDefaultAuth() {
        char[] pass = DEFAULT_PASS.toCharArray();
        return new PasswordAuthentication(DEFAULT_USER, pass);
    }

    @Override
    public void setUp() {
        mockConnection = new MockClientConnection();
        mockConnector = new MockClientConnector(mockConnection);

        MockClientConnector.setConnectorFactory(
            new MockConnectorFactory(mockConnector));

        connectionProps = new Properties();
    }

    @Override
    public void tearDown() {
        MockClientConnector.setConnectorFactory(new SimpleConnectorFactory());
        mockConnector = null;
        mockConnection = null;
        connectionProps = null;
        client = null;
    }

    // Tests

    public void testNullClientListener() throws IOException {
        try {
            connect(null);
        } catch (NullPointerException expected) {
            // passed
            return;
        }
        Assert.fail("Expected NullPointerException");
    }

    public void testNullPasswordAuth() throws IOException {
        ClientListenerBase listener = new ClientListenerBase(null);

        try {
            connect(listener);
            mockConnection.mockConnect();
        } catch (NullPointerException expected) {
            // passed
            return;
        }
        Assert.fail("Expected NullPointerException");
    }

    public void testDisconnect() throws IOException {
        ClientListenerBase listener = new ClientListenerBase() {
            boolean gotLoggedIn = false;
            boolean gotDisconnected = false;

            @Override
            public void loggedIn()
            {
                Assert.assertFalse("Already logged in", gotLoggedIn);
                gotLoggedIn = true;
            }

            @Override
            public void disconnected(boolean graceful, String reason)
            {
                Assert.assertTrue("Expected to be connected", gotLoggedIn);
                Assert.assertFalse("Already disconnected", gotDisconnected);
                gotDisconnected = true;
            }
            
            @Override
            void validate() {
                Assert.assertTrue("Expected to have connected", gotLoggedIn);
                Assert.assertTrue("Expected to have disconnected", gotDisconnected);
            }
        };
        connect(listener);
        mockConnection.mockConnect();
        queueLoggedIn(1);
        mockConnection.mockDeliverRecv();
        mockConnection.mockRequestDisconnect(false, null);
        listener.validate();
    }

    // Helpers
    
    void connect(SimpleClientListener listener) throws IOException {
        client = new SimpleClient(listener);
        client.login(connectionProps);
    }

    void queueLoggedIn(long reconnectKey) {
        byte[] rkey = BigInteger.valueOf(reconnectKey).toByteArray();
        mockConnection.mockLoggedIn(rkey);
    }

    static class ClientListenerBase implements SimpleClientListener
    {
        PasswordAuthentication auth;

        ClientListenerBase() {
            auth = getDefaultAuth();
        }

        ClientListenerBase(PasswordAuthentication auth) {
            this.auth = auth;
        }

        void setPasswordAuthentication(PasswordAuthentication auth) {
            this.auth = auth;
        }
        
        void validate() { }

        public PasswordAuthentication getPasswordAuthentication() {
            return auth;
        }

        public void loggedIn() { }

        public void loginFailed(String reason) { }

        public void disconnected(boolean graceful, String reason) { }

	public ClientChannelListener joinedChannel(ClientChannel channel) {
	    return null;
	}
	
        public void receivedMessage(ByteBuffer message) { }

        public void reconnected() { }

        public void reconnecting() { }
    
    }

    static final class MockClientConnection
        implements ClientConnection
    {
        private ClientConnectionListener listener = null;
        
        private final AtomicInteger state;
        private static final int UNINITIALIZED = 0;
        private static final int CONNECTING = 1;
        private static final int CONNECTED = 2;
        private static final int DISCONNECTING = 3;
        private static final int DISCONNECTED = 4;
        
        public final LinkedList<MessageBuffer> sendQueue;
        public final LinkedList<MessageBuffer> recvQueue;

        public MockClientConnection() {
            state = new AtomicInteger(UNINITIALIZED);
            sendQueue = new LinkedList<MessageBuffer>();
            recvQueue = new LinkedList<MessageBuffer>();
        }
        
        void setListener(ClientConnectionListener listener) {
            assert listener != null;
            this.listener = listener;
            boolean success = state.compareAndSet(UNINITIALIZED, CONNECTING);
            assert success;
        }
        
        void mockConnect() {
            boolean success = state.compareAndSet(CONNECTING, CONNECTED);
            assert success;
            listener.connected(this);
        }
        
        void mockRequestDisconnect(boolean graceful, byte[] message) {
            boolean success = state.compareAndSet(CONNECTED, DISCONNECTING);
            assert success;
            listener.disconnected(graceful, message); // TODO
        }

        void mockFinishDisconnect() {
            boolean success = state.compareAndSet(DISCONNECTING, DISCONNECTED);
            assert success;
        }

        void mockDeliverRecv() {
            MessageBuffer buf = recvQueue.poll();
            assert buf != null;
            mockDeliverRecv(buf.getBuffer());
        }

        void mockDeliverRecv(byte[] message) {
            listener.receivedMessage(message);
        }

        void mockLoggedIn(byte[] reconnectKey) {
            MessageBuffer buf = new MessageBuffer(1 + reconnectKey.length);
            buf.putByte(SimpleSgsProtocol.LOGIN_SUCCESS).
                putBytes(reconnectKey);
            recvQueue.add(buf);
        }

        public void disconnect() throws IOException {
            mockRequestDisconnect(true, null);
        }

        public void sendMessage(ByteBuffer message) throws IOException {
            assert state.get() == CONNECTED;
            byte[] messageCopy = new byte[message.remaining()];
            message.duplicate().get(messageCopy);
            sendQueue.add(new MessageBuffer(messageCopy));
        }
    }
    
    static final class MockClientConnector
        extends ClientConnector
    {
        private final MockClientConnection connection;

        public static void setConnectorFactory(ClientConnectorFactory factory) {
            ClientConnector.setConnectorFactory(factory);
        }

        public MockClientConnector(MockClientConnection connection) {
            this.connection = connection;
        }

        @Override
        public void cancel() throws IOException
        {
            throw new UnsupportedOperationException("Cancel not yet implemented");
        }

        @Override
        public void connect(ClientConnectionListener listener) throws IOException
        {
            connection.setListener(listener);
        }
        
    }
    
    static final class MockConnectorFactory
        implements ClientConnectorFactory
    {
        private final MockClientConnector connector;

        public MockConnectorFactory(MockClientConnector connector) {
            this.connector = connector;
        }

        public MockClientConnector createConnector(Properties properties) {
            return connector;
        }
        
    }
}
