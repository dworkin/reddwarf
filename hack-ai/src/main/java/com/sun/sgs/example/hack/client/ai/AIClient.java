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

import com.sun.sgs.example.hack.client.ChatManager;
import com.sun.sgs.example.hack.client.CreatorChannelListener;
import com.sun.sgs.example.hack.client.CreatorListener;
import com.sun.sgs.example.hack.client.CreatorManager;
import com.sun.sgs.example.hack.client.DungeonChannelListener;
import com.sun.sgs.example.hack.client.GameManager;
import com.sun.sgs.example.hack.client.LobbyChannelListener;
import com.sun.sgs.example.hack.client.LobbyManager;

import com.sun.sgs.example.hack.share.CharacterStats;

import java.math.BigInteger;

import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;

import java.util.Timer;
import java.util.TimerTask;

/**
 * A barebones reference implementation of an AI client.  Developers
 * may extend this class as necessary to add additional features.
 */
public class AIClient implements SimpleClientListener {


    /**
     * The possible states the client could be in.  
     */
    private enum State {
	CREATE,
	LOBBY,
	DUNGEON
    }

    /**
     * The current state of the server which determine the handler
     * that gets the incoming message from the server
     */
    private State state;

    /**
     * A lookup table for determining the state based on the type of
     * message seen.
     */
    private static final State[] stateTable = new State[25];

    static {
	// NOTE: we start in state CREATE, and once we transition from
	// there, we can never go back, so no lookup should result in
	// State.CREATE.

	stateTable[11] = State.LOBBY;
	stateTable[12] = State.LOBBY;
	stateTable[13] = State.LOBBY;
	stateTable[14] = State.LOBBY;
	stateTable[15] = State.LOBBY;

	stateTable[21] = State.DUNGEON;
	stateTable[22] = State.DUNGEON;
	stateTable[23] = State.DUNGEON;
	stateTable[24] = State.DUNGEON;
    }


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
                runDelayed(new Runnable() {
                        public void run() {
                            creatorManager.createCurrentCharacter(name);
                        }
                    }, 500);
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
        dungeonChannelListener =
            new DungeonChannelListener(dungeonManager, chatManager,
                                       dungeonManager);
        aiDungeonListener = new AIDungeonListener(dungeonManager, name);
        dungeonManager.addBoardListener(aiDungeonListener);
        dungeonManager.addPlayerListener(aiDungeonListener);

        simpleClient = new SimpleClient(this);
        lobbyManager.setClient(simpleClient);
        creatorManager.setClient(simpleClient);
        dungeonManager.setClient(simpleClient);

	state = State.CREATE;
    }

    public PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(name, "".toCharArray());
    }

    private static final class DummyClientChannelListener 
	implements ClientChannelListener {

	public void leftChannel(ClientChannel channel) {

	}

	public void receivedMessage(ClientChannel c, ByteBuffer mesg) {
// 	    System.out.printf("Receved %d bytes on %s%n",
// 			      mesg.remaining(), c.getName());
	}

    }

    public ClientChannelListener joinedChannel(ClientChannel channel) {
        chatManager.setChannel(channel);
        if (channel.getName().equals("game:lobby")) {
            aiDungeonListener.leftDungeon();
            aiLobbyListener.enteredLobby();
            //return lobbyChannelListener;
        } else if (channel.getName().equals("game:creator")) {
            runDelayed(new Runnable() {
                    public void run() {
                        creatorManager.rollForStats(42);
                    }
                }, 250);
            //return creatorChannelListener;
        } else {
            aiDungeonListener.enteredDungeon();
            //return dungeonChannelListener;
        }
	return new DummyClientChannelListener();
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

// 	System.out.printf("%n%s received %d bytes%n", this, message.remaining());

	if (sessionId == null) {
	    byte[] bytes = new byte[message.remaining()];
	    message.get(bytes);
	    sessionId = new BigInteger(1, bytes);
	}
	else {
	    // peek at the command byte to determine what game state
	    // we're in
// 	    int command = (int)(message.get());

// 	    // rewind the mark so the listeners can't tell we peeked.
// 	    message.rewind();
	    int command = message.get(message.position());

	    if (command == 0 || command == 1 ||
		command == 8 || command == 9) {
		// stay in the current state
	    }
	    else 
		state = stateTable[command];	    

//   	    System.out.printf("%s received command %d, and is now in state %s%n",
//   			      this, command, state);

	    switch (state) {
	    case CREATE:		
		creatorChannelListener.receivedMessage(null, message);
		break;
	    case LOBBY:
		lobbyChannelListener.receivedMessage(null, message);
		break;
	    case DUNGEON:
		dungeonChannelListener.receivedMessage(null, message);
		break;
	    default:
		// NOTE: in the event of an unknown message type, the
		//       client should handle the message more
		//       gracefully.
		System.out.println("unhandled state: " + state);
	    }
	}
    }
    
    public String toString() {
	return name;
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
	    Thread.sleep(1000); 
        }
    }

}
