package com.sun.sgs.impl.client.simple;

import java.io.IOException;
import java.net.PasswordAuthentication;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.ServerSession;
import com.sun.sgs.client.ServerSessionListener;
import com.sun.sgs.client.SessionId;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;
import com.sun.sgs.impl.client.comm.ClientConnection;
import com.sun.sgs.impl.client.comm.ClientConnectionListener;
import com.sun.sgs.impl.client.comm.ClientConnector;
import com.sun.sgs.impl.client.simple.ProtocolMessage;
import com.sun.sgs.impl.client.simple.ProtocolMessageDecoder;
import com.sun.sgs.impl.client.simple.ProtocolMessageEncoder;

public class SimpleClientImpl implements ServerSession {

    /** The listener for this simple client. */
    private final SimpleClientListener clientListener;

    private final ClientConnectionListener connListener;
    private ClientConnection connection;
    private boolean connected = false;
    private SessionId sessionId;

    private final Map<String, SimpleClientChannel> channels;
    private byte[] reconnectKey;

    public SimpleClientImpl(SimpleClientListener listener) {
        this.clientListener = listener;
        connListener = new SimpleClientConnectionListener();
        channels = new ConcurrentHashMap<String, SimpleClientChannel>();
    }

    public void login(Properties props) throws IOException {
        ClientConnector connector = ClientConnector.create(props);
        connector.connect(connListener);
    }

    public SessionId getSessionId() {
        return sessionId;
    }

    public boolean isConnected() {
        return connected;
    }

    public void logout(boolean force) {
        if (connected == false) {
            clientListener.disconnected(true);
            return;
        }
        connected = false;
        if (force) {
            try {
                connection.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                ProtocolMessageEncoder m =
                    new ProtocolMessageEncoder(
                        ProtocolMessage.APPLICATION_SERVICE,
                        ProtocolMessage.LOGOUT_REQUEST);
                sendRaw(m.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    connection.disconnect();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
            }
        }
    }

    public void send(byte[] message) throws IOException {
        ProtocolMessageEncoder m =
            new ProtocolMessageEncoder(ProtocolMessage.APPLICATION_SERVICE,
                ProtocolMessage.MESSAGE_SEND);
        m.writeBytes(message);
        sendRaw(m.getMessage());
    }

    private void sendRaw(byte[] data) throws IOException {
        connection.sendMessage(data);
    }

    /**
     * Receives callbacks on the associated {@code ClientConnection}.
     * 
     * @author Sten Anderson
     * @version 1.0
     */
    final class SimpleClientConnectionListener
        implements ClientConnectionListener
    {
        public void connected(@SuppressWarnings("hiding")
        ClientConnection connection)
        {
            System.out.println("SimpleClient: connected");
            connected = true;
            SimpleClientImpl.this.connection = connection;

            PasswordAuthentication authentication =
                clientListener.getPasswordAuthentication("Enter Username and Password");

            ProtocolMessageEncoder m =
                new ProtocolMessageEncoder(
                    ProtocolMessage.APPLICATION_SERVICE,
                    ProtocolMessage.LOGIN_REQUEST);
            m.writeString(authentication.getUserName());
            m.writeString(new String(authentication.getPassword()));
            try {
                sendRaw(m.getMessage());
            } catch (IOException e) {
                e.printStackTrace();
                logout(true);
            }
        }

        public void disconnected(boolean graceful, byte[] message) {
            connected = false;
            clientListener.disconnected(graceful);
        }

        public void receivedMessage(byte[] message) {
            ProtocolMessageDecoder decoder =
                new ProtocolMessageDecoder(message);
            int versionNumber = decoder.readVersionNumber();
            if (versionNumber != ProtocolMessage.VERSION) {
                System.err.println("Bad version, got: 0x"
                                   + Integer.toHexString(versionNumber)
                                   + " wanted: 0x" + ProtocolMessage.VERSION);
                return;
            }
            int service = decoder.readServiceNumber();
            int command = decoder.readCommand();
            /*
            System.out.println("SimpleClient messageReceived: "
                               + message.length + " command 0x"
                               + Integer.toHexString(command));
                               */
            switch (service) {

            case ProtocolMessage.APPLICATION_SERVICE:
                switch (command) {
                case ProtocolMessage.LOGIN_SUCCESS:
                    System.out.println("logging in");
                    sessionStarted(message);
                    break;

                case ProtocolMessage.LOGIN_FAILURE:
                    clientListener.loginFailed(decoder.readString());
                    break;

                case ProtocolMessage.MESSAGE_SEND:
                    clientListener.receivedMessage(decoder.readBytes());
                    break;

                case ProtocolMessage.RECONNECT_SUCCESS:
                    clientListener.reconnected();
                    break;

                case ProtocolMessage.RECONNECT_FAILURE:
                    try {
                        connection.disconnect();
                    } catch (IOException e) {
                        // TODO
                    }
                    break;

                case ProtocolMessage.LOGOUT_SUCCESS:
                    try {
                        connection.disconnect();
                    } catch (IOException e) {
                        // TODO
                    }
                    break;

                default:
                    System.err.println("Unknown opcode: 0x"
                                       + Integer.toHexString(command));
                    break;
                }
                break;

            case ProtocolMessage.CHANNEL_SERVICE:
                switch (command) {

                case ProtocolMessage.CHANNEL_JOIN: {
                    String channelName = decoder.readString();
                    System.err.println("joining channel " + channelName);
                    SimpleClientChannel channel =
                        new SimpleClientChannel(channelName);
                    channels.put(channelName, channel);
                    channel.setListener(clientListener.joinedChannel(channel));
                    break;
                }

                case ProtocolMessage.CHANNEL_LEAVE: {
                    String channelName = decoder.readString();
                    SimpleClientChannel channel =
                        channels.remove(channelName);
                    if (channel != null) {
                        channel.getListener().leftChannel(channel);
                    }
                    break;
                }

                case ProtocolMessage.CHANNEL_MESSAGE:
                    String channelName = decoder.readString();
                    SimpleClientChannel channel = channels.get(channelName);
                    if (channel == null) {
                        System.err.println("No channel found for '"
                                           + channelName + "'");
                        return;
                    }
                    long seq = decoder.readLong();
                    SessionId sid = SessionId.fromBytes(decoder.readBytes());
                    channel.getListener()
                        .receivedMessage(channel, sid, decoder.readBytes());
                    break;

                default:
                    System.err.println("Unknown opcode: 0x"
                                       + Integer.toHexString(command));
                    break;
                }
                break;

            default:
                System.err.println("Unknown service: 0x"
                                   + Integer.toHexString(service));
                break;
            }
        }

        public void reconnected(byte[] message) {
            clientListener.reconnected();
        }

        public void reconnecting(byte[] message) {
            clientListener.reconnecting();
        }

        public ServerSessionListener sessionStarted(byte[] message) {
            extractSessionId(message);
            clientListener.loggedIn();
            return clientListener;
        }

        private void extractSessionId(byte[] message) {
            ProtocolMessageDecoder decoder =
                new ProtocolMessageDecoder(message);
            byte[] bytes = decoder.readBytes();
            sessionId = SessionId.fromBytes(bytes);
        }
    }

    final class SimpleClientChannel implements ClientChannel {
        private final String name;

        private ClientChannelListener channelListener;

        SimpleClientChannel(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        void setListener(ClientChannelListener listener) {
            this.channelListener = listener;
        }

        ClientChannelListener getListener() {
            return channelListener;
        }

        public void send(byte[] message) throws IOException {
            sendInternal(null, message);
        }

        public void send(SessionId recipient, byte[] message)
            throws IOException
        {
            sendInternal(Collections.singleton(recipient), message);
        }

        public void send(Set<SessionId> recipients, byte[] message)
            throws IOException
        {
            sendInternal(recipients, message);
        }

        public void sendInternal(Set<SessionId> recipients, byte[] message)
            throws IOException
        {
            ProtocolMessageEncoder m =
                new ProtocolMessageEncoder(ProtocolMessage.CHANNEL_SERVICE,
                    ProtocolMessage.CHANNEL_SEND_REQUEST);
            m.writeString(name);
            if (recipients == null) {
                m.writeShort(Short.valueOf((short) 0));
            } else {
                m.writeShort(Short.valueOf((short) recipients.size()));
                for (SessionId id : recipients) {
                    m.writeSessionId(id);
                }
            }
            m.writeBytes(message);
            sendRaw(m.getMessage());
        }
    }
}
