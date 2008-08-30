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
import com.sun.sgs.example.hack.share.CreatureInfo;
import com.sun.sgs.example.hack.share.CreatureInfo.Creature;
import com.sun.sgs.example.hack.share.CreatureInfo.CreatureType;
import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.GameMembershipDetail;
import com.sun.sgs.example.hack.share.ItemInfo;
import com.sun.sgs.example.hack.share.ItemInfo.Item;
import com.sun.sgs.example.hack.share.ItemInfo.ItemType;
import com.sun.sgs.example.hack.share.RoomInfo;
import com.sun.sgs.example.hack.share.RoomInfo.FloorType;

import com.sun.sgs.impl.sharedutil.HexDumper;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
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

import java.util.logging.Logger;

/**
 * This class provides a single point for static methods that send messages
 * the client. This is provided both as a convenience, and also as a way
 * to keep all message formatting in one place. All message formatting and
 * message code definition is done here.
 */
public final class Messages {


    private static final Logger logger =
	Logger.getLogger(Messages.class.getName());

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

//     private static void broadcastToClients(Channel channel,
// 					   Command command,
// 					   byte[] args) {

//         // create a buffer for the message code and the object bytes
// 	ByteBuffer bb = ByteBuffer.allocate(bytes.length + 4);
// 	bb.putInt(Commands.encode(command));
// 	bb.put(bytes);
// 	bb.rewind();
	
// 	channel.send(null, bb);	
//     }
    
    
//     private static void sendToClient(ClientSession session,
// 				     Command command,
// 				     byte[] args) {
        
//         // create a buffer for the message code and the object bytes
// 	ByteBuffer bb = ByteBuffer.allocate(bytes.length + 4);
// 	bb.putInt(Commands.encode(command));
// 	bb.put(bytes);
// 	bb.rewind();
	
// 	session.send(bb);
//     }


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
//     private static byte [] encodeObjects(Object... args) {
// 	try {
// 	    // serialize each object to a stream
// 	    ByteArrayOutputStream bout = new ByteArrayOutputStream();
// 	    ObjectOutputStream oos = new ObjectOutputStream(bout);
	    
// 	    if (args.length == 1)
// 		oos.writeObject(args[0]);
// 	    else
// 		oos.writeObject(args);
// 	    oos.close();
	    
// 	    // return the bytes
// 	    return bout.toByteArray();
//         } catch (IOException ioe) {
//             throw new IllegalArgumentException("couldn't encode object", ioe);
//         }
//     }

    public static void 
	sendNotificationOfUnhandledCommand(ClientSession session,
					   String channelName, 
					   Command unhandledCommand) {

	// space for the length of the string, the encoded command and
	// the name of the channel that the command was sent to
	ByteBuffer bb = ByteBuffer.allocate(12 + (channelName.length() * 2));
	bb.putInt(Commands.encode(Command.UNHANDLED_COMMAND));
	bb.putInt(Commands.encode(unhandledCommand));
	bb.putInt(channelName.length());
	char[] arr = channelName.toCharArray();
	for (char c : arr)
	    bb.putChar(c);

	bb.rewind();
	session.send(bb);
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
	byte[] id = playerID.toByteArray();
	ByteBuffer bb = ByteBuffer.allocate(8 + id.length);
	bb.putInt(Commands.encode(Command.PLAYER_JOINED));
	bb.putInt(id.length);
	for (byte b : id)
	    bb.put(b);

	bb.rewind();
	channel.send(null, bb);
    }

    /**
     * Notifies all the sessons on the channel that the provided
     * {@code ClientSession} has left.
     */
    public static void broadcastPlayerLeft(Channel channel,
					   BigInteger playerID) {
	byte[] id = playerID.toByteArray();
	ByteBuffer bb = ByteBuffer.allocate(8 + id.length);
	bb.putInt(Commands.encode(Command.PLAYER_LEFT));
	bb.putInt(id.length);
	for (byte b : id)
	    bb.put(b);

	bb.rewind();
	channel.send(null, bb);
    }

    /**
     * Sends the command to add the mapping from player ID to player
     * name to all the clients on the provided channel.
     */
    public static void broadcastPlayerID(Channel channel,
					 String playerName,
					 BigInteger playerID) {
	byte[] id = playerID.toByteArray();
	char[] arr = playerName.toCharArray();

	ByteBuffer bb = ByteBuffer.allocate(12 + id.length + (arr.length * 2));
	bb.putInt(Commands.encode(Command.ADD_PLAYER_ID));
	bb.putInt(id.length);
	for (byte b : id)
	    bb.put(b);
	bb.putInt(arr.length);
	for (char c : arr)
	    bb.putChar(c);

	bb.rewind();
	channel.send(null, bb);
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
// 	// if we know we can call size() on it, see how big the map is
// 	// See ScalableHashMap.size() on why we can't call it in this
// 	// case
// 	else if (playerToName.size() > 200) {

// 	    // if the map is too big to send at once, then we'll start
// 	    // a task to iterate over a fixed number of names and
// 	    // enqueue itself again to do the rest if necessary.
// 	    AppContext.getTaskManager().
// 		scheduleTask(new HashMapBulkIdSender(playerToName, session));
// 	}
	// otherwise, it's okay to send the entire map at once.
	else {

	    // allocate a stream to write the data, as it will be too
	    // much work to determine the size for preallocating a
	    // ByteBuffer
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    DataOutputStream dos = new DataOutputStream(baos);

	    try {
		dos.writeInt(Commands.encode(Command.ADD_BULK_PLAYER_IDS));
		dos.writeInt(playerToName.size());
	    } catch (IOException ioe) {
		// this should never happen, but log in case it
		// ever does
		logger.severe("unexpected error: " + ioe);
	    }	
	    
	    // convert each reference to ClientSession to a BigInteger
	    // identifier
	    Map<BigInteger,String> idsToNames = 
		new HashMap<BigInteger,String>();

	    for (Map.Entry<ManagedReference<ClientSession>,String> e :
		     playerToName.entrySet()) {
		
		BigInteger playerId = e.getKey().getId();
		String playerName = e.getValue();

		byte[] id = playerId.toByteArray();
		char[] nm = playerName.toCharArray();

		try {
		    dos.writeInt(id.length);
		    dos.write(id);
		    dos.writeInt(nm.length);
		    for (char c : nm)
			dos.writeChar(c);
		} catch (IOException ioe) {
		    // this should never happen, but log in case it
		    // ever does
		    logger.severe("unexpected error: " + ioe);
		}		
	    }
	    
	    try {
		baos.close();
		dos.close();
	    } catch (IOException ioe) {
		// this should never happen, but log in case it ever
		// does
		logger.severe("unexpected error: " + ioe);
	    }

	    // wrap all the data that we just wrote in a byte buffer
	    ByteBuffer bb = ByteBuffer.wrap(baos.toByteArray());
	    bb.rewind();
	    session.send(bb);
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

	    // allocate a stream to write the data, as it will be too
	    // much work to determine the size for preallocating a
	    // ByteBuffer
	    ByteArrayOutputStream baos = new ByteArrayOutputStream();
	    DataOutputStream dos = new DataOutputStream(baos);	    
		
	    int idCount = 0;

	    while (idCount < MAX_ID_SEND && iter.hasNext()) {
		
		Map.Entry<ManagedReference<ClientSession>,String> e = 
		    iter.next();

		BigInteger playerId = e.getKey().getId();
		String playerName = e.getValue();

		byte[] id = playerId.toByteArray();
		char[] nm = playerName.toCharArray();

		try {
		    dos.writeInt(id.length);
		    dos.write(id);
		    dos.writeInt(nm.length);

		    for (char c : nm)
			dos.writeChar(c);
		
		} catch (IOException ioe) {
		    // this should never happen, but log in case it ever
		    // does
		    logger.severe("unexpected error: " + ioe);
		}
		
		idCount++;
	    }
	    
	    try {
		baos.close();
		dos.close();
	    } catch (IOException ioe) {
		// this should never happen, but log in case it ever
		// does
		logger.severe("unexpected error: " + ioe);
	    }

	    byte[] bulkIds = baos.toByteArray();
	    ByteBuffer bb = ByteBuffer.allocate(8 + bulkIds.length);

	    bb.putInt(Commands.encode(Command.ADD_BULK_PLAYER_IDS));
	    bb.putInt(idCount);	    
	    // wrap all the data that we just wrote in a byte buffer
	    bb.put(bulkIds);
	    bb.rewind();

	    sessionRef.get().send(bb);
	
	    // reschedule this task if there are still more ids to
	    // send
	    if (iter.hasNext())
		AppContext.getTaskManager().scheduleTask(this);
	    // otherwise, remove this object from the data store
	    else
		AppContext.getDataManager().removeObject(this);
	}
    }

//     private static final class HashMapBulkIdSender 
// 	implements Task, ManagedObject, Serializable {

// 	private static final long serialVersionUID = 1L;

// 	private final 
// 	    Map<ManagedReference<ClientSession>,String> remaining;

// 	private final ManagedReference<ClientSession> sessionRef;
	
// 	public HashMapBulkIdSender(
// 	    Map<ManagedReference<ClientSession>,String> playerToName,
// 	    ClientSession session)
// 	{
// 	    // make a copy of the map since we'll be mutating it
// 	    remaining = new HashMap<ManagedReference<ClientSession>,String>(
//                 playerToName);
// 	    this.sessionRef = 
// 		AppContext.getDataManager().createReference(session);
// 	}
	
// 	/**
// 	 * Iterates over a finite number of IDs and sends them over.
// 	 * Then reschedules itself if more IDs remain.
// 	 */
// 	public void run() {
// 	    AppContext.getDataManager().markForUpdate(this);
	    
// 	    Map<BigInteger,String> idsToNames = new HashMap<BigInteger,String>();

// 	    Iterator<Map.Entry<ManagedReference<ClientSession>,String>> iter =
// 		remaining.entrySet().iterator();

// 	    while (idsToNames.size() < MAX_ID_SEND &&
// 		   iter.hasNext()) {
// 		Map.Entry<ManagedReference<ClientSession>,String> e = 
// 		    iter.next();
// 		// remove the entry from the map so that we don't send
// 		// this id again.
// 		iter.remove();
		
// 		idsToNames.put(e.getKey().getId(), e.getValue());
// 	    }
	    
// 	    Messages.sendToClient(sessionRef.get(), Command.ADD_BULK_PLAYER_IDS,
// 				  idsToNames);	    
	    
// 	    if (iter.hasNext())
// 		AppContext.getTaskManager().scheduleTask(this);
// 	}
//     }
    
	  

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

	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	DataOutputStream dos = new DataOutputStream(baos);	    
	
	try {
	    for (GameMembershipDetail d : games) {
		String gameName = d.getGame();
		dos.writeInt(gameName.length());
		char[] nm = gameName.toCharArray();
		for (char c : nm)
		    dos.writeChar(c);
		dos.writeInt(d.getCount());
	    }
	    dos.close();
	    baos.close();
	} catch (IOException ioe) {
	    // this should never happen, but log in case it ever
	    // does
	    logger.severe("unexpected error: " + ioe);
	}

	byte[] data = baos.toByteArray();
	ByteBuffer bb = ByteBuffer.allocate(8 + data.length);
	bb.putInt(Commands.encode(Command.UPDATE_AVAILABLE_GAMES));
	bb.putInt(games.size());
	bb.put(data);
	bb.rewind();

	session.send(bb);
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

	ByteBuffer bb = ByteBuffer.allocate(12 + (name.length() * 2));
	bb.putInt(Commands.encode(Command.UPDATE_GAME_MEMBER_COUNT));
	bb.putInt(count);
	bb.putInt(name.length());
	char[] arr = name.toCharArray();
	for (char c : arr)
	    bb.putChar(c);
	
	bb.rewind();
	channel.send(null, bb);
    }

    /**
     * Sends notice to a set of clients that a game has been added.
     *
     * @param name the name of the game that was added
     */
    public static void broadcastGameAdded(Channel channel, String name) {

	ByteBuffer bb = ByteBuffer.allocate(8 + (name.length() * 2));
	bb.putInt(Commands.encode(Command.GAME_ADDED));
	bb.putInt(name.length());
	char[] arr = name.toCharArray();
	for (char c : arr)
	    bb.putChar(c);
	
	bb.rewind();
	channel.send(null, bb);
    }

    /**
     * Sends notice to a set of clients that a game has been removed.
     *
     * @param name the name of the game that was added
     */
    public static void broadcastGameRemoved(Channel channel, String name) {

	ByteBuffer bb = ByteBuffer.allocate(8 + (name.length() * 2));
	bb.putInt(Commands.encode(Command.GAME_REMOVED));
	bb.putInt(name.length());
	char[] arr = name.toCharArray();
	for (char c : arr)
	    bb.putChar(c);
	
	bb.rewind();
	channel.send(null, bb);
    }

    /**
     * Sends a <code>Collection</code> of player characters that a
     * client may choose from when selecting which character to play.
     * This message is sent when the client is in the Lobby game
     * state.
     *
     * @param stats the collection of character statistics
     */
    public static void sendPlayableCharacters(ClientSession session,
					      Collection<CharacterStats> stats) {

	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	DataOutputStream dos = new DataOutputStream(baos);	    
	
	try {
	    for (CharacterStats s : stats) {
		char[] nm = s.getName().toCharArray();
		dos.writeInt(nm.length);
		for (char c : nm)
		    dos.writeChar(c);
		dos.writeInt(s.getStrength());
		dos.writeInt(s.getIntelligence());
		dos.writeInt(s.getDexterity());
		dos.writeInt(s.getWisdom());
		dos.writeInt(s.getConstitution());
		dos.writeInt(s.getCharisma());
		dos.writeInt(s.getHitPoints());
		dos.writeInt(s.getMaxHitPoints());
	    }
	    dos.close();
	    baos.close();
	} catch (IOException ioe) {
	    // this should never happen, but log in case it ever
	    // does
	    logger.severe("unexpected error: " + ioe);
	}

	byte[] data = baos.toByteArray();
	ByteBuffer bb = ByteBuffer.allocate(8 + data.length);
	bb.putInt(Commands.encode(Command.NOTIFY_PLAYABLE_CHARACTERS));
	bb.putInt(stats.size());
	bb.put(data);
	bb.rewind();

	session.send(bb);
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

	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	DataOutputStream dos = new DataOutputStream(baos);	    
	
	try {
	    int width = board.getWidth();
	    int height = board.getHeight();

	    dos.writeInt(width);
	    dos.writeInt(height);

	    // as a part of this command, we assume that the client
	    // will know that we are sending all the squares in this
	    // order
	    for (int i = 0; i < width; ++i) {
		for (int j = 0; j < height; ++j) {
		    BoardSpace s = board.getAt(i, j);
		    writeBoardSpace(dos, s, false);
		}
	    }

	    dos.close();
	    baos.close();
	} catch (IOException ioe) {
	    // this should never happen, but log in case it ever
	    // does
	    logger.severe("unexpected error: " + ioe);
	}
	
	byte[] data = baos.toByteArray();

	ByteBuffer bb = ByteBuffer.allocate(4 + data.length);
	bb.putInt(Commands.encode(Command.NEW_BOARD));
	bb.put(data);
	bb.rewind();

	session.send(bb);
    }

    /**
     * Sends updates about a <code>Collection</code> of spaces.
     *
     * @param spaces the spaces that are being updated
     */
    public static void broadcastBoardUpdate(Channel channel,
					    Collection<BoardSpace> spaces) {
	
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	DataOutputStream dos = new DataOutputStream(baos);	    

	try {
	    for (BoardSpace s : spaces) {
		writeBoardSpace(dos, s, true);
	    }
	    dos.close();
	    baos.close();
	} catch (IOException ioe) {
	    // this should never happen, but log in case it ever
	    // does
	    logger.severe("unexpected error: " + ioe);
	}

	byte[] data = baos.toByteArray();

	ByteBuffer bb = ByteBuffer.allocate(8 + data.length);
	bb.putInt(Commands.encode(Command.UPDATE_BOARD_SPACES));
	bb.putInt(spaces.size());
	bb.put(data);
	bb.rewind();

	channel.send(null, bb);
    }


    private static void writeBoardSpace(DataOutputStream dos,
					BoardSpace s, boolean writeCoords) 
	throws IOException {

	if (writeCoords) {
	    dos.writeInt(s.getX());
	    dos.writeInt(s.getY());
	}

	// floor
	int encodedFloorType = 
	    RoomInfo.encodeFloorType(s.getFloorType());
	dos.writeInt(encodedFloorType);
	
	// item.  If the item was null, then write 0 for the length of
	// the name.  The client will recognize this as no item
	// present.
	Item item = s.getItem();
	if (item == null)
	    dos.writeInt(0);
	else {
	    String itemName = item.getName();
	    dos.writeInt(itemName.length());
	    char[] arr = itemName.toCharArray();
	    for (char c : arr)
		dos.writeChar(c);
	    dos.writeLong(item.getItemId());
	    int encodedItemType = 
		ItemInfo.encodeItemType(item.getItemType());
	    dos.writeInt(encodedItemType);
	}
	
	// if no creature is present, then write 0 for the length of
	// the creature's name.  The client will recognize this as no
	// creature present
	Creature creature = s.getCreature();
	if (creature == null)
	    dos.writeInt(0);
	else {
	    String creatureName = creature.getName();
	    dos.writeInt(creatureName.length());
	    char[] arr = creatureName.toCharArray();
	    for (char c : arr)
		dos.writeChar(c);
	    dos.writeLong(creature.getCreatureId());
	    int encodedCreatureType = 
		CreatureInfo.encodeCreatureType(creature.getCreatureType());
	    dos.writeInt(encodedCreatureType);
	}

    } 

    /**
     * Sends a text message to the client. These are messages generated by
     * the game logic, not chat messages from other clients.
     *
     * @param message the message to send
     */
    public static void sendServerMessage(ClientSession session, String message) {
	ByteBuffer bb = ByteBuffer.allocate(8 + (message.length() * 2));

	bb.putInt(Commands.encode(Command.NEW_SERVER_MESSAGE));
	char[] arr = message.toCharArray();
	bb.putInt(arr.length);
	for (char c : arr)
	    bb.putChar(c);

	bb.rewind();
	session.send(bb);
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
    public static void sendCharacter(ClientSession session, 
				     CreatureType creatureType,
				     CharacterStats stats) {

	char[] nm = stats.getName().toCharArray();

	// 4 for command, 4 for creature type, 4 for name length,
	// 4 * 8 for stats, name length
	ByteBuffer bb = ByteBuffer.allocate(44 + (nm.length * 2));
	bb.putInt(Commands.encode(Command.NEW_CHARACTER_STATS));
	bb.putInt(CreatureInfo.encodeCreatureType(creatureType));
	bb.putInt(nm.length);	
	for (char c : nm)
	    bb.putChar(c);
	bb.putInt(stats.getStrength());
	bb.putInt(stats.getIntelligence());
	bb.putInt(stats.getDexterity());
	bb.putInt(stats.getWisdom());
	bb.putInt(stats.getConstitution());
	bb.putInt(stats.getCharisma());
	bb.putInt(stats.getHitPoints());
	bb.putInt(stats.getMaxHitPoints());

	bb.rewind();
	session.send(bb);
    }

}
