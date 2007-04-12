/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
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

import static com.sun.sgs.protocol.simple.SimpleSgsProtocol.*;

/**
 * A simple server harness for testing the Client API. This server will
 * respond to the client/server protocol. It uses the IO framework for its
 * networking.
 */
public class SimpleServer implements ConnectionListener {

    private Acceptor<SocketAddress> acceptor;

    private int port = 10002;

    private long sequenceNumber;

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
        if (version != VERSION) {
            System.out.println("Version number mismatch: " + version
                    + " " + VERSION);
            return;
        }
        byte service = msg.getByte();
        byte command = msg.getByte();
        if (command == LOGIN_REQUEST) {
            assert service == APPLICATION_SERVICE;

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
                byte[] reconnectKeyBytes = new byte[] {
                    0x1a, 0x1b, 0x1c, 0x1d, 0x30, 0x31, 0x32, 0x33 
                };

                reply =
                    new MessageBuffer(3 +
                        2 + sessionIdBytes.length +
                        2 + reconnectKeyBytes.length);
                reply.putByte(SimpleSgsProtocol.VERSION).
                      putByte(SimpleSgsProtocol.APPLICATION_SERVICE).
                      putByte(SimpleSgsProtocol.LOGIN_SUCCESS).
                      putByteArray(sessionIdBytes).
                      putByteArray(reconnectKeyBytes);
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
        } else if (command == SESSION_MESSAGE) {
            assert service == APPLICATION_SERVICE;
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
        } else if (command == CHANNEL_SEND_REQUEST) {
            assert service == CHANNEL_SERVICE;
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
        } else if (command == LOGOUT_REQUEST) {
            assert service == APPLICATION_SERVICE;
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
            2 +
            MessageBuffer.getSize(chanMessage);
        
        MessageBuffer msg = new MessageBuffer(msgLen);
        msg.putByte(SimpleSgsProtocol.VERSION).
            putByte(SimpleSgsProtocol.CHANNEL_SERVICE).
            putByte(SimpleSgsProtocol.CHANNEL_MESSAGE).
            putBytes(TEST_CHANNEL_ID.getExternalForm()).
            putLong(seq).
            putShort(0).
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
