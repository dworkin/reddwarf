/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.client;

import java.net.PasswordAuthentication;
import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.benchmark.client.listener.*;
import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.SessionId;
import com.sun.sgs.client.simple.SimpleClientListener;

/**
 *
 */
public class BenchmarkClientListener
    implements SimpleClientListener, ClientChannelListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;
    
    private PasswordAuthentication auth = null;
    
    private List<ChannelMessageListener> channelMessageListeners =
        new LinkedList<ChannelMessageListener>();
    
    private List<DisconnectedListener> disconnectedListeners =
        new LinkedList<DisconnectedListener>();
    
    private List<JoinedChannelListener> joinedChannelListeners =
        new LinkedList<JoinedChannelListener>();
    
    private List<LeftChannelListener> leftChannelListeners =
        new LinkedList<LeftChannelListener>();
    
    private List<LoggedInListener> loggedInListeners =
        new LinkedList<LoggedInListener>();
    
    private List<LoginFailedListener> loginFailedListeners =
        new LinkedList<LoginFailedListener>();
    
    private List<ReconnectedListener> reconnectedListeners =
        new LinkedList<ReconnectedListener>();
    
    private List<ReconnectingListener> reconnectingListeners =
        new LinkedList<ReconnectingListener>();
    
    private List<ServerMessageListener> serverMessageListeners =
        new LinkedList<ServerMessageListener>();
    
    // Constructor

    /**
     * Creates a new {@code BenchmarkClientListener}.
     */
    public BenchmarkClientListener() {
    }
    
    // Public Methods
    
    public void registerChannelMessageListener(ChannelMessageListener listener) {
        synchronized (channelMessageListeners) {
            channelMessageListeners.add(listener);
        }
    }
    
    public void registerDisconnectedListener(DisconnectedListener listener) {
        synchronized (disconnectedListeners) {
            disconnectedListeners.add(listener);
        }
    }
    
    public void registerJoinedChannelListener(JoinedChannelListener listener) {
        synchronized (joinedChannelListeners) {
            joinedChannelListeners.add(listener);
        }
    }
    
    public void registerLeftChannelListener(LeftChannelListener listener) {
        synchronized (leftChannelListeners) {
            leftChannelListeners.add(listener);
        }
    }
    
    public void registerLoggedInListener(LoggedInListener listener) {
        synchronized (loggedInListeners) {
            loggedInListeners.add(listener);
        }
    }
    
    public void registerLoginFailedListener(LoginFailedListener listener) {
        synchronized (loginFailedListeners) {
            loginFailedListeners.add(listener);
        }
    }
    
    public void registerReconnectedListener(ReconnectedListener listener) {
        synchronized (reconnectedListeners) {
            reconnectedListeners.add(listener);
        }
    }
    
    public void registerReconnectingListener(ReconnectingListener listener) {
        synchronized (reconnectingListeners) {
            reconnectingListeners.add(listener);
        }
    }
    
    public void registerServerMessageListener(ServerMessageListener listener) {
        synchronized (serverMessageListeners) {
            serverMessageListeners.add(listener);
        }
    }
    
    public void setPasswordAuthentication(String login, String password) {
        auth = new PasswordAuthentication(login, password.toCharArray());
    }
    
    public boolean unregisterChannelMessageListener(ChannelMessageListener listener) {
        synchronized (channelMessageListeners) {
            return channelMessageListeners.remove(listener);
        }
    }
    
    public boolean unregisterDisconnectedListener(DisconnectedListener listener) {
        synchronized (disconnectedListeners) {
            return disconnectedListeners.remove(listener);
        }
    }
    
    public boolean unregisterJoinedChannelListener(JoinedChannelListener listener) {
        synchronized (joinedChannelListeners) {
            return joinedChannelListeners.remove(listener);
        }
    }
    
    public boolean unregisterLeftChannelListener(LeftChannelListener listener) {
        synchronized (leftChannelListeners) {
            return leftChannelListeners.remove(listener);
        }
    }
    
    public boolean unregisterLoggedInListener(LoggedInListener listener) {
        synchronized (loggedInListeners) {
            return loggedInListeners.remove(listener);
        }
    }
    
    public boolean unregisterLoginFailedListener(LoginFailedListener listener) {
        synchronized (loginFailedListeners) {
            return loginFailedListeners.remove(listener);
        }
    }
    
    public boolean unregisterReconnectedListener(ReconnectedListener listener) {
        synchronized (reconnectedListeners) {
            return reconnectedListeners.remove(listener);
        }
    }
    
    public boolean unregisterReconnectingListener(ReconnectingListener listener) {
        synchronized (reconnectingListeners) {
            return reconnectingListeners.remove(listener);
        }
    }
    
    public boolean unregisterServerMessageListener(ServerMessageListener listener) {
        synchronized (serverMessageListeners) {
            return serverMessageListeners.remove(listener);
        }
    }
    
    // Implement SimpleClientListener

    /**
     * {@inheritDoc}
     */
    public void loggedIn() {
        synchronized (loggedInListeners) {
            for (LoggedInListener listener : loggedInListeners)
                listener.loggedIn();
        }
    }

    /**
     * {@inheritDoc}
     */
    public PasswordAuthentication getPasswordAuthentication() {
        return auth;
    }

    /**
     * {@inheritDoc}
     */
    public void loginFailed(String reason) {
        synchronized (loginFailedListeners) {
            for (LoginFailedListener listener : loginFailedListeners)
                listener.loginFailed(reason);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void disconnected(boolean graceful, String reason) {
        synchronized (disconnectedListeners) {
            for (DisconnectedListener listener : disconnectedListeners)
                listener.disconnected(graceful, reason);
        }
    }
    
    /**
     * {@inheritDoc}
     */
    public void reconnecting() {
        synchronized (reconnectingListeners) {
            for (ReconnectingListener listener : reconnectingListeners)
                listener.reconnecting();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void reconnected() {
        synchronized (reconnectedListeners) {
            for (ReconnectedListener listener : reconnectedListeners)
                listener.reconnected();
        }
    }
    
    /**
     * {@inheritDoc}
     */
    public ClientChannelListener joinedChannel(ClientChannel channel) {
        synchronized (joinedChannelListeners) {
            for (JoinedChannelListener listener : joinedChannelListeners)
                listener.joinedChannel(channel);
        }
        
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public void receivedMessage(byte[] message) {
        synchronized (serverMessageListeners) {
            for (ServerMessageListener listener : serverMessageListeners)
                listener.receivedMessage(message);
        }
    }

    // Implement ClientChannelListener

    /**
     * {@inheritDoc}
     */
    public void receivedMessage(ClientChannel channel, SessionId sender,
            byte[] message)
    {
        synchronized (channelMessageListeners) {
            for (ChannelMessageListener listener : channelMessageListeners)
                listener.receivedMessage(channel.getName(), sender, message);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void leftChannel(ClientChannel channel) {
        synchronized (leftChannelListeners) {
            for (LeftChannelListener listener : leftChannelListeners)
                listener.leftChannel(channel.getName());
        }
    }
}
