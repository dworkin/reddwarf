package com.sun.sgs.test.client.simple;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import com.sun.sgs.client.SessionId;

import com.sun.sgs.impl.client.simple.ProtocolMessageDecoder;
import com.sun.sgs.impl.client.simple.ProtocolMessageEncoder;
import com.sun.sgs.impl.io.ServerSocketEndpoint;
import com.sun.sgs.impl.io.TransportType;
import com.sun.sgs.impl.util.HexDumper;
import com.sun.sgs.io.AcceptorListener;
import com.sun.sgs.io.Acceptor;
import com.sun.sgs.io.Connection;
import com.sun.sgs.io.ConnectionListener;

import static com.sun.sgs.impl.client.simple.ProtocolMessage.*;

/**
 * A simple server harness for testing the Client API. This server will
 * respond to the client/server protocol. It uses the IO framework for its
 * networking.
 */
public class SimpleServer implements ConnectionListener {

    private Acceptor<SocketAddress> acceptor;

    private int port = 10002;

    private long sequenceNumber;

    // just a place holder for some sort of ID
    private SessionId serverID;

    /**
     * Construct a new SimpleServer to accept incoming connections.
     */
    public SimpleServer() {
        acceptor = new ServerSocketEndpoint(new InetSocketAddress(port),
                TransportType.RELIABLE).createAcceptor();
        serverID = SessionId.fromBytes(new byte[10]);
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
        ProtocolMessageDecoder messageDecoder = new ProtocolMessageDecoder(
                buffer);
        int versionNumber = messageDecoder.readVersionNumber();
        if (versionNumber != VERSION) {
            System.out.println("Version number mismatch: " + versionNumber
                    + " " + VERSION);
            return;
        }
        int service = messageDecoder.readServiceNumber();
        int command = messageDecoder.readCommand();
        if (command == LOGIN_REQUEST) {
            assert service == APPLICATION_SERVICE;
            ProtocolMessageEncoder messageEncoder = null;

            String username = messageDecoder.readString();
            String password = messageDecoder.readString();

            System.out.println("UserName: " + username + " Password "
                    + password);

            if (password.equals("guest")) {
                messageEncoder = new ProtocolMessageEncoder(
                        APPLICATION_SERVICE, LOGIN_SUCCESS);

                // don't know what the SessionID will look like yet, but
                // it'll at least probably be a byte array.
                messageEncoder.writeSessionId(serverID);
                byte[] sessionByteArray = new byte[10];
                for (byte b = 0; b < sessionByteArray.length; b++) {
                    sessionByteArray[b] = b;
                }
                messageEncoder.writeBytes(sessionByteArray);

                // send the reconnect key
                messageEncoder.writeBytes(new byte[10]);
            } else {
                messageEncoder = new ProtocolMessageEncoder(
                        APPLICATION_SERVICE, LOGIN_FAILURE);
                messageEncoder.writeString("Bad password");
            }
            sendMessage(conn, messageEncoder);
        } else if (command == SESSION_MESSAGE) {
            assert service == APPLICATION_SERVICE;
            String serverMessage = messageDecoder.readString();
            System.out.println("Received general server message: "
                    + serverMessage);

            if (serverMessage.equals("Join Channel")) {
                ProtocolMessageEncoder messageEncoder =
                    new ProtocolMessageEncoder(
                        CHANNEL_SERVICE, CHANNEL_JOIN);

                messageEncoder.writeString("Test Channel");
                sendMessage(conn, messageEncoder);

                sendPeriodicChannelMessages(conn);
            } else if (serverMessage.equals("Leave Channel")) {
                ProtocolMessageEncoder messageEncoder =
                    new ProtocolMessageEncoder(
                        CHANNEL_SERVICE, CHANNEL_LEAVE);
                messageEncoder.writeString("Test Channel");
                sendMessage(conn, messageEncoder);
            }
        } else if (command == CHANNEL_SEND_REQUEST) {
            assert service == CHANNEL_SERVICE;
            String channelName = messageDecoder.readString();
            int numRecipients = messageDecoder.readInt();
            String messageStr = messageDecoder.readString();
            System.out.println("Channel Message " + channelName
                    + " num recipients " + numRecipients + " message "
                    + messageStr);

            ProtocolMessageEncoder messageEncoder =
                new ProtocolMessageEncoder(
                    CHANNEL_SERVICE, CHANNEL_LEAVE);

            messageEncoder.writeString(channelName);
            sendMessage(conn, messageEncoder);
        } else if (command == LOGOUT_REQUEST) {
            assert service == APPLICATION_SERVICE;
            ProtocolMessageEncoder messageEncoder =
                new ProtocolMessageEncoder(
                    APPLICATION_SERVICE, LOGOUT_SUCCESS);

            sendMessage(conn, messageEncoder);
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
        ProtocolMessageEncoder message = new ProtocolMessageEncoder(
                CHANNEL_SERVICE, CHANNEL_MESSAGE);

        message.writeString("Test Channel");
        sequenceNumber++;
        message.writeLong(sequenceNumber % 3 == 0 ? 2 : sequenceNumber);
        message.writeShort(0);

        message.writeShort(2 + chanMessage.getBytes().length);
        message.writeString(chanMessage);
        sendMessage(conn, message);
    }

    private void sendMessage(Connection conn,
            ProtocolMessageEncoder messageEncoder) {
        try {
            byte[] byteMessage = messageEncoder.getMessage();
            System.out.println("sendMessage: (" + byteMessage.length + ") "
                    + HexDumper.format(byteMessage));
            conn.sendBytes(byteMessage);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
