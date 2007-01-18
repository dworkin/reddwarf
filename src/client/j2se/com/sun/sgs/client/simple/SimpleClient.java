package com.sun.sgs.client.simple;

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
import com.sun.sgs.impl.client.comm.ClientConnection;
import com.sun.sgs.impl.client.comm.ClientConnectionListener;
import com.sun.sgs.impl.client.comm.ClientConnector;
import com.sun.sgs.impl.client.simple.ProtocolMessage;
import com.sun.sgs.impl.client.simple.ProtocolMessageDecoder;
import com.sun.sgs.impl.client.simple.ProtocolMessageEncoder;
import com.sun.sgs.impl.client.simple.SimpleConnectorFactory;

/**
 * An implementation of {@link ServerSession} that clients can use to
 * manage logging in and communicating with the server.  A
 * <code>SimpleClient</code> is used to establish (or re-establish) a
 * login session with the server, send messages to the server, and log
 * out.
 *
 * <p>A <code>SimpleClient</code> is constructed with a {@link
 * SimpleClientListener} which receives connection-related events,
 * receives messages from the server, and also receives notification
 * of each channel the client is joined to.
 *
 * <p>If the server session associated with a simple client becomes
 * disconnected, then its {@link #send send} and {@link
 * #getSessionId getSessionId} methods will throw
 * <code>IllegalStateException</code>.  Additionally, when a client is
 * disconnected, the server removes that client from the channels that
 * it had been joined to.  A disconnected client can use the {@link
 * #login login} method to log in again.
 *
 * <p>Note that the session identifier of a client changes with each login
 * session; so if a server session is disconnected and then logs in
 * again, the {@link #getSessionId getSessionId} method will
 * return a new <code>SessionId</code>.
 */
public class SimpleClient implements ServerSession {
    
    /** The listener for this simple client. */
    private final SimpleClientListener listener;
    private final ClientConnectionListener connListener;
    private final Map<String, SimpleClientChannel> channels;
    private boolean connected = false;
    private ClientConnection connection;
    private SessionId sessionId;
    
    /**
     * Creates an instance of this class with the specified listener.
     * Once this client is logged in (by using the {@link #login
     * login} method), the specified listener receives
     * connection-related events, receives messages from the server,
     * and also receives notification of each channel the client is
     * joined to.  If this client becomes disconnected for any reason,
     * it may use the <code>login</code> method to log in again.
     *
     * @param listener a listener that will receive events for this
     * client
     */
    public SimpleClient(SimpleClientListener listener) {
        this.listener = listener;
        connListener = new SimpleClientConnectionListener();
        channels = new ConcurrentHashMap<String, SimpleClientChannel>();
    }


    /**
     * Initiates a login session with the server.  A session is
     * established asynchronously with the server as follows:
     *
     * <p>First, this client's {@link PasswordAuthentication login
     * credential} is obtained by invoking its {@link
     * SimpleClientListener listener}'s {@link
     * SimpleClientListener#getPasswordAuthentication
     * getPasswordAuthentication} method with a login prompt.
     *
     * <p>Next, if a connection with the server is successfuly
     * established and the client's login credential (as obtained
     * above) is verified, then the client listener's {@link
     * SimpleClientListener#loggedIn loggedIn} method is invoked.  If,
     * however, the login fails due to a connection failure with the
     * server, a login authentication failure, or some other failure,
     * the client listener's {@link SimpleClientListener#loginFailed
     * loginFailed} method is invoked with a <code>String</code>
     * indicating the reason for the failure.
     *
     * <p>If this client is disconnected for any reason (including
     * login failure), this method may be used again to log in.
     *
     * @param props a properties list specifying properties to
     * use for this client's session (e.g., connection properties)
     */
    public void login(Properties props) throws IOException {
        ClientConnector connector =
            new SimpleConnectorFactory().createConnector(props);
        connector.connect(connListener);
    }

    /**
     * {@inheritDoc}
     * <p>
     * @throws IllegalStateException if this session is disconnected
     */
    public SessionId getSessionId() {
        return sessionId;
    }

    /** {@inheritDoc} */
    public boolean isConnected() {
	return connected;
    }

    /**
     * {@inheritDoc}
     * <p>
     * @throws IllegalStateException if this session is disconnected
     */
    public void logout(boolean force) {
        if (connected == false) {
            listener.disconnected(true);
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
            ProtocolMessageEncoder m = new ProtocolMessageEncoder(
                    ProtocolMessage.APPLICATION_SERVICE,
                    ProtocolMessage.LOGOUT_REQUEST);
            sendRaw(m.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * @throws IllegalStateException if this session is disconnected
     */
    public void send(byte[] message) {
        ProtocolMessageEncoder m = new ProtocolMessageEncoder(
                ProtocolMessage.APPLICATION_SERVICE,
                ProtocolMessage.MESSAGE_SEND);
        m.writeBytes(message);
        sendRaw(m.getMessage());
    }
    
    
    private void sendRaw(byte[] data) {
        connection.sendMessage(data);
    }

    final class SimpleClientConnectionListener
        implements ClientConnectionListener
    {
        /**
         * Called in the midst of the connection process.  At this point the connection
         * has been established, but there is as yet no session.  
         *
         * @param connection        the live connection to the server
         */
        public void connected(
                @SuppressWarnings("hiding") ClientConnection connection)
        {
            System.out.println("SimpleClient: connected");
            connected = true;
            SimpleClient.this.connection = connection;
            
            PasswordAuthentication authentication = 
                listener.getPasswordAuthentication("Enter Username/Password");
    
            ProtocolMessageEncoder m = new ProtocolMessageEncoder(
                    ProtocolMessage.APPLICATION_SERVICE,
                    ProtocolMessage.LOGIN_REQUEST);
            m.writeString(authentication.getUserName());
            // TODO wrapping the char[] in a String is probably not the way to go
            m.writeString(new String(authentication.getPassword()));
            sendRaw(m.getMessage());
        }
    
        public void disconnected(boolean graceful, byte[] message) {
            connected = false;
            listener.disconnected(graceful);
        }
    
        // refactor to receivedMessage(ByteBuffer)
        // all API level messages will come in on this call-back.  From here they
        // are interpreted and dispatched to a channel, or the ServerSessionListener.
        public void receivedMessage(byte[] message) {
            ProtocolMessageDecoder decoder = new ProtocolMessageDecoder(message);
            int versionNumber = decoder.readVersionNumber();
            int service = decoder.readServiceNumber();
            int command = decoder.readCommand();
            System.out.println("SimpleClient messageReceived: " + message.length + 
                                " command 0x" + Integer.toHexString(command));
            
            switch (service) {
            
            case ProtocolMessage.APPLICATION_SERVICE:
                switch (command) {
                case ProtocolMessage.LOGIN_SUCCESS:
                    System.out.println("logging in");
                    sessionStarted(message);
                    break;
                    
                case ProtocolMessage.LOGIN_FAILURE:
                    listener.loginFailed(decoder.readString());
                    break;
    
                    
                case ProtocolMessage.MESSAGE_SEND:
                    listener.receivedMessage(decoder.readBytes());
                    break;
    
                case ProtocolMessage.RECONNECT_SUCCESS:
                    listener.reconnected();
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
                    System.err.println("Unknown opcode: 0x" +
                        Integer.toHexString(command));
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
                    channel.setListener(listener.joinedChannel(channel));
                    break;
                }
                    
                case ProtocolMessage.CHANNEL_LEAVE: {
                    String channelName = decoder.readString();
                    SimpleClientChannel channel = channels.remove(channelName);
                    if (channel != null) {
                        channel.getListener().leftChannel(channel);
                    }
                    break;
                }
                    
                case ProtocolMessage.CHANNEL_MESSAGE:
                    String channelName = decoder.readString();
                    SimpleClientChannel channel = channels.get(channelName);
                    if (channel == null) {
                        System.err.println("No channel found for '" +
                                channelName + "'");
                        return;
                    }
                    long seq = decoder.readLong();
                    SessionId sid =
                        SessionId.fromBytes(decoder.readBytes());
                    channel.getListener().receivedMessage(
                            channel, sid, decoder.readBytes());
                    break;
                    
                default:
                    System.err.println("Unknown opcode: 0x" +
                        Integer.toHexString(command));
                    break;
                }
                break;
                
            default:
                System.err.println("Unknown service: 0x" +
                        Integer.toHexString(service));
                break;
            }
        }
    
        public void reconnected(byte[] message) {
            listener.reconnected();
            
        }
    
        public void reconnecting(byte[] message) {
            listener.reconnecting();
        }
    
        public ServerSessionListener sessionStarted(byte[] message) {
            extractSessionId(message);
            
            listener.loggedIn();
            
            return listener;
        }
        
        private void extractSessionId(byte[] message) {
            ProtocolMessageDecoder decoder = new ProtocolMessageDecoder(message);
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
    
        public void send(byte[] message) {
            sendInternal(null, message);
        }
    
        public void send(SessionId recipient, byte[] message) {
            sendInternal(Collections.singleton(recipient), message);
        }
    
        public void send(Set<SessionId> recipients, byte[] message) {
            sendInternal(recipients, message);
        }

        public void sendInternal(Set<SessionId> recipients,
                byte[] message)
        {
            ProtocolMessageEncoder m = new ProtocolMessageEncoder(
                    ProtocolMessage.CHANNEL_SERVICE,
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
