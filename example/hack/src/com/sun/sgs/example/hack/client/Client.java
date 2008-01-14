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

package com.sun.sgs.example.hack.client;

/*import com.sun.gi.comm.discovery.impl.URLDiscoverer;

import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;

import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;

import com.sun.gi.utils.SGSUUID;*/

import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;
import com.sun.sgs.client.util.UtilChannel;
import com.sun.sgs.client.util.UtilChannelListener;

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

/*import java.net.URL;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;*/

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

    /**
     * Creates an instance of <code>Client</code>. This sets up the GUI
     * elements and the state for talking with the game server, but does not
     * establish a connection.
     *
     * @throws Exception if there is any problem with the initial setup
     */
    public Client() throws Exception {
        super("SGS Demo Game 0.1");

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
        lmanager.setConnectionManager(client);
        crmanager.setConnectionManager(client);
        gmanager.setConnectionManager(client);
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
        System.err.println("logged in");
    }

    public void loginFailed(String reason) {
        System.out.println("Login failed: " + reason);
    }

    public void disconnected(boolean graceful, String reason) {}
    public void reconnecting() {}
    public void reconnected() {}


    /**
     * Called when the client joins a communication channel. In this game
     * the client is never on more than one channel at a time, so this
     * is used to switch between states.
     *
     * @param channel the channel that we joined
     */
    public UtilChannelListener joinedChannel(UtilChannel channel) {
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

        byte[] bytes = new byte[message.remaining()];
        message.get(bytes);
        BigInteger sessionId = new BigInteger(1, bytes);
        chatPanel.setSessionId(sessionId);
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
