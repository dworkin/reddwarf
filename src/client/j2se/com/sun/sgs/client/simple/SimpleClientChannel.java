package com.sun.sgs.client.simple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

    private String name;
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
        send(new ArrayList(), message);
    }

    /**
     * {@inheritDoc}
     */
    public void send(SessionId recipient, byte[] message) {
        List<SessionId> list = new ArrayList<SessionId>();
        list.add(recipient);
        send(list, message);
    }

    /**
     * {@inheritDoc}
     */
    public void send(Collection<SessionId> recipients, byte[] message) {
        manager.sendMessage(name, recipients, message);
    }
    
    // methods specific to SimpleClientChannel
    
    void setListener(ClientChannelListener listener) {
        this.listener = listener;
    }
    
    ClientChannelListener getListener() {
        return listener;
    }
}
