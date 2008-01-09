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

package com.sun.sgs.test.client.simple;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.sun.sgs.impl.io.ServerSocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.impl.sharedutil.CompactId;
import com.sun.sgs.impl.sharedutil.HexDumper;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.io.AcceptorListener;
import com.sun.sgs.io.Acceptor;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;
import com.sun.sgs.protocol.simple.SimpleSgsProtocol;

/**
 * A simple server harness for testing the Client API. This server will
 * respond to the client/server protocol. It uses the IO framework for its
 * networking.
 */
public class SimpleServer implements ConnectionListener {

    private Acceptor<SocketAddress> acceptor;

    private int port = 10002;

    private long sequenceNumber;

    private static final CompactId SERVER_ID = new CompactId(new byte[]{0});
    
    final String TEST_CHANNEL_NAME = "Test Channel";
    
    final CompactId TEST_CHANNEL_ID = new CompactId(new byte[] { 0x22 });

    /**
     * Construct a new SimpleServer to accept incoming connections.
     */
    public SimpleServer() {
        acceptor = new ServerSocketEndpoint(new InetSocketAddress(port),
                TransportType.RELIABLE).createAcceptor();
    }

    private void start() {
        System.out.println("Listening on port " + port);
        try {
            acceptor.listen(new AcceptorListener() {
                /**
                 * {@inheritDoc}
                 */
                public ConnectionListener newConnection() {
                    System.out.println("Server: New Connection");
                    return SimpleServer.this;
                }

                /**
                 * {@inheritDoc}
                 */
                public void disconnected() {
                    // TODO Auto-generated method stub
                }
            });
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

    }

    /**
     * Run the SimpleServer.
     *
     * @param args command-line arguments
     */
    public final static void main(String[] args) {
        SimpleServer server = new SimpleServer();
        synchronized (server) {
            server.start();
            try {
                server.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void connected(Connection conn) {
        System.out.println("SimpleServer: connected");
    }

    /**
     * {@inheritDoc}
     */
    public void disconnected(Connection conn) {
        System.out.println("SimpleServer: disconnected");
        synchronized (this) {
            this.notifyAll();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void exceptionThrown(Connection conn, Throwable exception) {
        System.out.println("SimpleServer: exceptionThrown ");
        exception.printStackTrace();
    }

    /**
     * {@inheritDoc}
     */
    public void bytesReceived(Connection conn, byte[] buffer) {
        System.err.println("SimpleServer: messageReceived: ("
                + buffer.length + ") " + HexDumper.format(buffer));

        if (buffer.length < 3) {
            System.err.println("protocol message to short, length:" +
                buffer.length);
            try {
                conn.close();
            } catch (IOException e) {
                // ignore
            }
            return;
        }

        MessageBuffer msg = new MessageBuffer(buffer);

        byte version = msg.getByte();
        if (version != SimpleSgsProtocol.VERSION) {
            System.out.println("Version number mismatch: " + version
                    + " " + SimpleSgsProtocol.VERSION);
            return;
        }
        byte service = msg.getByte();
        byte command = msg.getByte();
        if (command == SimpleSgsProtocol.LOGIN_REQUEST) {
            assert service == SimpleSgsProtocol.APPLICATION_SERVICE;

            String username = msg.getString();
            String password = msg.getString();

            System.out.println("UserName: " + username + " Password "
                    + password);

            MessageBuffer reply;
            if (password.equals("guest")) {

                byte[] sessionIdBytes = new byte[] {
                    (byte)0xda, 0x2c, 0x57, (byte)0xa2,
                    0x01, 0x02, 0x03, 0x04 
                };
		CompactId sessionId = new CompactId(sessionIdBytes);
                byte[] reconnectKeyBytes = new byte[] {
                    0x1a, 0x1b, 0x1c, 0x1d, 0x30, 0x31, 0x32, 0x33 
                };
		CompactId reconnectKey = new CompactId(reconnectKeyBytes);

                reply =
                    new MessageBuffer(3 +
			sessionId.getExternalFormByteCount() +
			reconnectKey.getExternalFormByteCount());
                reply.putByte(SimpleSgsProtocol.VERSION).
                      putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
                      putByte(SimpleSgsProtocol.LOGIN_SUCCESS).
		      putBytes(sessionId.getExternalForm()).
		      putBytes(reconnectKey.getExternalForm());
            } else {
                String reason = "Bad password";
                reply =
                    new MessageBuffer(3 + MessageBuffer.getSize(reason));

                reply.putByte(SimpleSgsProtocol.VERSION).
                      putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
                      putByte(SimpleSgsProtocol.LOGIN_FAILURE).
                      putString(reason);
            }
            sendMessage(conn, reply.getBuffer());
        } else if (command == SimpleSgsProtocol.SESSION_MESSAGE) {
            assert service == SimpleSgsProtocol.APPLICATION_SERVICE;
            msg.getLong(); // FIXME sequence number
            String serverMessage = msg.getString();
            System.out.println("Received general server message: "
                    + serverMessage);

            if (serverMessage.equals("Join Channel")) {
                MessageBuffer reply =
                    new MessageBuffer(3 +
                        MessageBuffer.getSize(TEST_CHANNEL_NAME) +
			TEST_CHANNEL_ID.getExternalFormByteCount());

                reply.putByte(SimpleSgsProtocol.VERSION).
                      putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
                      putByte(SimpleSgsProtocol.CHANNEL_JOIN).
                      putString(TEST_CHANNEL_NAME).
		      putBytes(TEST_CHANNEL_ID.getExternalForm());

                
                sendMessage(conn, reply.getBuffer());

                sendPeriodicChannelMessages(conn);
            } else if (serverMessage.equals("Leave Channel")) {
                MessageBuffer reply =
                    new MessageBuffer(3 +
			TEST_CHANNEL_ID.getExternalFormByteCount());

                reply.putByte(SimpleSgsProtocol.VERSION).
                      putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
                      putByte(SimpleSgsProtocol.CHANNEL_LEAVE).
		      putBytes(TEST_CHANNEL_ID.getExternalForm());
                
                sendMessage(conn, reply.getBuffer());
            }
        } else if (command == SimpleSgsProtocol.CHANNEL_SEND_REQUEST) {
            assert service == SimpleSgsProtocol.CHANNEL_SERVICE;
            CompactId channelId = CompactId.getCompactId(msg);
            msg.getLong(); // FIXME sequence number
            int numRecipients = msg.getShort();
            String messageStr = msg.getString();
            System.out.println("Channel Message " + channelId
                    + " num recipients " + numRecipients + " message "
                    + messageStr);

            // Reply with a channel-leave message, for this test.
            MessageBuffer reply =
                new MessageBuffer(3 +
		  TEST_CHANNEL_ID.getExternalFormByteCount());


            reply.putByte(SimpleSgsProtocol.VERSION).
                  putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
                  putByte(SimpleSgsProtocol.CHANNEL_LEAVE).
		  putBytes(TEST_CHANNEL_ID.getExternalForm());
            
            sendMessage(conn, reply.getBuffer());
        } else if (command == SimpleSgsProtocol.LOGOUT_REQUEST) {
            assert service == SimpleSgsProtocol.APPLICATION_SERVICE;
            MessageBuffer reply = new MessageBuffer(3);
            reply.putByte(SimpleSgsProtocol.VERSION).
                  putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
                  putByte(SimpleSgsProtocol.LOGOUT_SUCCESS);
            sendMessage(conn, reply.getBuffer());
            try {
                conn.close();
            } catch (IOException e) {
                e.printStackTrace();
                // ignore
            }
        }
    }

    private void sendPeriodicChannelMessages(final Connection conn) {
        Thread t = new Thread() {
            public void run() {
                while (true) {
                    sendChannelMessage(
                            "Channel Message " + sequenceNumber, conn);
                    try {
                        Thread.sleep((int) (Math.random() * 1000) + 50);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }
                }
            }
        };
        t.start();
    }

    private void sendChannelMessage(String chanMessage, Connection conn) {
        
        sequenceNumber++;
        long seq = sequenceNumber % 3 == 0 ? 2 : sequenceNumber;
        
        int msgLen = 3 +
            TEST_CHANNEL_ID.getExternalFormByteCount() +
            8 +
            SERVER_ID.getExternalFormByteCount() +
            MessageBuffer.getSize(chanMessage);
        
        MessageBuffer msg = new MessageBuffer(msgLen);
        msg.putByte(SimpleSgsProtocol.VERSION).
            putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
            putByte(SimpleSgsProtocol.CHANNEL_MESSAGE).
            putBytes(TEST_CHANNEL_ID.getExternalForm()).
            putLong(seq).
            putBytes(SERVER_ID.getExternalForm()).
            putString(chanMessage);

        sendMessage(conn, msg.getBuffer());
    }

    private void sendMessage(Connection conn, byte[] message) {
        try {
            System.out.println("sendMessage: (" + message.length + ") "
                    + HexDumper.format(message));
            conn.sendBytes(message);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
