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
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.Task;

import com.sun.sgs.app.util.ScalableHashMap;

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.BoardSpace;
import com.sun.sgs.example.hack.share.Commands;
import com.sun.sgs.example.hack.share.Commands.Command;
import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.GameMembershipDetail;

import com.sun.sgs.impl.sharedutil.HexDumper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.math.BigInteger;

import java.nio.ByteBuffer;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;

/**
 * This class provides a single point for static methods that send messages
 * the client. This is provided both as a convenience, and also as a way
 * to keep all message formatting in one place. All message formatting and
 * message code definition is done here.
 */
public final class Messages {

    /**
     * The maximum number of Player ID to Name mappings to send during
     * a single task.
     *
     * @see #sendBulkPlayerIDs(ClientSession,Map)
     */
    private static final int MAX_ID_SEND = 100;

    /**
     * Private constructor for preventing instantiation.
     */
    private Messages() { }

    //
    // Actual data-sending methods
    //

    private static void broadcastToClients(Channel channel,
					   Command command,
					   Object... args) {
	byte [] bytes = encodeObjects(args);

        // create a buffer for the message code and the object bytes
	ByteBuffer bb = ByteBuffer.allocate(bytes.length + 4);
	bb.putInt(Commands.encode(command));
	bb.put(bytes);
	bb.rewind();
	
	channel.send(null, bb);	
    }
    
    
    private static void sendToClient(ClientSession session,
				     Command command,
				     Object... args) {
	byte [] bytes = encodeObjects(args);
        
        // create a buffer for the message code and the object bytes
	ByteBuffer bb = ByteBuffer.allocate(bytes.length + 4);
	bb.putInt(Commands.encode(command));
	bb.put(bytes);
	bb.rewind();
	
	session.send(bb);
    }


    //
    // Helper methods
    //

    /**
     * Encodes the provided arguments as bytes.  If the number of
     * arguments is 1, then that argument is serialized by itself.
     * Otherwise, the entire list of arguements is serialized withing
     * an {@code Object[]}.
     *
     * @param args one or more arguments
     *
     * @return the serialized byte representation of the argument if
     *         only one was provided, or of an {@Object[]} containing
     *         all the arguments if more than one was provided.
     */
    private static byte [] encodeObjects(Object... args) {
	try {
	    // serialize each object to a stream
	    ByteArrayOutputStream bout = new ByteArrayOutputStream();
	    ObjectOutputStream oos = new ObjectOutputStream(bout);
	    
	    if (args.length == 1)
		oos.writeObject(args[0]);
	    else
		oos.writeObject(args);
	    oos.close();
	    
	    // return the bytes
	    return bout.toByteArray();
        } catch (IOException ioe) {
            throw new IllegalArgumentException("couldn't encode object", ioe);
        }
    }

    public static void sendNotificationOfUnhandledCommand(ClientSession session,
							  String channelName, 
							  Command command) {
	sendToClient(session, Command.UNHANDLED_COMMAND, channelName, 
		     new Integer(Commands.encode(command)));
    }
    
    //
    // Game-state notification methods
    //

    /**
     * Notifies all the sessons on the channel that the provided
     * {@code ClientSession} has joined.
     */
    public static void broadcastPlayerJoined(Channel channel,
					     BigInteger playerID) {
        broadcastToClients(channel, Command.PLAYER_JOINED,
			   playerID);
    }

    /**
     * Notifies all the sessons on the channel that the provided
     * {@code ClientSession} has left.
     */
    public static void broadcastPlayerLeft(Channel channel,
					   BigInteger playerID) {
        broadcastToClients(channel, Command.PLAYER_LEFT, 
			   playerID);
    }

    /**
     * Sends the command to add the mapping from player ID to player
     * name to all the clients on the provided channel.
     */
    public static void broadcastPlayerID(Channel channel,
					 String playerName,
					 BigInteger playerID) {
	broadcastToClients(channel, Command.ADD_PLAYER_ID, playerName,
			   playerID);
    }

    /**	
     * Sends the command to add the all of the provided mappings from
     * player ID to player name to the specified client. If the
     * provided map is too large, it will be sent in parts through
     * mutliple messages.
     */
    public static void sendBulkPlayerIDs(ClientSession session,
	Map<ManagedReference<ClientSession>,String> playerToName) 
    {

	// The provided mapping could be large enough to serializable
	// to more bytes than we can send at one time.  Furthermore,
	// it could also be a ScalableHashMap, which we can't send to
	// the client side.  
	if (playerToName instanceof ScalableHashMap) {
	    AppContext.getTaskManager().
		scheduleTask(new ScalableMapBulkIdSender(
                    (ScalableHashMap<ManagedReference<ClientSession>,String>)
		        playerToName, session));
	}
	// if we know we can call size() on it, see how big the map is
	// See ScalableHashMap.size() on why we can't call it in this
	// case
	else if (playerToName.size() > 200) {

	    // if the map is too big to send at once, then we'll start
	    // a task to iterate over a fixed number of names and
	    // enqueue itself again to do the rest if necessary.
	    AppContext.getTaskManager().
		scheduleTask(new HashMapBulkIdSender(playerToName, session));
	}
	// otherwise, it's okay to send the entire map at once.
	else {
	    
	    // convert the ClientSession to a BigInteger identifier
	    Map<BigInteger,String> idsToNames = 
		new HashMap<BigInteger,String>();

	    for (Map.Entry<ManagedReference<ClientSession>,String> e :
		     playerToName.entrySet()) {
		
		idsToNames.put(e.getKey().getId(), e.getValue());
	    }
	    
	    sendToClient(session, Command.ADD_BULK_PLAYER_IDS,
			 idsToNames);	    
	}
    }
	
    private static final class ScalableMapBulkIdSender 
	implements Task, ManagedObject, Serializable {

	private static final long serialVersionUID = 1L;

	private final 
	    Iterator<Map.Entry<ManagedReference<ClientSession>,String>> iter;
	
	private final ManagedReference<ClientSession> sessionRef;

	public ScalableMapBulkIdSender(
	    ScalableHashMap<ManagedReference<ClientSession>,String> playrToName,
	    ClientSession session)
	{
	    iter = playrToName.entrySet().iterator();
	    this.sessionRef = 
		AppContext.getDataManager().createReference(session);
	}
	
	/**
	 * Iterates over a finite number of IDs and sends them over.
	 * Then reschedules itself if more IDs remain.
	 */
	public void run() {
	    AppContext.getDataManager().markForUpdate(this);

	    Map<BigInteger,String> idsToNames = new HashMap<BigInteger,String>();
	    while (idsToNames.size() < MAX_ID_SEND &&
		   iter.hasNext()) {
		Map.Entry<ManagedReference<ClientSession>,String> e = 
		    iter.next();
		idsToNames.put(e.getKey().getId(), e.getValue());
	    }
	    
	    Messages.sendToClient(sessionRef.get(), Command.ADD_BULK_PLAYER_IDS,
				  idsToNames);	    
	    
	    if (iter.hasNext())
		AppContext.getTaskManager().scheduleTask(this);
	}
    }

    private static final class HashMapBulkIdSender 
	implements Task, ManagedObject, Serializable {

	private static final long serialVersionUID = 1L;

	private final 
	    Map<ManagedReference<ClientSession>,String> remaining;

	private final ManagedReference<ClientSession> sessionRef;
	
	public HashMapBulkIdSender(
	    Map<ManagedReference<ClientSession>,String> playerToName,
	    ClientSession session)
	{
	    // make a copy of the map since we'll be mutating it
	    remaining = new HashMap<ManagedReference<ClientSession>,String>(
                playerToName);
	    this.sessionRef = 
		AppContext.getDataManager().createReference(session);
	}
	
	/**
	 * Iterates over a finite number of IDs and sends them over.
	 * Then reschedules itself if more IDs remain.
	 */
	public void run() {
	    AppContext.getDataManager().markForUpdate(this);
	    
	    Map<BigInteger,String> idsToNames = new HashMap<BigInteger,String>();

	    Iterator<Map.Entry<ManagedReference<ClientSession>,String>> iter =
		remaining.entrySet().iterator();

	    while (idsToNames.size() < MAX_ID_SEND &&
		   iter.hasNext()) {
		Map.Entry<ManagedReference<ClientSession>,String> e = 
		    iter.next();
		// remove the entry from the map so that we don't send
		// this id again.
		iter.remove();
		
		idsToNames.put(e.getKey().getId(), e.getValue());
	    }
	    
	    Messages.sendToClient(sessionRef.get(), Command.ADD_BULK_PLAYER_IDS,
				  idsToNames);	    
	    
	    if (iter.hasNext())
		AppContext.getTaskManager().scheduleTask(this);
	}
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
     */
    public static void sendGameListing(ClientSession session, 
				       Collection<GameMembershipDetail> games) 
    {

        sendToClient(session, Command.UPDATE_AVAILABLE_GAMES, games);
    }

    /**
     * Sends notice to a set of clients that the membership of a given
     * game has changed.
     *
     * @param name the name of the game that changed
     * @param count the updated membership count
     */
    public static void broadcastGameCountChanged(Channel channel,
						 int count, String name) {

	broadcastToClients(channel, Command.UPDATE_GAME_MEMBER_COUNT, 
			   new Integer(count), name);
    }

    /**
     * Sends notice to a set of clients that a game has been added.
     *
     * @param name the name of the game that was added
     */
    public static void broadcastGameAdded(Channel channel, String name) {

        broadcastToClients(channel, Command.GAME_ADDED, name);
    }

    /**
     * Sends notice to a set of clients that a game has been removed.
     *
     * @param name the name of the game that was added
     */
    public static void broadcastGameRemoved(Channel channel, String name) {

        broadcastToClients(channel, Command.GAME_REMOVED, name);
    }

    /**
     * Sends a <code>Collection</code> of player statistics.
     *
     * @param stats the collection of character statistics
     */
    public static void sendPlayableCharacters(ClientSession session,
					    Collection<CharacterStats> stats) {
        sendToClient(session, Command.NOTIFY_PLAYABLE_CHARACTERS, stats);
    }

    /*
     * START DUNGEON MESSAGES
     */
    
    /**
     * Sends a complete <code>Board</code> to a client.
     *
     * @param board the <code>Board</code> to send
     */
    public static void sendBoard(ClientSession session, Board board) {
	
        sendToClient(session, Command.NEW_BOARD, board);
    }

    /**
     * Sends updates about a <code>Collection</code> of spaces.
     *
     * @param spaces the spaces that are being updated
     */
    public static void broadcastBoardUpdate(Channel channel,
					    Collection<BoardSpace> spaces) 
    {
        broadcastToClients(channel, Command.UPDATE_BOARD_SPACES, spaces);
    }

    /**
     * Sends a text message to the client. These are messages generated by
     * the game logic, not chat messages from other clients.
     *
     * @param message the message to send
     */
    public static void sendServerMessage(ClientSession session, String message) {

        sendToClient(session, Command.NEW_SERVER_MESSAGE, message.toCharArray());
    }

    /*
     * START CHARACTER MESSAGES
     */
    
    /**
     * Sends detail about a single character.
     *
     * @param id the character's id
     * @param stats the character's statistics
     */
    public static void sendCharacter(ClientSession session, int id,
				     CharacterStats stats) {

        sendToClient(session, Command.NEW_CHARACTER_STATS, new Integer(id), 
		     stats);
    }

}
