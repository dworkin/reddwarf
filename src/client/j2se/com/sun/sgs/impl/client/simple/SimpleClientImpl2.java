package com.sun.sgs.impl.client.simple;

import java.io.IOException;
import java.net.PasswordAuthentication;
import java.util.Properties;
import java.util.concurrent.Callable;

import com.sun.sgs.client.ServerSession;
import com.sun.sgs.client.ServerSessionListener;
import com.sun.sgs.client.SessionId;
import com.sun.sgs.client.simple.SimpleClientListener;
import com.sun.sgs.impl.client.comm.ClientConnection;
import com.sun.sgs.impl.client.comm.ClientConnectionListener;
import com.sun.sgs.impl.client.comm.ClientConnector;

import static com.sun.sgs.impl.client.simple.ProtocolMessage.*;

public class SimpleClientImpl2 implements ServerSession {

    /** The listener for this simple client. */
    private SimpleClientListener clientListener;

    private ClientConnectionListener connListener;
    private ClientConnection connection;
    private boolean connected = false;
    private SessionId sessionId;

    private ChannelManager channelManager;
    private byte[] reconnectKey;

    public SimpleClientImpl2(SimpleClientListener listener) {
        this.clientListener = listener;
        connListener = new SimpleClientConnectionListener();
    }

    public void login(Properties props) throws IOException {
        channelManager = new ChannelManager(new Callable<ClientConnection>() {
            public ClientConnection call() throws Exception {
                return connection;
            }
        }, clientListener);
        ClientConnector connector = ClientConnector.create(props);
        connector.connect(connListener);
    }

    public SessionId getSessionId() {
        if (!isConnected()) {
            throw new IllegalStateException("Client not connected");
        }
        return sessionId;
    }

    public boolean isConnected() {
        return connected;
    }

    public void logout(boolean force) throws IllegalStateException {
        if (!isConnected()) {
            throw new IllegalStateException("Client not connected");
        }
        ProtocolMessageEncoder messageEncoder =
            new ProtocolMessageEncoder(APPLICATION_SERVICE, LOGOUT_SUCCESS);
        try {
            sendMessage(messageEncoder);
        } catch (IOException e) {
            try {
                connection.disconnect();
            } catch (IOException e2) {
                // ignore
            }
        }
    }

    public void send(byte[] message) throws IOException {
        if (!isConnected()) {
            throw new IllegalStateException("Client not connected");
        }
        ProtocolMessageEncoder messageEncoder =
            new ProtocolMessageEncoder(APPLICATION_SERVICE, MESSAGE_TO_SERVER);

        messageEncoder.writeBytes(message);
        sendMessage(messageEncoder);
    }

    private void sendMessage(ProtocolMessageEncoder messageEncoder)
        throws IOException
    {
        connection.sendMessage(messageEncoder.getMessage());
    }

    /**
     * Receives callbacks on the associated {@code ClientConnection}.
     * 
     * @author Sten Anderson
     * @version 1.0
     */
    private class SimpleClientConnectionListener
        implements ClientConnectionListener
    {

        /**
         * Called in the midst of the connection process. At this point the
         * connection has been established, but there is as yet no session.
         * 
         * @param connection the live connection to the server
         */
        public void connected(
                @SuppressWarnings("hiding")
                ClientConnection connection)
        {
            connected = true;
            SimpleClientImpl2.this.connection = connection;
            PasswordAuthentication auth =
                clientListener
                    .getPasswordAuthentication("Enter Username and Password");

            ProtocolMessageEncoder messageEncoder =
                new ProtocolMessageEncoder(APPLICATION_SERVICE, LOGIN_REQUEST);

            messageEncoder.writeString(auth.getUserName());
            messageEncoder.writeString(new String(auth.getPassword()));
            try {
                sendMessage(messageEncoder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /**
         * {@inheritDoc}
         *
         * TODO implement the graceful piece of this.
         */
        public void disconnected(boolean graceful, byte[] message) {
            connected = false;
            clientListener.disconnected(graceful);
        }

        /**
         * All Protocol level messages will come in on this callback from
         * the associated {@code ClientConnection}. From here they are
         * interpreted and dispatched to a channel, or the associated
         * ServerSessionListener.
         * 
         * @param message the incoming message from the server
         */
        public void receivedMessage(byte[] message) {
            ProtocolMessageDecoder decoder =
                new ProtocolMessageDecoder(message);
            int versionNumber = decoder.readVersionNumber();
            if (versionNumber != ProtocolMessage.VERSION) {
                // TODO not sure what to do here if the protocol versions
                // don't match. Probably need to disconnect the client with an
                // error. It would be good to bubble up the reason though.
                try {
                    connection.disconnect();
                } catch (IOException ioe) {
                    // doesn't matter
                }
                return;
            }

            int serviceNumber = decoder.readServiceNumber();
            if (serviceNumber == ProtocolMessage.CHANNEL_SERVICE) {
                channelManager.receivedMessage(decoder);
                return;
            }

            // at this point, the message is assumed to be from the
            // "Application Service" -- the only other service besides
            // the "Channel Service".
            int command = decoder.readCommand();
            if (command == LOGIN_SUCCESS) {
                sessionId = SessionId.fromBytes(decoder.readBytes());
                reconnectKey = decoder.readBytes();
                sessionStarted(message);
            } else if (command == LOGIN_FAILURE) {
                clientListener.loginFailed(decoder.readString());
            } else if (command == LOGOUT_SUCCESS) {
                try {
                    connection.disconnect();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            } else if (command == MESSAGE_FROM_SERVER) {
                byte[] serverMessage = new byte[message.length - 3];
                System.arraycopy(message, 3, serverMessage, 0,
                                 serverMessage.length);
                clientListener.receivedMessage(serverMessage);
            }
        }

        public void reconnected(byte[] message) {
            clientListener.reconnected();
        }

        public void reconnecting(byte[] message) {
            clientListener.reconnecting();
        }

        // TODO not sure about the utility of having the message passed in
        // here, it's already been fully interpreted at this point.
        public ServerSessionListener sessionStarted(byte[] message) {
            clientListener.loggedIn();
            return clientListener;
        }

    }
}
