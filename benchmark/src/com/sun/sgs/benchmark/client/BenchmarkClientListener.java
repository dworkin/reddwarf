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
        channelMessageListeners.add(listener);
    }
    
    public void registerDisconnectedListener(DisconnectedListener listener) {
        disconnectedListeners.add(listener);
    }
    
    public void registerJoinedChannelListener(JoinedChannelListener listener) {
        joinedChannelListeners.add(listener);
    }
    
    public void registerLeftChannelListener(LeftChannelListener listener) {
        leftChannelListeners.add(listener);
    }
    
    public void registerLoggedInListener(LoggedInListener listener) {
        loggedInListeners.add(listener);
    }
    
    public void registerLoginFailedListener(LoginFailedListener listener) {
        loginFailedListeners.add(listener);
    }
    
    public void registerReconnectedListener(ReconnectedListener listener) {
        reconnectedListeners.add(listener);
    }
    
    public void registerReconnectingListener(ReconnectingListener listener) {
        reconnectingListeners.add(listener);
    }
    
    public void registerServerMessageListener(ServerMessageListener listener) {
        serverMessageListeners.add(listener);
    }
    
    public void setPasswordAuthentication(String login, String password) {
        auth = new PasswordAuthentication(login, password.toCharArray());
    }
    
    // Implement SimpleClientListener

    /**
     * {@inheritDoc}
     */
    public void loggedIn() {
        for (LoggedInListener listener : loggedInListeners)
            listener.loggedIn();
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
        System.out.println("FAILED! " + reason);
        for (LoginFailedListener listener : loginFailedListeners)
            listener.loginFailed(reason);
    }

    /**
     * {@inheritDoc}
     */
    public void disconnected(boolean graceful, String reason) {
        for (DisconnectedListener listener : disconnectedListeners)
            listener.disconnected(graceful, reason);
    }
    
    /**
     * {@inheritDoc}
     */
    public void reconnecting() {
        for (ReconnectingListener listener : reconnectingListeners)
            listener.reconnecting();
    }

    /**
     * {@inheritDoc}
     */
    public void reconnected() {
        for (ReconnectedListener listener : reconnectedListeners)
            listener.reconnected();
    }
    
    /**
     * {@inheritDoc}
     */
    public ClientChannelListener joinedChannel(ClientChannel channel) {
        for (JoinedChannelListener listener : joinedChannelListeners)
            listener.joinedChannel(channel);
        
        return this;
    }

    /**
     * {@inheritDoc}
     */
    public void receivedMessage(byte[] message) {
            for (ServerMessageListener listener : serverMessageListeners)
                listener.receivedMessage(message);
    }

    // Implement ClientChannelListener

    /**
     * {@inheritDoc}
     */
    public void receivedMessage(ClientChannel channel, SessionId sender,
            byte[] message)
    {
        for (ChannelMessageListener listener : channelMessageListeners)
            listener.receivedMessage(channel.getName(), sender, message);
    }

    /**
     * {@inheritDoc}
     */
    public void leftChannel(ClientChannel channel) {
        for (LeftChannelListener listener : leftChannelListeners)
            listener.leftChannel(channel.getName());
    }
}
