/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSession;

import com.sun.sgs.example.hack.server.util.UtilChannel;

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.BoardSpace;
import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.GameMembershipDetail;

import com.sun.sgs.impl.sharedutil.HexDumper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import java.nio.ByteBuffer;

import java.util.Collection;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;


/**
 * This class provides a single point for static methods that send messages
 * the client. This is provided both as a convenience, and also as a way
 * to keep all message formatting in one place. All message formatting and
 * message code definition is done here.
 * <p>
 * FIXME: All the messages codes are fixed numbers. This should actually be
 * using some enumeration.
 */
public class Messages {

    /**
     * Generic method to send data to a set of users on a given channel.
     *
     * @param task the task
     * @param data the message
     * @param channel the channel to send the message on
     * @param users the set of users to send to
     */
    public static void sendToClient(ByteBuffer data, UtilChannel channel,
                                    ClientSession[] users) {
        /*for (UserId uid : users)
            task.sendData(channel, uid, data, true);*/
        // FIXME: Actually send the message here
        HashSet<ClientSession> recipients = new HashSet<ClientSession>();
        for (ClientSession session : users)
            recipients.add(session);
        data.rewind();
        byte [] bytes = new byte[data.remaining()];
        data.get(bytes);
        channel.send(recipients, bytes);
    }

    /**
     * Generic method to send data to a set of users on a given channel. This
     * serializes the data and sends the object to the client.
     *
     * @param task the task
     * @param command the message code, which will be included before the data
     * @param data the message, which must be <code>Serializable</code>
     * @param channel the channel to send the message on
     * @param users the set of users to send to
     */
    public static void sendToClient(int command, Object data, UtilChannel channel,
                                    ClientSession [] users) {
        // get the bytes
        byte [] bytes = encodeObject(data);
        
        // create a buffer for the message code and the object bytes
        ByteBuffer bb = ByteBuffer.allocate(bytes.length + 1);
        bb.put((byte)command);
        bb.put(bytes);

        // send to the client
        sendToClient(bb, channel, users);
    }

    /**
     * Private helper that encodes the data into an array of bytes
     */
    private static byte [] encodeObject(Object data) {
        try {
            // serialize the object to a stream
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bout);
            oos.writeObject(data);

            // return the bytes
            return bout.toByteArray();
        } catch (IOException ioe) {
            throw new IllegalArgumentException("couldn't encode object", ioe);
        }
    }

    /**
     *
     */
    private static void sendChannelNotice(byte [] session, UtilChannel channel,
                                          boolean joining) {
        byte [] message = new byte[session.length + 1];
        message[0] = joining ? (byte)8 : (byte)9;
        for (int i = 0; i < session.length; i++)
            message[i+1] = session[i];
        channel.send(message);
    }

    private static byte[] getSessionIdBytes(ClientSession session) {
        return AppContext.getDataManager().
            createReference(session).getId().toByteArray();
    }

    /**
     *
     */
    public static void sendPlayerJoined(ClientSession session,
                                        UtilChannel channel) {
        sendChannelNotice(getSessionIdBytes(session), channel, true);
    }

    /**
     *
     */
    public static void sendPlayerLeft(ClientSession session,
                                      UtilChannel channel) {
        sendChannelNotice(getSessionIdBytes(session), channel, false);
    }

    /**
     * Sends uid-to-name mapping. This bulk version is typically used when
     * a player first joins a game, though it may be used at any point.
     *
     * @param task the task
     * @param uidMap the <code>Map</code> of UserIDs to login names
     * @param channel the channel to send the message on
     * @param uid the user to send to
     */
    public static void sendUidMap(Map<ClientSession,String> uidMap,
                                  UtilChannel channel, ClientSession uid) {
        Map<String,String> map = new HashMap<String,String>();
        for (ClientSession session : uidMap.keySet())
            map.put(HexDumper.toHexString(getSessionIdBytes(session)), uidMap.get(session));
        sendToClient(0, map, channel, new ClientSession [] {uid});
    }

    /**
     * Sends a single uid-to-name mapping.
     *
     * @param task the task
     * @param uid the user's identifier
     * @param name the user's login name
     * @param channel the channel to send the message on
     * @param users the users to send to
     */
    public static void sendUidMap(ClientSession uid, String name,
                                  UtilChannel channel, ClientSession [] users) {
        Map<String,String> map = new HashMap<String,String>();
        map.put(HexDumper.toHexString(getSessionIdBytes(uid)),name);
        sendToClient(0, map, channel, users);
    }


    /**
     * START LOBBY MESSAGES
     */

    /**
     * Sends the initial welcome message when a client enters the lobby. This
     * just sends the set of game names to the client. The correct lobby
     * and game counts come from other messages.
     *
     * @param task the task
     * @param games the <code>Collection</code> of games and their detail
     * @param channel the channel to send the message on
     * @param uid the users to send to
     */
    public static void sendLobbyWelcome(Collection<GameMembershipDetail> games,
                                        UtilChannel channel, ClientSession uid) {
        ClientSession [] uids = new ClientSession[] {uid};

        sendToClient(1, games, channel, uids);
    }

    /**
     * Sends notice to a set of clients that the membership of a given
     * game has changed.
     *
     * @param task the task
     * @param name the name of the game that changed
     * @param count the updated membership count
     * @param channel the channel to send the message on
     * @param users the users to send to
     */
    public static void sendGameCountChanged(String name, int count,
                                            UtilChannel channel,
                                            ClientSession [] users) {
        ByteBuffer bb = ByteBuffer.allocate(5 + name.length());

        bb.put((byte)2);
        bb.putInt(count);
        bb.put(name.getBytes());

        sendToClient(bb, channel, users);
    }

    /**
     * Sends notice to a set of clients that a game has been added.
     *
     * @param task the task
     * @param name the name of the game that was added
     * @param channel the channel to send the message on
     * @param users the users to send to
     */
    public static void sendGameAdded(String name, UtilChannel channel,
                                     ClientSession [] users) {
        ByteBuffer bb = ByteBuffer.allocate(1 + name.length());

        bb.put((byte)3);
        bb.put(name.getBytes());

        sendToClient(bb, channel, users);
    }

    /**
     * Sends notice to a set of clients that a game has been removed.
     *
     * @param task the task
     * @param name the name of the game that was added
     * @param channel the channel to send the message on
     * @param users the users to send to
     */
    public static void sendGameRemoved(String name, UtilChannel channel,
                                       ClientSession [] users) {
        ByteBuffer bb = ByteBuffer.allocate(1 + name.length());

        bb.put((byte)4);
        bb.put(name.getBytes());

        sendToClient(bb, channel, users);
    }

    /**
     * Sends a <code>Collection</code> of player statistics.
     *
     * @param task the task
     * @param stats the collection of character statistics
     * @param channel the channel to send the message on
     * @param uid the user to send to
     */
    public static void sendPlayerCharacters(Collection<CharacterStats> stats,
                                            UtilChannel channel,
                                            ClientSession uid) {
        sendToClient(5, stats, channel, new ClientSession [] {uid});
    }

    /**
     * START DUNGEON MESSAGES
     */

    /**
     * Sends a new mapping from identifiers to sprite images. This is
     * typically done with each level.
     *
     * @param task the task
     * @param spriteMap the mapping from identifier to sprite
     * @param channel the channel to send the message on
     * @param uid the user to send to
     */
    public static void sendSpriteMap(SpriteMap spriteMap, UtilChannel channel,
                                     ClientSession uid) {
        // get the bytes
        byte [] bytes = encodeObject(spriteMap.getSpriteMap());
        ByteBuffer bb = ByteBuffer.allocate(bytes.length + 5);

        bb.put((byte)1);
        bb.putInt(spriteMap.getSpriteSize());
        bb.put(bytes);

        sendToClient(bb, channel, new ClientSession [] {uid});
    }
    
    /**
     * Sends a complete <code>Board</code> to a client.
     *
     * @param task the task
     * @param board the <code>Board</code> to send
     * @param channel the channel to send the message on
     * @param uid the user to send to
     */
    public static void sendBoard(Board board, UtilChannel channel,
                                 ClientSession uid) {
        sendToClient(2, board, channel, new ClientSession [] {uid});
    }

    /**
     * Sends updates about a <code>Collection</code> of spaces.
     *
     * @param task the task
     * @param spaces the spaces that are being updated
     * @param channel the channel to send the message on
     * @param users the users to send to
     */
    public static void sendUpdate(Collection<BoardSpace> spaces,
                                  UtilChannel channel, ClientSession [] users) {
        sendToClient(3, spaces, channel, users);
    }

    /**
     * Sends a text message to the client. These are messages generated by
     * the game logic, not chat messages from other clients.
     *
     * @param task the task
     * @param message the message to send
     * @param channel the channel to send the message on
     * @param uid the user to send to
     */
    public static void sendTextMessage(String message, UtilChannel channel,
                                       ClientSession uid) {
        ByteBuffer bb = ByteBuffer.allocate(message.length() + 1);

        bb.put((byte)4);
        bb.put(message.getBytes());

        sendToClient(bb, channel, new ClientSession [] {uid});
    }

    /**
     * START CHARACTER MESSAGES
     */
    
    /**
     * Sends detail about a single character.
     *
     * @param task the task
     * @param id the character's id
     * @param stats the character's statistics
     * @param channel the channel to send the message on
     * @param uid the user to send to
     */
    public static void sendCharacter(int id, CharacterStats stats,
                                     UtilChannel channel, ClientSession uid) {
        byte [] bytes = encodeObject(stats);
        ByteBuffer bb = ByteBuffer.allocate(bytes.length + 5);

        bb.put((byte)64);
        bb.putInt(id);
        bb.put(bytes);

        sendToClient(bb, channel, new ClientSession [] {uid});
    }

}
