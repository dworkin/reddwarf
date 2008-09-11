/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.client.ai;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;

import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;

import com.sun.sgs.example.hack.client.BoardListener;
import com.sun.sgs.example.hack.client.ChatListener;
import com.sun.sgs.example.hack.client.ChatManager;
import com.sun.sgs.example.hack.client.CreatorChannelListener;
import com.sun.sgs.example.hack.client.CreatorListener;
import com.sun.sgs.example.hack.client.CreatorManager;
import com.sun.sgs.example.hack.client.DungeonChannelListener;
import com.sun.sgs.example.hack.client.GameManager;
import com.sun.sgs.example.hack.client.LobbyChannelListener;
import com.sun.sgs.example.hack.client.LobbyManager;
import com.sun.sgs.example.hack.client.PlayerListener;

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.BoardSpace;
import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.Commands;
import com.sun.sgs.example.hack.share.Commands.Command;
import com.sun.sgs.example.hack.share.GameMembershipDetail;

import java.awt.Image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

import java.math.BigInteger;

import java.net.PasswordAuthentication;

import java.nio.ByteBuffer;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;


import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;

import java.util.Timer;
import java.util.TimerTask;

/**
 * A barebones reference implementation of an AI client.  Developers
 * may extend this class as necessary to add additional features.
 */
public class AIClient implements SimpleClientListener {

    private final String name;

    private final ChatManager chatManager;

    private final LobbyChannelListener lobbyChannelListener;
    private final LobbyManager lobbyManager;
    private final AILobbyListener aiLobbyListener;

    private final CreatorChannelListener creatorChannelListener;
    private final CreatorManager creatorManager;

    private final DungeonChannelListener dungeonChannelListener;
    private final GameManager dungeonManager;
    private final AIDungeonListener aiDungeonListener;

    private final SimpleClient simpleClient;

    private static final Timer timer = new Timer();

    private BigInteger sessionId = null;

    private final CreatorListener creatorListener =
        new CreatorListener() {
            public void changeStatistics(int id, CharacterStats stats) {
		// This gets called everytime we roll a new character,
		// or when our stats change in the game (e.g. our hp
		// goes down).  For now, we don't do anything, as this
		// is more of a mind-less AI client.
            }
        };    

    public AIClient(String name) {
        this.name = name;

        chatManager = new ChatManager();
        chatManager.addChatListener(new AIChatListener(chatManager, name));

        lobbyManager = new LobbyManager();
        lobbyChannelListener =
            new LobbyChannelListener(lobbyManager, chatManager);
        aiLobbyListener = new AILobbyListener(lobbyManager, name);
        lobbyManager.addLobbyListener(aiLobbyListener);

        creatorManager = new CreatorManager();
        creatorChannelListener =
            new CreatorChannelListener(creatorManager, chatManager);
        creatorManager.addCreatorListener(creatorListener);

        dungeonManager = new GameManager();

        aiDungeonListener = new AIDungeonListener(dungeonManager, name);

        dungeonChannelListener =
            new AIDungeonChannelListener(dungeonManager, chatManager,
					 dungeonManager, aiDungeonListener);
        dungeonManager.addBoardListener(aiDungeonListener);
        dungeonManager.addPlayerListener(aiDungeonListener);

        simpleClient = new SimpleClient(this);
        lobbyManager.setClient(simpleClient);
        creatorManager.setClient(simpleClient);
        dungeonManager.setClient(simpleClient);

    }   

    public PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(name, "".toCharArray());
    }

    public ClientChannelListener joinedChannel(ClientChannel channel) {
        // chatManager.setChannel(channel);
        if (channel.getName().equals("game:lobby")) {
            aiDungeonListener.leftDungeon();
            aiLobbyListener.enteredLobby();
            return lobbyChannelListener;
        } else if (channel.getName().equals("game:creator")) {
	    creatorManager.rollForStats(42);
	    creatorManager.createCurrentCharacter(name);
            return creatorChannelListener;	    
        } 
	// REMINDER: this should change as we add new channels
	else {
            aiDungeonListener.enteredDungeon();
            return dungeonChannelListener;
        }
    }

    static void runDelayed(Runnable task, int delay) {
        timer.schedule(new DelayedTask(task), (long)delay);
    }

    private static class DelayedTask extends TimerTask {
        private final Runnable task;
        DelayedTask(Runnable task) {
            this.task = task;
        }
        public void run() {
            task.run();
        }
    }

    public void loggedIn() {
        System.out.println(name + ": logged in");
    }

    public void loginFailed(String reason) {
        System.out.println(name + ": login failed: " + reason);
    }

    public void disconnected(boolean graceful, String reason) {
	System.out.println(name + ": disconnected: " + reason);
    }

    public void reconnecting() {}
    public void reconnected() {}

    public void receivedMessage(ByteBuffer message) {

	if (sessionId == null) {

	    byte[] bytes = new byte[message.remaining()];
	    message.get(bytes);
	    sessionId = new BigInteger(1, bytes);	    
	    return;
	}

	// otherwise the message is a command from the server
	try {
	    int encodedCmd = (int)(message.getInt());
	    Command cmd = Commands.decode(encodedCmd);
	    
	    switch(cmd) {
		
	    /*
	     * When entering a new game state, the server will send us
	     * a bulk mapping of all the player-ids to their names.
	     */
	    case ADD_BULK_PLAYER_IDS:
		@SuppressWarnings("unchecked")
		    Map<BigInteger,String> playerIdsToNames = 
		    (Map<BigInteger,String>)(getObject(message));
		    
		// we currently don't do anything with the IDs.		   
		break;
	    
	    /*
	     * When creating a new character, the server will send us
	     * new stats for the character.
	     */
	    case NEW_CHARACTER_STATS:
		Object[] idAndStats = (Object[])(getObject(message));
		Integer id = (Integer)(idAndStats[0]);
		CharacterStats stats = (CharacterStats)(idAndStats[1]);
		creatorManager.changeStatistics(id, stats);
		break;

	    /*
	     * When we join the Lobby, the server will send us a
	     * message of all the available games
	     */
	    case UPDATE_AVAILABLE_GAMES: 
		// we were sent game membership updates
		@SuppressWarnings("unchecked")
		    Collection<GameMembershipDetail> details =
		    (Collection<GameMembershipDetail>)(getObject(message));
		for (GameMembershipDetail detail : details) {
		    // for each update, see if it's about the lobby
		    // or some specific dungeon
		    if (! detail.getGame().equals("game:lobby")) {
			// it's a specific dungeon, so add the game and
			// set the initial count
			lobbyManager.gameAdded(detail.getGame());
			lobbyManager.playerCountUpdated(detail.getGame(),
							detail.getCount());
		    } else {
			// it's the lobby, so update the count
			lobbyManager.playerCountUpdated(detail.getCount());
		    }
		}		    
		break;

	    /*
	     * When we join the lobby, the server will send us a
	     * message with all the characters that our player has.
	     */
	    case NOTIFY_PLAYABLE_CHARACTERS: 
		// we got updated with some character statistics...these
		// are characters that the client is allowed to play
		@SuppressWarnings("unchecked")
		    Collection<CharacterStats> characters =
		    (Collection<CharacterStats>)(getObject(message));
		lobbyManager.setCharacters(characters);		    
		break; 

	    /*
	     * When we first join a dungeon, the server will send us
	     * the sprite map that is used by the dungeon.
	     */
	    case NEW_SPRITE_MAP:
		// we were sent game membership updates
		Object[] sizeAndSprites = (Object[])(getObject(message));
		Integer spriteSize = (Integer)(sizeAndSprites[0]);
		@SuppressWarnings("unchecked")
		    Map<Integer,byte[]> spriteMap =
		    (Map<Integer,byte[]>)(sizeAndSprites[1]);
		dungeonManager.setSpriteMap(spriteSize,
					    convertMap(spriteMap));
		break;

	    /*
	     * When we join a dungeon or move between levels in a
	     * dungeon, the server will send us a full listing of all
	     * the board spaces for the current dungeon level .  This
	     * is essentially a client-directed, bulk update method
	     * similar to the UPDATE_BOARD_SPACES command.
	     */		
	    case NEW_BOARD:
		// we got a complete board update
		Board board = (Board)(getObject(message));
		dungeonManager.changeBoard(board);
		break;

	    /*
	     * The server will occassionaly send us text messages
	     * regarding the players state in the game.
	     */
	    case NEW_SERVER_MESSAGE:
		// we heard some message from the server
		byte [] bytes = new byte[message.remaining()];
		message.get(bytes);
		String msg = new String(bytes);
		dungeonManager.hearMessage(msg);
		break;

	    case UNHANDLED_COMMAND:
		Object[] channelNameAndCommand = (Object[])(getObject(message));
		String channelName = (String)(channelNameAndCommand[0]);
		Integer encodedCommand = (Integer)(channelNameAndCommand[1]);
		Command command = Commands.decode(encodedCommand);

		// The following conditional is a kludge to fix Issue
		// 22 where the AIClient think it is in a dungeon, but
		// has yet to be notified that it has left the
		// channel.  In this case, we have sent a MOVE_PLAYER
		// to the lobby, so we should stop trying to move and
		// wait to be notified of joining the lobby
		if (command.equals(Command.MOVE_PLAYER) &&
				   channelName.equals("game:lobby")) {
		    aiDungeonListener.leftDungeon();
		}
		else {
		    System.out.printf("%s send unhandled command %s to channel"+
				      "%s%n", this, command, channelName);
		}
		break;
		
	    default:
		System.out.printf("Received unknown command %s (%d) from the " +
				  "server%n", cmd, encodedCmd);	
		
	    }
	}
	catch (IOException ioe) {
	    System.out.println("IOException caught while processing " + 
			       "message from server");
	    ioe.printStackTrace();
	}
    }

    /**
     * Retrieves a serialized object from the given buffer.
     *
     * @param data the encoded object to retrieve
     */
    private static Object getObject(ByteBuffer data) throws IOException {
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
    
    /**
     * A private helper that converts the map from the server (that
     * maps integers to byte arrays) into the form needed on the
     * client (that maps integers to images). The server sends the
     * byte array form because images aren't serializable.
     */
    private static Map<Integer,Image> convertMap(Map<Integer,byte[]> map) {
	Map<Integer,Image> newMap = new HashMap<Integer,Image>();
	
	// for each of the identified sprites, try to load the bytes
	// as a recognizable image format and store in the new map
	for (int identifier : map.keySet()) {
	    try {
		ByteArrayInputStream in =
		    new ByteArrayInputStream(map.get(identifier));
		newMap.put(identifier, ImageIO.read(in));
	    } catch (IOException ioe) {
		System.out.println("Failed to convert image: " + identifier);
		ioe.printStackTrace();
	    }
	}
	
	return newMap;
    }
    
    
    public String toString() {
	return name;
    }

    /**
     * A private utility class for ensuring that the {@link
     * AIDungeonListener} is notified when this client is no longer in
     * the dungeon.
     */
    private static class AIDungeonChannelListener extends DungeonChannelListener {
	
	private final AIDungeonListener dungeonListener;

	public AIDungeonChannelListener(BoardListener boardListener,
					ChatListener chatListener,
					PlayerListener playerListener,
					AIDungeonListener dungeonListener) {
	    super (boardListener, chatListener, playerListener);
	    this.dungeonListener = dungeonListener;
	}

	/**
	 * Notifies the AIDungeonListener that it is no longer connected
	 *
	 * {@inheritDoc}
	 */
	public void leftChannel(ClientChannel channel) {
	    // This is currently unused due to how each level is now
	    // associated with a channel, but is left in as a reminder
	    // of  where to put the dungeon-exiting code

	    //dungeonListener.leftDungeon();
	}

    }

    public static void main(String [] args) throws Exception {
        if (args.length != 1) {
            System.out.println("Please specify the number of clients...");
            System.exit(0);
        }

	int numClients = Integer.valueOf(args[0]);

        for (int i = 0; i < numClients; i++) {

	    // choose a unique identifier that reflects both its
	    // creation order and has some random element to in in the
	    // even that multiple JVMs running this code are started
	    // and connect to the same host.
	    AIClient client = new AIClient("AIClient-" + i + "-" + 
					   (int)(Math.random() * 1000));
	    client.simpleClient.login(System.getProperties());
	    // Sleep briefly to avoid possibly overwhelming the server
	    // with simultaneous connections.
	    Thread.sleep(500); 
        }
    }

}
