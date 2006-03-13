/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */


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
