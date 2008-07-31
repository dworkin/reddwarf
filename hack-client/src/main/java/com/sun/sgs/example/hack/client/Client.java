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


import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.BoardSpace;
import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.Commands;
import com.sun.sgs.example.hack.share.Commands.Command;
import com.sun.sgs.example.hack.share.GameMembershipDetail;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Container;
import java.awt.Image;
import java.awt.Window;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

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

import javax.swing.JFrame;
import javax.swing.JPanel;


/**
 * This is the main class for the client app. It creates the connection
 * with the server, sets up the GUI elements, and listens for the major
 * events from the server game app.
 */
public class Client extends JFrame implements SimpleClientListener {

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
    private PasswordDialog pd;
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
    }

    /**
     * Tries to connect to the game server.
     *
     * @throws Exception if the connection fails
     */
    public void connect() throws Exception {
	pd = new PasswordDialog(this, "Name", "Password") {

		@Override
		    public void connect(String login, char[] pass) {
		    try {
			setupClient();
			client.login(System.getProperties());
		    } catch (IOException ex) {
			throw new RuntimeException(ex);
		    }
		}
	    };
	pd.pack();
	pd.setVisible(true);

	if (pd.isCancel()) {
	    setVisible(false);
	    dispose();
	}
    }

    private void setupClient() {
        // setup the client connection
        client = new SimpleClient(this);
        lmanager.setClient(client);
        crmanager.setClient(client);
        gmanager.setClient(client);
    }
    
    public PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(pd.getLogin(), pd.getPassword());
    }

    public void loggedIn() {
	pd.setVisible(false);
	pd.dispose();
	pd = null;
    }

    public void loginFailed(String reason) {
	pd.setConnectionFailed(reason);
    }

    public void disconnected(boolean graceful, String reason) {
	pd.setConnectionFailed(reason);
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
            // lobbyPanel.clearList();
            System.out.println("joined lobby channel");
            managerLayout.show(managerPanel, "lobby");
            return llistener;
        } 
	else if (channel.getName().equals("game:creator")) {
            // we joined the creator
            System.out.println("joined creator channel");
            managerLayout.show(managerPanel, "creator");
            return crListener;
        } 
	else {
            // we joined some dungeon
            gamePanel.showLoadingScreen();
	    System.out.println("joined " + channel.getName() + " channel");
            managerLayout.show(managerPanel, "game");
            // request focus so all key presses are captured
            gamePanel.requestFocusInWindow();

            return dlistener;
        }
    }

    public void receivedMessage(ByteBuffer message) {


	if (chatPanel.getSessionId() == null) {
	    byte[] bytes = new byte[message.remaining()];
	    message.get(bytes);
	    BigInteger sessionId = new BigInteger(1, bytes);
	    chatPanel.setSessionId(sessionId);
	}
	else {
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
		    
		    chatPanel.addPlayerIdMappings(playerIdsToNames);
		    break;
	    
		/*
		 * When creating a new character, the server will send us
		 * new stats for the character.
		 */
		case NEW_CHARACTER_STATS:
		    Object[] idAndStats = (Object[])(getObject(message));
		    Integer id = (Integer)(idAndStats[0]);
		    CharacterStats stats = (CharacterStats)(idAndStats[1]);
		    crmanager.changeStatistics(id, stats);
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
			    lmanager.gameAdded(detail.getGame());
			    lmanager.playerCountUpdated(detail.getGame(),
							detail.getCount());
			} else {
			    // it's the lobby, so update the count
			    lmanager.playerCountUpdated(detail.getCount());
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
		    lmanager.setCharacters(characters);		    
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
		    gmanager.setSpriteMap(spriteSize,
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
		    gmanager.changeBoard(board);
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
		    gmanager.hearMessage(msg);
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
