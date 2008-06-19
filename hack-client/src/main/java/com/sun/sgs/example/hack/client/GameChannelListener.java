/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package com.sun.sgs.example.hack.client;

/*import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;*/

import com.sun.sgs.impl.sharedutil.HexDumper;



import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


/**
 * This abstract class is the base for all game-specific listeners.
 */
public abstract class GameChannelListener implements ClientChannelListener
{

    // the chat listener that accepts all incoming chat messages
    private ChatListener chatListener;

    /**
     * Creates an instance of <code>GameChannelListener</code>.
     */
    protected GameChannelListener(ChatListener chatListener) {
        this.chatListener = chatListener;
    }

    /**
     * NOTE that this is part of the new API, but it was never needed
     * under the EA APIs for this client, so for now it's just implemented
     * here and ignored..
     */
    public void leftChannel(ClientChannel channel) {
        
    }

    /**
     *
     */
    protected void notifyJoinOrLeave(ByteBuffer data, boolean joined) {
        byte [] bytes = new byte[data.remaining()];
        data.get(bytes);
        BigInteger sessionId = new BigInteger(1, bytes);
        if (joined)
            chatListener.playerJoined(sessionId);
        else
            chatListener.playerLeft(sessionId);
    }

    /**
     * Notifies this listener that a chat message arrived from the
     * given player.
     *
     * @param playerID the player's identifier
     * @param data the chat message
     */
    protected void notifyChatMessage(ByteBuffer data) {
        byte [] bytes = new byte[data.remaining()];
        data.get(bytes);
        String message = new String(bytes);
        chatListener.messageArrived(message);
    }

    /**
     * Notifies this listener of new user identifier mappings.
     *
     * @param data encoded mapping from user identifier to string
     */
    protected void addUidMappings(ByteBuffer data) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String,String> map = (Map<String,String>)(getObject(data));
        HashMap<BigInteger,String> sessionMap = new HashMap<BigInteger,String>();
        for (String hexString : map.keySet()) {
            BigInteger sessionId =
                new BigInteger(1, HexDumper.fromHexString(hexString));
            sessionMap.put(sessionId, map.get(hexString));
        }
        chatListener.addUidMappings(sessionMap);
    }

    /**
     * Retrieves a serialized object from the given buffer.
     *
     * @param data the encoded object to retrieve
     */
    protected Object getObject(ByteBuffer data) throws IOException {
        try {
            byte [] bytes = new byte[data.remaining()];
            data.get(bytes);

            ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bin);
            return ois.readObject();
        } catch (ClassNotFoundException cnfe) {
            throw new IOException(cnfe.getMessage());
        }
    }

}
