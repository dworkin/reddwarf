/*
 * JMEClientChannelListener.java
 *
 * Created on January 30, 2006, 3:55 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package com.sun.gi.comm.users.client;

import com.sun.gi.utils.jme.ByteBuffer;

/**
 *
 * @author as93050
 */
public interface JMEClientChannelListener {
    public void playerJoined(byte[] playerID);
    /**
     * This method is called when a player leaves the channel.
     * @param playerID The ID of the leaving player.
     */
    public void playerLeft(byte[] playerID);
    /**
     * This method is called to report the arrival of a packet intended for us on the
     * channel.
     * @param from The ID of the sending player.
     * @param data The actual packet data
     * @param reliable Whether this packet was sent reliably or unreliably.
     */
    public void dataArrived(byte[] from, ByteBuffer data, boolean reliable);
    
    public void channelClosed();
}
