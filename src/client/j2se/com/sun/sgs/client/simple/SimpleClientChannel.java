package com.sun.sgs.client.simple;

import java.util.Collections;
import java.util.Set;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.SessionId;

/**
 * A basic implementation of {@code ClientChannel} that uses a 
 * {@code ChannelManager} to send messages.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class SimpleClientChannel implements ClientChannel {

    private final String name;
    private ChannelManager manager;
    private ClientChannelListener listener;
    
    SimpleClientChannel(ChannelManager manager, String name) {
        this.name = name;
        this.manager = manager;
    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    public void send(byte[] message) {
        sendInternal(null, message);
    }

    /**
     * {@inheritDoc}
     */
    public void send(SessionId recipient, byte[] message) {
        sendInternal(Collections.singleton(recipient), message);
    }

    /**
     * {@inheritDoc}
     */
    public void send(Set<SessionId> recipients, byte[] message) {
        sendInternal(recipients, message);
    }
    
    // methods specific to SimpleClientChannel

    private void sendInternal(Set<SessionId> recipients, byte[] message) {
        manager.sendMessage(name, recipients, message);
    }
    
    void setListener(ClientChannelListener listener) {
        this.listener = listener;
    }
    
    ClientChannelListener getListener() {
        return listener;
    }
}
