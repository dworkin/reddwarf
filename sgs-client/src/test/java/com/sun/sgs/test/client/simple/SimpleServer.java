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
import java.util.concurrent.ConcurrentHashMap;

import com.sun.sgs.impl.io.ServerSocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
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

    private volatile Acceptor<SocketAddress> acceptor;

    private static int DEFAULT_PORT_NUMBER = 10002;

    private int port;
    
    final String TEST_CHANNEL_NAME = "Test Channel";

    /**
     * Construct a new SimpleServer to accept incoming connections.
     */
    public SimpleServer(int port) {
	if (port == 0) {
	    throw new IllegalArgumentException("port must be non-zero");
	}
	this.port = port;
        acceptor = new ServerSocketEndpoint(new InetSocketAddress(port),
                TransportType.RELIABLE).createAcceptor();
    }

    /**
     * Starts up the server.
     */
    public void start() {
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
     * Shuts down the server.
     */
    public void shutdown() {
	if (acceptor != null) {
	    try {
		acceptor.shutdown();
	    } catch (RuntimeException rte) {
	    }
	}
    }

    /**
     * Run the SimpleServer.
     *
     * @param args command-line arguments
     */
    public final static void main(String[] args) {
        SimpleServer server = new SimpleServer(DEFAULT_PORT_NUMBER);
        synchronized (server) {
            server.start();
            try {
                server.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
	    server.shutdown();
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

        if (buffer.length < 1) {
            System.err.println("protocol message too short, length:" +
                buffer.length);
            try {
                conn.close();
            } catch (IOException e) {
                // ignore
            }
            return;
        }

        MessageBuffer msg = new MessageBuffer(buffer);

        byte command = msg.getByte();
        if (command == SimpleSgsProtocol.LOGIN_REQUEST) {

            byte version = msg.getByte();
            if (version != SimpleSgsProtocol.VERSION) {
                System.out.println("Version number mismatch: " + version
                        + " " + SimpleSgsProtocol.VERSION);
                return;
            }

            String username = msg.getString();
            String password = msg.getString();

            System.out.println("UserName: " + username + " Password: "
                    + password);

            MessageBuffer reply;
            if (password.equals("guest")) {

                byte[] reconnectKey = new byte[] {
                    0x1a, 0x1b, 0x1c, 0x1d, 0x30, 0x31, 0x32, 0x33 
                };

                reply =
                    new MessageBuffer(1 + reconnectKey.length);
                reply.putByte(SimpleSgsProtocol.LOGIN_SUCCESS).
		      putBytes(reconnectKey);
            } else {
                String reason = "Bad password";
                reply =
                    new MessageBuffer(1 + MessageBuffer.getSize(reason));

                reply.putByte(SimpleSgsProtocol.LOGIN_FAILURE).
                      putString(reason);
            }
            sendMessage(conn, reply.getBuffer());
        } else if (command == SimpleSgsProtocol.SESSION_MESSAGE) {
            String serverMessage = msg.getString();
            System.out.println("Received general server message: "
                    + serverMessage);

            if (serverMessage.equals("Join Channel")) {
                startMessages(conn);
            } else if (serverMessage.equals("Leave Channel")) {
                stopMessages(conn);
            }
        } else if (command == SimpleSgsProtocol.LOGOUT_REQUEST) {
	    System.out.println("Received logout request");
            MessageBuffer reply = new MessageBuffer(1);
            reply.putByte(SimpleSgsProtocol.LOGOUT_SUCCESS);
            sendMessage(conn, reply.getBuffer());
        }
    }

    final ConcurrentHashMap<Connection, Thread> messageThreads =
        new ConcurrentHashMap<Connection, Thread>();

    private void startMessages(final Connection conn) {
        Thread t = new Thread() {
            private long seq = 0;
            public void run() {
                while (messageThreads.containsKey(conn)) {
                    String msg = "Message " + seq;
                    ++seq;
                    MessageBuffer reply = new MessageBuffer(3 + msg.length());
                    reply.putByte(SimpleSgsProtocol.SESSION_MESSAGE)
                         .putString(msg);
                    sendMessage(conn, reply.getBuffer());
                    try {
                        Thread.sleep((int) (Math.random() * 1000) + 50);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }
                }
            }
        };
        if (messageThreads.putIfAbsent(conn, t) == null) {
            t.start();
        }
    }

    private void stopMessages(final Connection conn) {
        Thread t = messageThreads.remove(conn);
        t.interrupt();
        try {
            t.join();
        } catch (InterruptedException e) {
            // ignore
        }
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
