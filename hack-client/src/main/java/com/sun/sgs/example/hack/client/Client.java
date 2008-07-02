/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.client;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;

import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;

import com.sun.sgs.example.hack.client.gui.ChatPanel;
import com.sun.sgs.example.hack.client.gui.CreatorPanel;
import com.sun.sgs.example.hack.client.gui.GamePanel;
import com.sun.sgs.example.hack.client.gui.LobbyPanel;
import com.sun.sgs.example.hack.client.gui.PasswordDialog;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Container;
import java.awt.Window;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.math.BigInteger;
import java.net.PasswordAuthentication;
import java.nio.ByteBuffer;

import java.util.Properties;

import javax.swing.JFrame;
import javax.swing.JPanel;


/**
 * This is the main class for the client app. It creates the connection
 * with the server, sets up the GUI elements, and listens for the major
 * events from the server game app.
 */
public class Client extends JFrame implements SimpleClientListener {

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

    private static final long serialVersionUID = 1;

    // the simple client connection
    private SimpleClient client;

    // the gui and message handlers for interaction with the lobby
    private LobbyManager lmanager;
    private LobbyPanel lobbyPanel;
    private LobbyChannelListener llistener;

    // the gui and message handlers for interacting with character creation
    private CreatorManager crmanager;
    private CreatorPanel creatorPanel;
    private CreatorChannelListener crListener;

    // the gui and messages handlers for chatting
    private ChatManager cmanager;
    private ChatPanel chatPanel;

    // the gui and message handlers for interaction with a dungeon
    private GameManager gmanager;
    private GamePanel gamePanel;
    private DungeonChannelListener dlistener;

    // the card layout manager for swapping between different panels
    private CardLayout managerLayout;
    private JPanel managerPanel;

    /**
     * Creates an instance of <code>Client</code>. This sets up the GUI
     * elements and the state for talking with the game server, but does not
     * establish a connection.
     *
     * @throws Exception if there is any problem with the initial setup
     */
    public Client() throws Exception {
        super("Hack 0.4");

        // listen for events on the root window
        addWindowListener(new BasicWindowMonitor());

        // create the managers
        lmanager = new LobbyManager();
        crmanager = new CreatorManager();
        cmanager = new ChatManager();
        gmanager = new GameManager();

        // setup the listeners to handle communication from the game app
        llistener = new LobbyChannelListener(lmanager, cmanager);
        crListener = new CreatorChannelListener(crmanager, cmanager);
        dlistener = new DungeonChannelListener(gmanager, cmanager, gmanager);

        Container c = getContentPane();
        c.setLayout(new BorderLayout());

        // create the GUI panels used for the game elements
        lobbyPanel = new LobbyPanel(lmanager);
        creatorPanel = new CreatorPanel(crmanager);
        gamePanel = new GamePanel(gmanager);
        chatPanel = new ChatPanel(cmanager, gamePanel);

        // setup a CardLayout for the game and lobby panels, so we can
        // easily switch between them
        managerLayout = new CardLayout();
        managerPanel = new JPanel(managerLayout);
        managerPanel.add(new JPanel(), "blank");
        managerPanel.add(lobbyPanel, "lobby");
        managerPanel.add(creatorPanel, "creator");
        managerPanel.add(gamePanel, "game");

        c.add(managerPanel, BorderLayout.CENTER);
        c.add(chatPanel, BorderLayout.SOUTH);

        // setup the client connection
        client = new SimpleClient(this);
        lmanager.setClient(client);
        crmanager.setClient(client);
        gmanager.setClient(client);
	
	// we start off the client in the create state
	state = State.CREATE;
    }

    /**
     * Tries to connect to the game server.
     *
     * @throws Exception if the connection fails
     */
    public void connect() throws Exception {
        client.login(System.getProperties());
    }

    public PasswordAuthentication getPasswordAuthentication() {
        PasswordDialog pd = new PasswordDialog(this, "Name", "Password");
        pd.pack();
        pd.setVisible(true);

        return new PasswordAuthentication(pd.getLogin(), pd.getPassword());
    }

    public void loggedIn() {
        System.out.println("logged in");
    }

    public void loginFailed(String reason) {
        System.out.println("Login failed: " + reason);
    }

    public void disconnected(boolean graceful, String reason) {
	System.out.println("disconnected: " + reason);
    }
    public void reconnecting() {}
    public void reconnected() {}


    /**
     * Called when the client joins a communication channel. In this game
     * the client is never on more than one channel at a time, so this
     * is used to switch between states.
     *
     * @param channel the channel that we joined
     */
    public ClientChannelListener joinedChannel(ClientChannel channel) {
        // clear the chat area each time we join a new area
        chatPanel.clearMessages();

        // update the chat manager with the channel, so it knows where to
        // broadcast chat messages
        cmanager.setChannel(channel);

        // see which type of game we've joined, and based on this display
        // the right panel and set the appropriate listener to handle
        // messages from the server
        if (channel.getName().equals("game:lobby")) {
            // we joined the lobby
            lobbyPanel.clearList();
            managerLayout.show(managerPanel, "lobby");
            return llistener;
        } else if (channel.getName().equals("game:creator")) {
            // we joined the creator
            System.out.println("joined creator channel");
            managerLayout.show(managerPanel, "creator");
            return crListener;
        } else {
            // we joined some dungeon
            gamePanel.showLoadingScreen();
            managerLayout.show(managerPanel, "game");
            // request focus so all key presses are captured
            gamePanel.requestFocusInWindow();

            return dlistener;
        }
    }

    public void receivedMessage(ByteBuffer message) {
        // NOTE: This wasn't available in the EA API, so the Hack code
        // currently sends almost all messages from server to client on a
        // specific channel, but that design should probably change now

        // The only "direct" message is sent to the client to inform it
        // that of its session id. -JM

	if (chatPanel.getSessionId() == null) {

	    byte[] bytes = new byte[message.remaining()];
	    message.get(bytes);
	    BigInteger sessionId = new BigInteger(1, bytes);
	    chatPanel.setSessionId(sessionId);
	}
	else {
	    // peek at the command byte to determine what game state
	    // we're in
	    int command = (int)(message.get());

	    // rewind the mark so the listeners can't tell we peeked.
	    message.rewind();

	    if (command == 0 || command == 1 ||
		command == 8 || command == 9) {
		// stay in the current state
	    }
	    else 
		state = stateTable[command];	    

	    switch (state) {
	    case CREATE:		
		crListener.receivedMessage(null, message);
		break;
	    case LOBBY:
		llistener.receivedMessage(null, message);
		break;
	    case DUNGEON:
		dlistener.receivedMessage(null, message);
		break;
	    default:
		// NOTE: in the event of an unknown message type, the
		//       client should handle the message more
		//       gracefully.
		System.out.println("unhandled state: " + state);
	    }
	}
    }

    /**
     * The main-line for the client app.
     *
     * @param args command-line arguments, which are ignored
     */
    public static void main(String [] args) throws Exception {
        Client client = new Client();
        client.pack();
        client.setVisible(true);

        client.connect();
    }

    /**
     * Simple window monitor that quits the program when the main window
     * is closed.
     */
    class BasicWindowMonitor extends WindowAdapter {
        public void windowClosing(WindowEvent e) {
			Window w = e.getWindow();
			w.setVisible(false);
			w.dispose();
			System.exit(0);
		}
    }

}
