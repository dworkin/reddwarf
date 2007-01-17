package com.sun.sgs.client.simple;

import java.io.IOException;
import java.net.PasswordAuthentication;
import java.util.Properties;

import org.apache.mina.filter.codec.ProtocolEncoder;

import com.sun.sgs.client.ServerSession;
import com.sun.sgs.client.ServerSessionListener;
import com.sun.sgs.client.SessionId;
import com.sun.sgs.client.comm.ClientConnection;
import com.sun.sgs.client.comm.ClientConnectionListener;
import com.sun.sgs.client.comm.ClientConnector;
import com.sun.sgs.impl.client.comm.SimpleConnectorFactory;

import static com.sun.sgs.client.simple.ProtocolMessage.*;

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
public class SimpleClient2 implements ServerSession {
    
    private final static String NOT_CONNECTED = "Client not connected";
    
    /** The listener for this simple client. */
    private SimpleClientListener simpleClientListener;
    private boolean connected = false;
    private ClientConnection connection;
    private SessionId sessionId;
    private ClientConnectionListener clientConnectionListener;
    private ChannelManager channelManager;
    private byte[] reconnectKey;
    
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
    public SimpleClient2(SimpleClientListener listener) {
        this.simpleClientListener = listener;
        clientConnectionListener = new SimpleClientConnectionListener();
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
        channelManager = new ChannelManager(this, simpleClientListener);
        ClientConnector connector = 
                            new SimpleConnectorFactory().createConnector(props);
        connector.connect(clientConnectionListener);
    }

    /** {@inheritDoc}
     * @throws IllegalStateException if this session is disconnected
     */
    public SessionId getSessionId() {
        if (!isConnected()) {
            throw new IllegalStateException(NOT_CONNECTED);
        }
        return sessionId;
    }

    /** {@inheritDoc} */
    public boolean isConnected() {
	return connected;
    }

    /** {@inheritDoc}
     * @throws IllegalStateException if this session is disconnected
     */
    public void logout(boolean force) {
        if (!isConnected()) {
            throw new IllegalStateException(NOT_CONNECTED);
        }
        ProtocolMessageEncoder messageEncoder = new ProtocolMessageEncoder(
                                            APPLICATION_SERVICE, LOGOUT_SUCCESS);
        sendMessage(messageEncoder);
    }

    /** {@inheritDoc}
     * @throws IllegalStateException if this session is disconnected
     */
    public void send(byte[] message) {
        if (!isConnected()) {
            throw new IllegalStateException(NOT_CONNECTED);
        }
        ProtocolMessageEncoder messageEncoder = 
            new ProtocolMessageEncoder(APPLICATION_SERVICE, MESSAGE_TO_SERVER);
        
        messageEncoder.writeBytes(message);
        sendMessage(messageEncoder);
    }
    
    /**
     * Sends the contents of the given {@code ProtocolMessageEncoder} to the 
     * server via the {@code ClientConnection}.
     * 
     * @param messageEncoder
     */
    void sendMessage(ProtocolMessageEncoder messageEncoder) {
        connection.sendMessage(messageEncoder.getMessage());
    }
    
    /**
     * Receives call backs on the associated {@code ClientConnection}.
     * 
     * @author  Sten Anderson
     * @version 1.0
     */
    private class SimpleClientConnectionListener implements 
                                                    ClientConnectionListener {
        

        /**
         * Called in the midst of the connection process.  At this point the 
         * connection has been established, but there is as yet no session.  
         *
         * @param connection        the live connection to the server
         */
        public void connected(ClientConnection connection) {
            connected = true;
            SimpleClient2.this.connection = connection;
            PasswordAuthentication auth = 
                            simpleClientListener.getPasswordAuthentication(
                                                "Enter Username and Password");
            
         
            ProtocolMessageEncoder messageEncoder = 
                new ProtocolMessageEncoder(APPLICATION_SERVICE, LOGIN_REQUEST);

            messageEncoder.writeString(auth.getUserName());
            messageEncoder.writeString(new String(auth.getPassword()));
            sendMessage(messageEncoder);
        }
    
        /**
         * Notification that the client has been disconnected.
         * 
         * TODO implement the graceful piece of this.
         */
        public void disconnected(boolean graceful, byte[] message) {
            connected = false;
            simpleClientListener.disconnected(graceful);
        }
    
        /**
         * All Protocol level messages will come in on this call-back from the
         * associated {@code ClientConnection}.  From here they are interpreted 
         * and dispatched to a channel, or the associated ServerSessionListener.
         * 
         * @param message       the incoming message from the server
         */
        public void receivedMessage(byte[] message) {
            ProtocolMessageDecoder messageDecoder = 
                                    new ProtocolMessageDecoder(message);
            int versionNumber = messageDecoder.readVersionNumber();
            if (versionNumber != ProtocolMessage.VERSION) {
                // TODO not sure what to do here if the protocol versions don't
                // match.  Probably need to disconnect the client with an error.
                // It would be good to bubble up the reason though.
                try {
                    connection.disconnect();
                }
                catch (IOException ioe) {
                    // doesn't matter
                }
                return;
            }
            
            int serviceNumber = messageDecoder.readServiceNumber();
            if (serviceNumber == ProtocolMessage.CHANNEL_SERVICE) {
                channelManager.receivedMessage(messageDecoder);
                return;
            }
            
            // at this point, the message is assumed to be from the "Application
            // Service" -- the only other service besides the "Channel Service".
            int command = messageDecoder.readCommand();
            if (command == LOGIN_SUCCESS) {
                sessionId = SessionId.fromBytes(messageDecoder.readBytes());
                reconnectKey = messageDecoder.readBytes();
                sessionStarted(message);
            }
            else if (command == LOGIN_FAILURE) {
                simpleClientListener.loginFailed(messageDecoder.readString());
            }
            else if (command == LOGOUT_SUCCESS) {
                try {
                    connection.disconnect();
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
            else if (command == MESSAGE_FROM_SERVER) {
                byte[] serverMessage = new byte[message.length - 3];
                System.arraycopy(message, 3, serverMessage, 0, 
                                                        serverMessage.length);
                simpleClientListener.receivedMessage(serverMessage);
            }
        }
    
        public void reconnected(byte[] message) {
            simpleClientListener.reconnected();
            
        }
    
        public void reconnecting(byte[] message) {
            simpleClientListener.reconnecting();
        }

        // TODO not sure about the utility of having the message passed in here,
        // it's already been fully interpreted at this point.
        public ServerSessionListener sessionStarted(byte[] message) {
            
            simpleClientListener.loggedIn();
            
            return simpleClientListener;
        }
        
    }
}
