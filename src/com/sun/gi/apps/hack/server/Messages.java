
/*
 * Messages.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Mon Feb 27, 2006	 7:11:57 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;

import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.share.Board;
import com.sun.gi.apps.hack.share.BoardSpace;
import com.sun.gi.apps.hack.share.CharacterStats;
import com.sun.gi.apps.hack.share.GameMembershipDetail;

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
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class Messages
{

    /**
     * Generic method to send data to a set of users on a given channel.
     *
     * @param task the task
     * @param data the message
     * @param channel the channel to send the message on
     * @param users the set of users to send to
     */
    public static void sendToClient(SimTask task, ByteBuffer data,
                                    ChannelID channel, UserID [] users) {
        for (UserID uid : users)
            task.sendData(channel, uid, data, true);
    }

    /**
     * Generic method to send data to a set of users on a given channel. This
     * serializes the data and sends the object to the client.
     *
     * @param task the task
     * @param the message code, which will be included before the data
     * @param data the message, which must be <code>Serializable</code>
     * @param channel the channel to send the message on
     * @param users the set of users to send to
     */
    public static void sendToClient(SimTask task, int command, Object data,
                                    ChannelID channel, UserID [] users) {
        // get the bytes
        byte [] bytes = encodeObject(data);
        
        // create a buffer for the message code and the object bytes
        ByteBuffer bb = ByteBuffer.allocate(bytes.length + 1);
        bb.put((byte)command);
        bb.put(bytes);

        // send to the client
        sendToClient(task, bb, channel, users);
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
     * Sends uid-to-name mapping. This bulk version is typically used when
     * a player first joins a game, though it may be used at any point.
     *
     * @param task the task
     * @param uidMap the <code>Map</code> of UserIDs to login names
     * @param channel the channel to send the message on
     * @param user the user to send to
     */
    public static void sendUidMap(SimTask task, Map<UserID,String> uidMap,
                                  ChannelID channel, UserID uid) {
        sendToClient(task, 0, uidMap, channel, new UserID [] {uid});
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
    public static void sendUidMap(SimTask task, UserID uid, String name,
                                  ChannelID channel, UserID [] users) {
        Map<UserID,String> map = new HashMap<UserID,String>();
        map.put(uid,name);
        sendToClient(task, 0, map, channel, users);
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
     * @param user the users to send to
     */
    public static void sendLobbyWelcome(SimTask task,
                                        Collection<GameMembershipDetail> games,
                                        ChannelID channel, UserID uid) {
        UserID [] uids = new UserID[] {uid};

        sendToClient(task, 1, games, channel, uids);
    }

    /**
     * Sends notice to a set of clients that the membership of a given
     * game has changed.
     *
     * @param task the task
     * @param name the name of the game that changed
     * @param count the updated membership count
     * @param channel the channel to send the message on
     * @param user the users to send to
     */
    public static void sendGameCountChanged(SimTask task, String name,
                                            int count, ChannelID channel,
                                            UserID [] users) {
        ByteBuffer bb = ByteBuffer.allocate(5 + name.length());

        bb.put((byte)2);
        bb.putInt(count);
        bb.put(name.getBytes());

        sendToClient(task, bb, channel, users);
    }

    /**
     * Sends notice to a set of clients that a game has been added.
     *
     * @param task the task
     * @param name the name of the game that was added
     * @param channel the channel to send the message on
     * @param user the users to send to
     */
    public static void sendGameAdded(SimTask task, String name,
                                     ChannelID channel, UserID [] users) {
        ByteBuffer bb = ByteBuffer.allocate(1 + name.length());

        bb.put((byte)3);
        bb.put(name.getBytes());

        sendToClient(task, bb, channel, users);
    }

    /**
     * Sends notice to a set of clients that a game has been removed.
     *
     * @param task the task
     * @param name the name of the game that was added
     * @param channel the channel to send the message on
     * @param user the users to send to
     */
    public static void sendGameRemoved(SimTask task, String name,
                                       ChannelID channel, UserID [] users) {
        ByteBuffer bb = ByteBuffer.allocate(1 + name.length());

        bb.put((byte)4);
        bb.put(name.getBytes());

        sendToClient(task, bb, channel, users);
    }

    /**
     * Sends a <code>Collection</code> of player statistics.
     *
     * @param task the task
     * @param stats the collection of character statistics
     * @param channel the channel to send the message on
     * @param user the user to send to
     */
    public static void sendPlayerCharacters(SimTask task,
                                            Collection<CharacterStats> stats,
                                            ChannelID channel, UserID uid) {
        sendToClient(task, 5, stats, channel, new UserID [] {uid});
    }

    /**
     * START DUNGEON MESSAGES
     */

    /**
     * Sends a new mapping from identifiers to sprite images. This is
     * typically done with each level.
     *
     * @param task the task
     * @param sprites the mapping from identifier to sprite
     * @param channel the channel to send the message on
     * @param user the user to send to
     */
    public static void sendSpriteMap(SimTask task, SpriteMap spriteMap,
                                     ChannelID channel, UserID uid) {
        // get the bytes
        byte [] bytes = encodeObject(spriteMap.getSpriteMap());
        ByteBuffer bb = ByteBuffer.allocate(bytes.length + 5);

        bb.put((byte)1);
        bb.putInt(spriteMap.getSpriteSize());
        bb.put(bytes);

        sendToClient(task, bb, channel, new UserID [] {uid});
    }
    
    /**
     * Sends a complete <code>Board</code> to a client.
     *
     * @param task the task
     * @param board the <code>Board</code> to send
     * @param channel the channel to send the message on
     * @param user the user to send to
     */
    public static void sendBoard(SimTask task, Board board, ChannelID channel,
                                 UserID uid) {
        sendToClient(task, 2, board, channel, new UserID [] {uid});
    }

    /**
     * Sends updates about a <code>Collection</code> of spaces.
     *
     * @param task the task
     * @param spaces the spaces that are being updated
     * @param channel the channel to send the message on
     * @param users the users to send to
     */
    public static void sendUpdate(SimTask task, Collection<BoardSpace> spaces,
                                  ChannelID channel, UserID [] users) {
        sendToClient(task, 3, spaces, channel, users);
    }

    /**
     * Sends a text mesage to the client. These are messages generated by
     * the game logic, not chat messages from other clients.
     *
     * @param task the task
     * @param message the message to send
     * @param channel the channel to send the message on
     * @param user the user to send to
     */
    public static void sendTextMessage(SimTask task, String message,
                                       ChannelID channel, UserID uid) {
        ByteBuffer bb = ByteBuffer.allocate(message.length() + 1);

        bb.put((byte)4);
        bb.put(message.getBytes());

        sendToClient(task, bb, channel, new UserID [] {uid});
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
     * @param user the user to send to
     */
    public static void sendCharacter(SimTask task, int id,
                                     CharacterStats stats, ChannelID channel,
                                     UserID uid) {
        byte [] bytes = encodeObject(stats);
        ByteBuffer bb = ByteBuffer.allocate(bytes.length + 5);

        bb.put((byte)64);
        bb.putInt(id);
        bb.put(bytes);

        sendToClient(task, bb, channel, new UserID [] {uid});
    }

}
