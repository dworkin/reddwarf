/*
 Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 Clara, California 95054, U.S.A. All rights reserved.
 
 Sun Microsystems, Inc. has intellectual property rights relating to
 technology embodied in the product that is described in this document.
 In particular, and without limitation, these intellectual property rights
 may include one or more of the U.S. patents listed at
 http://www.sun.com/patents and one or more additional patents or pending
 patent applications in the U.S. and in other countries.
 
 U.S. Government Rights - Commercial software. Government users are subject
 to the Sun Microsystems, Inc. standard license agreement and applicable
 provisions of the FAR and its supplements.
 
 This distribution may include materials developed by third parties.
 
 Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered
 trademarks of Sun Microsystems, Inc. in the U.S. and other countries.
 
 UNIX is a registered trademark in the U.S. and other countries, exclusively
 licensed through X/Open Company, Ltd.
 
 Products covered by and information contained in this service manual are
 controlled by U.S. Export Control laws and may be subject to the export
 or import laws in other countries. Nuclear, missile, chemical biological
 weapons or nuclear maritime end uses or end users, whether direct or
 indirect, are strictly prohibited. Export or reexport to countries subject
 to U.S. embargo or to entities identified on U.S. export exclusion lists,
 including, but not limited to, the denied persons and specially designated
 nationals lists is strictly prohibited.
 
 DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS,
 REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT,
 ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE
 LEGALLY INVALID.
 
 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 California 95054, Etats-Unis. Tous droits réservés.
 
 Sun Microsystems, Inc. détient les droits de propriété intellectuels
 relatifs à la technologie incorporée dans le produit qui est décrit dans
 ce document. En particulier, et ce sans limitation, ces droits de
 propriété intellectuelle peuvent inclure un ou plus des brevets américains
 listés à l'adresse http://www.sun.com/patents et un ou les brevets
 supplémentaires ou les applications de brevet en attente aux Etats -
 Unis et dans les autres pays.
 
 Cette distribution peut comprendre des composants développés par des
 tierces parties.
 
 Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique
 ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans
 d'autres pays.
 
 UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
 licenciée exlusivement par X/Open Company, Ltd.
 
 see above Les produits qui font l'objet de ce manuel d'entretien et les
 informations qu'il contient sont regis par la legislation americaine en
 matiere de controle des exportations et peuvent etre soumis au droit
 d'autres pays dans le domaine des exportations et importations.
 Les utilisations finales, ou utilisateurs finaux, pour des armes
 nucleaires, des missiles, des armes biologiques et chimiques ou du
 nucleaire maritime, directement ou indirectement, sont strictement
 interdites. Les exportations ou reexportations vers des pays sous embargo
 des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion
 d'exportation americaines, y compris, mais de maniere non exclusive, la
 liste de personnes qui font objet d'un ordre de ne pas participer, d'une
 facon directe ou indirecte, aux exportations des produits ou des services
 qui sont regi par la legislation americaine en matiere de controle des
 exportations et la liste de ressortissants specifiquement designes, sont
 rigoureusement interdites.
 
 LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
 DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES,
 DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE
 GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE
 UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.
*/

package com.sun.gi.apps.hack.client;

import com.sun.gi.comm.discovery.impl.URLDiscoverer;

import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;

import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;

import com.sun.gi.utils.SGSUUID;

import com.sun.gi.apps.hack.client.gui.ChatPanel;
import com.sun.gi.apps.hack.client.gui.GamePanel;
import com.sun.gi.apps.hack.client.gui.LobbyPanel;
import com.sun.gi.apps.hack.client.gui.PasswordDialog;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Container;
import java.awt.Window;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.net.URL;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;

import javax.swing.JFrame;
import javax.swing.JPanel;


/**
 * This is the main class for the client app. It creates the connection
 * with the server, sets up the GUI elements, and listenes for the major
 * events from the server game app.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class Client extends JFrame implements ClientConnectionManagerListener
{
    /**
     * The identifier for the server's messages.
     */
    public static final SGSUUID SERVER_UID = ClientConnectionManager.SERVER_ID;

    // the connection manager used to handle incoming messages
    private ClientConnectionManager connManager;

    // the gui and message handlers for interaction with the lobby
    private LobbyManager lmanager;
    private LobbyPanel lobbyPanel;
    private LobbyChannelListener llistener;

    // the gui and messages handlers for chatting
    private ChatManager cmanager;
    private ChatPanel chatPanel;

    // the gui and message handlers for interaction with a dungeon
    private GameManager gmanager;
    private GamePanel gamePanel;
    private DungeonChannelListener dlistener;

    // the card layour manager for swapping between different panels
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
        cmanager = new ChatManager();
        gmanager = new GameManager();

        // setup the listeners to handle communication from the game app
        llistener = new LobbyChannelListener(lmanager, cmanager);
        dlistener = new DungeonChannelListener(gmanager, cmanager, gmanager);

        Container c = getContentPane();
        c.setLayout(new BorderLayout());

        // create the GUI panels used for the game elements
        lobbyPanel = new LobbyPanel(lmanager);
        gamePanel = new GamePanel(gmanager);
        chatPanel = new ChatPanel(cmanager, gamePanel);

        // setup a CardLayout for the game and lobby panels, so we can
        // easily switch between them
        managerLayout = new CardLayout();
        managerPanel = new JPanel(managerLayout);
        managerPanel.add(lobbyPanel, "a");
        managerPanel.add(gamePanel, "b");

        c.add(managerPanel, BorderLayout.CENTER);
        c.add(chatPanel, BorderLayout.SOUTH);

        // setup the connection details
        URL url = new URL("file:FakeDiscovery.xml");
        connManager =
            new ClientConnectionManagerImpl("Hack",
                                            new URLDiscoverer(url));
        connManager.setListener(this);
        lmanager.setConnectionManager(connManager);
        gmanager.setConnectionManager(connManager);
    }

    /**
     * Tries to connect to the game server.
     *
     * @throws Exception if the connection failes
     */
    public void connect() throws Exception {
        String [] classNames = connManager.getUserManagerClassNames();
        connManager.connect(classNames[0]);
    }

    /**
     * Called when the server needs credentials to authenticate the client.
     *
     * @param callbacks the credential mechanisms
     */
    public void validationRequest(Callback[] callbacks) {
        NameCallback nameCb = null;
        PasswordCallback passCb = null;

        // look in the callbacks for the details required...in our app, all
        // authentication is done with username-password pairs
        for (int i = 0; i < callbacks.length; i++) {
            if (callbacks[i] instanceof NameCallback) {
                nameCb = (NameCallback)(callbacks[i]);
            } else if (callbacks[i] instanceof PasswordCallback) {
                passCb = (PasswordCallback)(callbacks[i]);
            }
        }

        // prompt the user for their login and password...
        PasswordDialog pd = new PasswordDialog(this, nameCb.getPrompt(),
                                               passCb.getPrompt());
        pd.pack();
        pd.setVisible(true);

        // ...and retrieve their inputs
        nameCb.setName(pd.getLogin());
        passCb.setPassword(pd.getPassword());

        // finally, send a response to the authentication request
        connManager.sendValidationResponse(callbacks);
    }

    /**
     * Called when the client establishes a connection.
     *
     * @param myID the client's identifier
     */
    public void connected(byte[] myID) {
        
    }

    /**
     * Called when a connection attempt is refused.
     *
     * @param message an explaination of the refusal
     */
    public void connectionRefused(String message) {
        System.out.println("Connection refused: " + message);
    }

    /**
     * Called when a connection fail-over happens. This typically happens
     * when the server goes down and then comes back up.
     */
    public void failOverInProgress() {

    }

    /**
     * Called when the client has been re-connected to the server.
     */
    public void reconnected() {

    }

    /**
     * Called when the client is disconnected from the server.
     */
    public void disconnected() {

    }

    /**
     * Called when a user joins the game.
     *
     * @param userID the joining user's identifier
     */
    public void userJoined(byte[] userID) {

    }

    /**
     * Called when a user leaves the game.
     *
     * @param userID the leaving user's identifier
     */
    public void userLeft(byte[] userID) {

    }

    /**
     * Called when the client joins a communication channel. In this game
     * the client is never on more than one channel at a time, so this
     * is used to switch between states.
     *
     * @param channel the channel that we joined
     */
    public void joinedChannel(ClientChannel channel) {
        // clear the chat area each time we join a new area
        chatPanel.clearMessages();

        // see which type of game we've joined, and based on this display
        // the right panel and set the appropriate listener to handle
        // messages from the server
        if (channel.getName().equals("game:lobby")) {
            // we joined the lobby
            lobbyPanel.clearList();
            channel.setListener(llistener);
            managerLayout.first(managerPanel);
        } else {
            // we joined some dungeon
            gamePanel.showLoadingScreen();
            channel.setListener(dlistener);
            managerLayout.last(managerPanel);

            // request focus so all key presses are captured
            gamePanel.requestFocusInWindow();
        }

        // update the chat manager with the channel, so it knows where to
        // broadcast chat messages
        cmanager.setChannel(channel);
    }

    /**
     * Called when a channel that the client is on is locked. In this game
     * this method is never called, becase all channels are locked as soon
     * as they are created, and therefore before a client joins the channel.
     *
     * @param channel the channel that was locked
     * @param userID the user 
     */
    public void channelLocked(String channel, byte[] userID) {
        
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
