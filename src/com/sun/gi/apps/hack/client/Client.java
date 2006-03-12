
/*
 * Client.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Fri Feb 17, 2006	 6:05:34 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client;

import com.sun.gi.comm.discovery.impl.URLDiscoverer;

import com.sun.gi.comm.routing.UserID;

import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;

import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;

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
 *
 */
public class Client extends JFrame implements ClientConnectionManagerListener
{

    /**
     *
     */
    public static final byte [] SERVER_UID = UserID.SERVER_ID.toByteArray();

    //
    private ClientConnectionManager connManager;

    //
    private LobbyManager lmanager;
    private LobbyPanel lobbyPanel;

    //
    private ChatManager cmanager;
    private ChatPanel chatPanel;

    //
    private GameManager gmanager;
    private GamePanel gamePanel;

    //
    private LobbyChannelListener llistener;

    //
    private DungeonChannelListener dlistener;

    // 
    private CardLayout managerLayout;
    private JPanel managerPanel;

    /**
     *
     */
    public Client() throws Exception {
        super("SGS Demo Game 0.1");

        addWindowListener(new BasicWindowMonitor());

        lmanager = new LobbyManager();
        cmanager = new ChatManager();
        gmanager = new GameManager();

        llistener = new LobbyChannelListener(lmanager, cmanager);
        dlistener = new DungeonChannelListener(gmanager, cmanager, gmanager);

        Container c = getContentPane();
        c.setLayout(new BorderLayout());

        lobbyPanel = new LobbyPanel(lmanager);
        gamePanel = new GamePanel(gmanager);
        chatPanel = new ChatPanel(cmanager, gamePanel);

        managerLayout = new CardLayout();
        managerPanel = new JPanel(managerLayout);
        managerPanel.add(lobbyPanel, "a");
        managerPanel.add(gamePanel, "b");

        c.add(managerPanel, BorderLayout.CENTER);
        c.add(chatPanel, BorderLayout.SOUTH);

        URL url = new URL("file:FakeDiscovery.xml");
        connManager =
            new ClientConnectionManagerImpl("Hack",
                                            new URLDiscoverer(url));
        connManager.setListener(this);
        lmanager.setConnectionManager(connManager);
        gmanager.setConnectionManager(connManager);
    }

    /**
     *
     */
    public void connect() throws Exception {
        System.out.println("connect");
        String [] classNames = connManager.getUserManagerClassNames();
        connManager.connect(classNames[0]);
        System.out.println("finished connect");
    }

    /**
     *
     */
    public void validationRequest(Callback[] callbacks) {
        System.out.println("validation request");
        NameCallback nameCb = null;
        PasswordCallback passCb = null;

        for (int i = 0; i < callbacks.length; i++) {
            if (callbacks[i] instanceof NameCallback) {
                nameCb = (NameCallback)(callbacks[i]);
            } else if (callbacks[i] instanceof PasswordCallback) {
                passCb = (PasswordCallback)(callbacks[i]);
            }
        }

        PasswordDialog pd = new PasswordDialog(this, nameCb.getPrompt(),
                                               passCb.getPrompt());
        pd.pack();
        pd.setVisible(true);

        nameCb.setName(pd.getLogin());
        passCb.setPassword(pd.getPassword());

        connManager.sendValidationResponse(callbacks);
    }

    public void connected(byte[] myID) {}
    public void connectionRefused(String message) {System.out.println("refused");}
    public void failOverInProgress() {System.out.println("fail-over");}
    public void reconnected() {System.out.println("re-connected");}
    public void disconnected() {System.out.println("dis-connected");}

    public void userJoined(byte[] userID) {
        System.out.println("From Client: joined ");
        //cmanager.messageArrived(new String(userID), "*joined*");
    }

    public void userLeft(byte[] userID) {
        System.out.println("From Client: left");
        //cmanager.messageArrived(new String(userID), "*left*");
    }

    public void joinedChannel(ClientChannel channel) {
        System.out.println("joined channel: " + channel.getName());

        // clear the chat area, 'cause we're going to a new area
        chatPanel.clearMessages();

        // see which type of game we've joined, and install the right listener
        if (channel.getName().equals("game:lobby")) {
            lobbyPanel.clearList();
            channel.setListener(llistener);
            managerLayout.first(managerPanel);
        } else {
            gamePanel.showLoadingScreen();
            channel.setListener(dlistener);
            managerLayout.last(managerPanel);
            gamePanel.requestFocusInWindow();
        }

        // update the chat managers with the channel, so it knows where to
        // broadcast chat messages
        cmanager.setChannel(channel);
    }

    public void channelLocked(String chan, byte[] userID) {System.out.println("locked channel");}

    /**
     *
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
