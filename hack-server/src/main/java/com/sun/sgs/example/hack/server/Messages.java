/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ManagedReference;

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
 */
public class Messages {

    /**
     * Generic method to send data to a set of users on a given channel.
     *
     * @param data the message
     * @param channel the channel to send the message on
     * @param users the set of users to send to
     */
    public static void sendToClient(ByteBuffer data, 
				    Channel channel,
                                    ClientSession[] users) {

        HashSet<ClientSession> recipients = new HashSet<ClientSession>();
        for (ClientSession session : users)
            recipients.add(session);
        data.rewind();
        byte [] bytes = new byte[data.remaining()];
        data.get(bytes);
	for (ClientSession user : users) {
	    user.send(ByteBuffer.wrap(bytes));
	}
    }

    /**
     * Generic method to send data to a set of users on a given channel. This
     * serializes the data and sends the object to the client.
     *
     * @param command the message code, which will be included before the data
     * @param data the message, which must be <code>Serializable</code>
     * @param channel the channel to send the message on
     * @param users the set of users to send to
     */
    public static void sendToClient(int command, Object data, Channel channel,
                                    ClientSession [] users) {
        // get the bytes
        byte [] bytes = encodeObject(data);
        
        // create a buffer for the message code and the object bytes
        ByteBuffer bb = ByteBuffer.allocate(bytes.length + 1);
        bb.put((byte)command);
        bb.put(bytes);
	
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
     * Sends whether a client has joined or left a channel.
     */
    private static void sendChannelNotice(ClientSession sender,
					  byte [] session, Channel channel,
                                          boolean joining) {
        byte [] message = new byte[session.length + 1];
        message[0] = joining ? (byte)8 : (byte)9;
        for (int i = 0; i < session.length; i++)
            message[i+1] = session[i];
        channel.send(sender, ByteBuffer.wrap(message));
    }

    /**
     * Returns the byte array representat of the id of the provided
     * session.
     */
    private static byte[] getSessionIdBytes(ClientSession session) {
        return AppContext.getDataManager().
            createReference(session).getId().toByteArray();
    }

    /**
     * Notifies all the sessons on the channel that the provided
     * {@code ClientSession} has joined.
     */
    public static void sendPlayerJoined(ClientSession session,
                                        Channel channel) {
        sendChannelNotice(session, getSessionIdBytes(session), channel, true);
    }

    /**
     * Notifies all the sessons on the channel that the provided
     * {@code ClientSession} has left.
     */
    public static void sendPlayerLeft(ClientSession session,
                                      Channel channel) {
        sendChannelNotice(session, getSessionIdBytes(session), channel, false);
    }

    /**
     * Sends uid-to-name mapping. This bulk version is typically used when
     * a player first joins a game, though it may be used at any point.
     *
     * @param uidMap the <code>Map</code> of references to client
     *               sessions to login names
     * @param channel the channel to send the message on
     * @param uid the user to send to
     */
    public static void sendUidMap(Map<ManagedReference<ClientSession>,String> uidMap,
                                  Channel channel, ClientSession uid) {
        Map<String,String> map = new HashMap<String,String>();
        for (ManagedReference<ClientSession> sessionRef : uidMap.keySet()) {
	    ClientSession session = sessionRef.get();
            map.put(HexDumper.toHexString(getSessionIdBytes(session)), uidMap.get(session));
	}
        sendToClient(0, map, channel, new ClientSession [] {uid});
    }

    /**
     * Sends a single uid-to-name mapping.
     *
     * @param sesion the user's session
     * @param name the user's login name
     * @param channel the channel to send the message on
     * @param users the users to send to
     */
    public static void sendUidMap(ClientSession session, String name,
                                  Channel channel, ClientSession [] users) {
        Map<String,String> map = new HashMap<String,String>();
        map.put(HexDumper.toHexString(getSessionIdBytes(session)),name);
        sendToClient(0, map, channel, users);
    }


    /*
     * START LOBBY MESSAGES
     */

    /**
     * Sends the initial welcome message when a client enters the
     * lobby. This just sends the set of game names to the client. The
     * correct lobby and game counts come from other messages.
     *
     * @param games the <code>Collection</code> of games and their detail
     * @param channel the channel to send the message on
     * @param session the user to send to
     */
    public static void sendLobbyWelcome(Collection<GameMembershipDetail> games,
                                        Channel channel, 
					ClientSession session) {
        ClientSession [] sessions = new ClientSession[] {session};

        sendToClient(11, games, channel, sessions);
    }

    /**
     * Sends notice to a set of clients that the membership of a given
     * game has changed.
     *
     * @param name the name of the game that changed
     * @param count the updated membership count
     * @param channel the channel to send the message on
     * @param users the users to send to
     */
    public static void sendGameCountChanged(String name, int count,
                                            Channel channel,
                                            ClientSession [] users) {
        ByteBuffer bb = ByteBuffer.allocate(5 + name.length());

        bb.put((byte)12);
        bb.putInt(count);
        bb.put(name.getBytes());

        sendToClient(bb, channel, users);
    }

    /**
     * Sends notice to a set of clients that a game has been added.
     *
     * @param name the name of the game that was added
     * @param channel the channel to send the message on
     * @param users the users to send to
     */
    public static void sendGameAdded(String name, Channel channel,
                                     ClientSession [] users) {
        ByteBuffer bb = ByteBuffer.allocate(1 + name.length());

        bb.put((byte)13);
        bb.put(name.getBytes());

        sendToClient(bb, channel, users);
    }

    /**
     * Sends notice to a set of clients that a game has been removed.
     *
     * @param name the name of the game that was added
     * @param channel the channel to send the message on
     * @param users the users to send to
     */
    public static void sendGameRemoved(String name, Channel channel,
                                       ClientSession [] users) {
        ByteBuffer bb = ByteBuffer.allocate(1 + name.length());

        bb.put((byte)14);
        bb.put(name.getBytes());

        sendToClient(bb, channel, users);
    }

    /**
     * Sends a <code>Collection</code> of player statistics.
     *
     * @param stats the collection of character statistics
     * @param channel the channel to send the message on
     * @param session the user to send to
     */
    public static void sendPlayerCharacters(Collection<CharacterStats> stats,
                                            Channel channel,
                                            ClientSession session) {

        sendToClient(15, stats, channel, new ClientSession [] {session});
    }

    /*
     * START DUNGEON MESSAGES
     */

    /**
     * Sends a new mapping from identifiers to sprite images. This is
     * typically done with each level.
     *
     * @param spriteMap the mapping from identifier to sprite
     * @param channel the channel to send the message on
     * @param session the user to send to
     */
    public static void sendSpriteMap(SpriteMap spriteMap, Channel channel,
                                     ClientSession session) {
        // get the bytes
        byte [] bytes = encodeObject(spriteMap.getSpriteMap());
        ByteBuffer bb = ByteBuffer.allocate(bytes.length + 5);

        bb.put((byte)21);
        bb.putInt(spriteMap.getSpriteSize());
        bb.put(bytes);

        sendToClient(bb, channel, new ClientSession [] {session});
    }
    
    /**
     * Sends a complete <code>Board</code> to a client.
     *
     * @param board the <code>Board</code> to send
     * @param channel the channel to send the message on
     * @param session the user to send to
     */
    public static void sendBoard(Board board, Channel channel,
                                 ClientSession session) {
        sendToClient(22, board, channel, new ClientSession [] {session});
    }

    /**
     * Sends updates about a <code>Collection</code> of spaces.
     *
     * @param spaces the spaces that are being updated
     * @param channel the channel to send the message on
     * @param sessions the users to send to
     */
    public static void sendUpdate(Collection<BoardSpace> spaces,
                                  Channel channel, ClientSession [] sessions) {
        sendToClient(23, spaces, channel, sessions);
    }

    /**
     * Sends a text message to the client. These are messages generated by
     * the game logic, not chat messages from other clients.
     *
     * @param message the message to send
     * @param channel the channel to send the message on
     * @param session the user to send to
     */
    public static void sendTextMessage(String message, Channel channel,
                                       ClientSession session) {
        ByteBuffer bb = ByteBuffer.allocate(message.length() + 1);

        bb.put((byte)24);
        bb.put(message.getBytes());

        sendToClient(bb, channel, new ClientSession [] {session});
    }

    /*
     * START CHARACTER MESSAGES
     */
    
    /**
     * Sends detail about a single character.
     *
     * @param id the character's id
     * @param stats the character's statistics
     * @param channel the channel to send the message on
     * @param session the user to send to
     */
    public static void sendCharacter(int id, CharacterStats stats,
                                     Channel channel, ClientSession session) {
        byte [] bytes = encodeObject(stats);
        ByteBuffer bb = ByteBuffer.allocate(bytes.length + 5);

        bb.put((byte)1);
        bb.putInt(id);
        bb.put(bytes);

        sendToClient(bb, channel, new ClientSession [] {session});
    }

}
