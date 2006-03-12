
/*
 * ChatManager.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Feb 25, 2006	 6:11:10 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client;

import com.sun.gi.comm.routing.UserID;

import com.sun.gi.comm.users.client.ClientChannel;

import java.nio.ByteBuffer;

import java.util.HashSet;
import java.util.Map;


/**
 * This class manages chat communications, acting both as a listener notifier
 * for incoming messages and as a broadcast point for outgoing messages.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class ChatManager implements ChatListener
{

    // the set of listeners
    private HashSet<ChatListener> listeners;

    // the chanel for both directions of communications
    private ClientChannel channel;

    /**
     * Creates a <code>ChatManager</code>.
     */
    public ChatManager() {
        listeners = new HashSet<ChatListener>();
        channel = null;
    }

    /**
     * Sets the channel that is used for incoming and outgoing communication.
     *
     * @param channel the communications channel
     */
    public void setChannel(ClientChannel channel) {
        this.channel = channel;
    }

    /**
     * Adds a listener to the set that will be notified when a message
     * arrives at this manager.
     *
     * @param listener the chat message listener
     */
    public void addChatListener(ChatListener listener) {
        listeners.add(listener);
    }

    /**
     * Sends a broadcast chat message to all participants on the current
     * channel.
     * <p>
     * NOTE: at the moment, this does not accept any user info or other
     * meta-data because it's assumed that the server will provide it all,
     * but this may change.
     *
     * @param message the chat message to send
     */
    public void sendMessage(String message) {
        ByteBuffer bb = ByteBuffer.allocate(message.length());
        bb.put(message.getBytes());
        channel.sendBroadcastData(bb, true);
    }

    /**
     *
     */
    public void playerJoined(UserID uid) {
        for (ChatListener listener : listeners)
            listener.playerJoined(uid);
    }

    /**
     *
     */
    public void playerLeft(UserID uid) {
        for (ChatListener listener : listeners)
            listener.playerLeft(uid);
    }

    /**
     * Notify the listener when a message has arrived.
     *
     * @param sender the id of the sender
     * @param message the messsage itself
     */
    public void messageArrived(UserID sender, String message) {
        for (ChatListener listener : listeners)
            listener.messageArrived(sender, message);
    }

    /**
     *
     */
    public void addUidMappings(Map<UserID,String> uidMap) {
        for (ChatListener listener : listeners)
            listener.addUidMappings(uidMap);
    }

}
