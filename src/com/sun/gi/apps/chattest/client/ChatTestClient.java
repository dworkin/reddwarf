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

package com.sun.gi.apps.chattest.client;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowStateListener;
import java.io.File;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;

import javax.security.auth.callback.Callback;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientAlreadyConnectedException;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;
import com.sun.gi.utils.types.BYTEARRAY;
import com.sun.gi.utils.types.StringUtils;

/**
 * <p>
 * The ChatTestClient implements a simple chat program using a Swing UI,
 * mainly for server channel testing purposes. It allows the user to
 * open arbitrary channels by name, or to use the Direct Client to
 * Client (DCC) channel.
 * </p>
 * 
 * <p>
 * Before connecting to any channels, the user must login to the server
 * via a UserManager. When the Login button is pressed, a list of
 * available UserManagers is displayed. (Right now the TCPIPUserManager
 * is the only implemented option). After selecting a UserManager, the
 * user is prompted for credentials (currently "Guest" with a blank
 * password will do a default login).
 * </p>
 * 
 * <p>
 * Once logged in, the user can open a new channel, or join an existing
 * channel by clicking the "Open Channel" button and typing in the
 * channel name. Once connected to a channel, a separate window opens
 * revealing the users connected to that channel as well as an area to
 * type messages. Each channel opened will appear in a new window.
 * </p>
 * 
 * <p>
 * A user can send a message directly to the server via the "Send to
 * Server" button. Messages are sent to the server via the
 * ClientConnectionManager.
 * </p>
 * 
 * <p>
 * A user can send a Direct Client to Client message via the "Send
 * Multi-DCC" button. This will send a multicast message on the
 * well-known DCC channel.
 * </p>
 * 
 * <p>
 * This class implements ClientConnectionManagerListener so that it can
 * receive and respond to connection events from the server.
 * </p>
 * 
 * <p>
 * The ChatTestClient is designed to work with the server application,
 * ChatTest. This application must be installed and running on the
 * server for the ChatTestClient to successfully login. Multiple
 * instances of this program can be run so that multiple users will be
 * shown in the client.
 * </p>
 */
public class ChatTestClient extends JFrame
        implements ClientConnectionManagerListener {

    private static final long serialVersionUID = 1L;

    JButton loginButton;
    JButton openChannelButton;
    JLabel statusMessage;
    JDesktopPane desktop;

    JList userList; // a list of users currently connected to the
                    // ChatTest app on the server.

    ClientConnectionManager mgr; // used for communication with the server.

    ClientChannel dccChannel; // the well-known channel for Direct
                              // Client to Client communication.

    private JButton dccButton;
    private JButton serverSendButton;
    private static String DCC_CHAN_NAME = "__DCC_Chan";

    public ChatTestClient() {
        // build interface
        super();
        setTitle("Chat Test Client");
        Container c = getContentPane();
        c.setLayout(new BorderLayout());
        JPanel buttonPanel = new JPanel();
        JPanel southPanel = new JPanel();
        southPanel.setLayout(new GridLayout(0, 1));
        statusMessage = new JLabel("Status: Not Connected");
        southPanel.add(buttonPanel);
        southPanel.add(statusMessage);
        c.add(southPanel, BorderLayout.SOUTH);
        desktop = new JDesktopPane();
        c.add(desktop, BorderLayout.CENTER);
        JPanel eastPanel = new JPanel();
        eastPanel.setLayout(new BorderLayout());
        eastPanel.add(new JLabel("Users"), BorderLayout.NORTH);
        userList = new JList(new DefaultListModel());
        userList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        userList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent evt) {
                if (evt.getClickCount() > 1) {
                    BYTEARRAY ba = (BYTEARRAY) userList.getSelectedValue();
                    if (ba != null) {
                        String message = JOptionPane.showInputDialog(
                                ChatTestClient.this, "Enter private message:");
                        doDCCMessage(ba.data(), message);
                    }
                }

            }
        });

        userList.setCellRenderer(new ListCellRenderer() {
            JLabel text = new JLabel();

            public Component getListCellRendererComponent(JList arg0,
                    Object arg1, int arg2, boolean arg3, boolean arg4) {
                byte[] data = ((BYTEARRAY) arg1).data();
                text.setText(StringUtils.bytesToHex(data, data.length - 4));
                return text;
            }
        });
        eastPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
        c.add(eastPanel, BorderLayout.EAST);
        buttonPanel.setLayout(new GridLayout(1, 0));
        loginButton = new JButton("Login");

        // The login button will attempt to login to the ChatTest
        // application on the server.
        // It must do this via an UserManager. The
        // ClientConnectionManager is used to
        // return an array of valid UserManager class names. Once
        // selected, the ClientConnectionManager
        // will attempt to connect via this UserManager.

        loginButton.addActionListener(new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                if (loginButton.getText().equals("Login")) {
                    loginButton.setEnabled(false);
                    String[] classNames = mgr.getUserManagerClassNames();
                    String choice = (String) JOptionPane.showInputDialog(
                            ChatTestClient.this, "Choose a user manager",
                            "User Manager Selection",
                            JOptionPane.INFORMATION_MESSAGE, null, classNames,
                            classNames[0]);
                    try {
                        mgr.connect(choice);
                    } catch (ClientAlreadyConnectedException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                        System.exit(1);
                    }
                } else {
                    mgr.disconnect();
                }

            }
        });

        // Opens a channel by name on the server. If the channel doesn't
        // exist,
        // it will be opened. In either case, it will attempt to join
        // the user
        // to the specified channel.
        openChannelButton = new JButton("Open Channel");
        openChannelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String channelName = JOptionPane.showInputDialog(
                        ChatTestClient.this, "Enter channel name");
                mgr.openChannel(channelName);
            }
        });
        openChannelButton.setEnabled(false);

        // Sends a message directly to the server.
        serverSendButton = new JButton("Send to Server");
        serverSendButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                Object[] targets = userList.getSelectedValues();
                if (targets != null) {
                    String message = JOptionPane.showInputDialog(
                            ChatTestClient.this, "Enter server message:");
                    doServerMessage(message);
                }
            }
        });
        serverSendButton.setEnabled(false);

        // sends a mulicast message to the selected users on the DCC
        // channel.
        dccButton = new JButton("Send Multi-DCC ");
        dccButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent arg0) {
                Object[] targets = userList.getSelectedValues();
                if (targets != null) {
                    String message = JOptionPane.showInputDialog(
                            ChatTestClient.this, "Enter private message:");
                    byte[][] targetList = new byte[targets.length][];
                    for (int i = 0; i < targets.length; i++) {
                        targetList[i] = ((BYTEARRAY) targets[i]).data();
                    }
                    doMultiDCCMessage(targetList, message);
                }
            }
        });
        dccButton.setEnabled(false);
        buttonPanel.add(loginButton);
        buttonPanel.add(openChannelButton);
        buttonPanel.add(serverSendButton);
        buttonPanel.add(dccButton);
        pack();
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // start connection process. The ClientConnectionManager is the
        // central point of server communication for the client. The
        // ClientConnectionManager needs to know the application name to
        // attempt to connect to on the server and how to find it. In
        // this case the app name is "ChatTest" and FakeDiscovery.xml
        // lists the valid UserManagers.
        try {
            mgr = new ClientConnectionManagerImpl("ChatTest",
                    new URLDiscoverer(
                            new File("FakeDiscovery.xml").toURI().toURL()));
            // mgr = new ClientConnectionManagerImpl("ChatTest",
            //     new URLDiscoverer(
            //         new URL("http://yourhost.example.com/discovery.xml")));

            mgr.setListener(this);

        } catch (MalformedURLException e) {
            e.printStackTrace();
            System.exit(2);
        }

        // When the window closes, disconnect from the manager.
        this.addWindowStateListener(new WindowStateListener() {
            public void windowStateChanged(WindowEvent arg0) {
                if (arg0.getNewState() == WindowEvent.WINDOW_CLOSED) {
                    mgr.disconnect();
                }
            }
        });
        setVisible(true);
    }

    /**
     * Sends a message to the server via the ClientConnectionManager.
     * 
     * @param message the message to send.
     */
    protected void doServerMessage(String message) {
        ByteBuffer out = ByteBuffer.allocate(message.length());
        out.put(message.getBytes());
        mgr.sendToServer(out, true);

    }

    /**
     * Sends a multicast message on the DCC channel.
     * 
     * @param targetList the list of users to send to
     * @param message the message to send
     */
    protected void doMultiDCCMessage(byte[][] targetList, String message) {
        ByteBuffer out = ByteBuffer.allocate(message.length());
        out.put(message.getBytes());
        dccChannel.sendMulticastData(targetList, out, true);

    }

    /**
     * Sends a message to a single user on the DCC channel.
     * 
     * @param target the user to send to.
     * @param message the message to send.
     */
    protected void doDCCMessage(byte[] target, String message) {
        ByteBuffer out = ByteBuffer.allocate(message.length());
        out.put(message.getBytes());
        dccChannel.sendUnicastData(target, out, true);

    }

    /**
     * ClientConnectionManagerListener callback.
     * 
     * Called by the ClientConnectionManager to request validation
     * credentials. The ValidatorDialog takes over from here to populate
     * the CallBacks with user data.
     * 
     * In the case of ChatTest, both a username and password are
     * required.
     * 
     * @param callbacks the array of
     * javax.security.auth.callbacks.CallBacks to validate against.
     */
    public void validationRequest(Callback[] callbacks) {
        statusMessage.setText("Status: Validating...");
        new ValidatorDialog(this, callbacks);
        mgr.sendValidationResponse(callbacks);
    }

    /**
     * ClientConnectionManagerListener callback.
     * 
     * Called by the ClientConnectionManager when the login is
     * successful.
     * 
     * @param myID the user's id. Used for future communication with the
     * server.
     */
    public void connected(byte[] myID) {
        statusMessage.setText("Status: Connected");
        setTitle("Chat Test Client: " + StringUtils.bytesToHex(myID));
        loginButton.setText("Logout");
        loginButton.setEnabled(true);
        openChannelButton.setEnabled(true);
        dccButton.setEnabled(true);
        serverSendButton.setEnabled(true);
        mgr.openChannel(DCC_CHAN_NAME);
    }

    /**
     * ClientConnectionManagerListener callback.
     * 
     * Called by the ClientConnectionManager if the login attempt
     * failed.
     * 
     * @param message the reason for failure.
     */
    public void connectionRefused(String message) {
        statusMessage.setText("Status: Connection refused. (" + message + ")");
        loginButton.setText("Login");
        loginButton.setEnabled(true);
    }

    /**
     * ClientConnectionManagerListener callback.
     * 
     * Called by ClientConnectionManager when the user is disconnected.
     * 
     */
    public void disconnected() {
        statusMessage.setText("Status: logged out");
        loginButton.setText("Login");
        loginButton.setEnabled(true);
        openChannelButton.setEnabled(false);
        dccButton.setEnabled(false);
        serverSendButton.setEnabled(false);
    }

    /**
     * ClientConnectionManagerListener callback.
     * 
     * Called by the ClientConnectionManager when a new user joins the
     * application. The new user is added to the user list on the right
     * side.
     * 
     * @param userID the new user
     */
    public void userJoined(byte[] userID) {
        DefaultListModel mdl = (DefaultListModel) userList.getModel();
        mdl.addElement(new BYTEARRAY(userID));
        userList.repaint();

    }

    /**
     * ClientConnectionManagerListener callback.
     * 
     * Called by the ClientConnectionManager when a user leaves the
     * application. The user is removed from the user list on the right
     * side.
     * 
     * @param userID the user that left.
     */
    public void userLeft(byte[] userID) {
        DefaultListModel mdl = (DefaultListModel) userList.getModel();
        mdl.removeElement(new BYTEARRAY(userID));
        userList.repaint();
    }

    /**
     * ClientConnectionManagerListener callback.
     * 
     * Called by the ClientConnectionManager as confirmation that the
     * user joined the given channel. If the channel is the DCC channel,
     * a ClientChannelListener will be attached to receive data from it.
     * 
     * @param channel the channel to which the user was joined.
     */
    public void joinedChannel(ClientChannel channel) {
        if (channel.getName().equals(DCC_CHAN_NAME)) {
            dccChannel = channel;
            dccChannel.setListener(new ClientChannelListener() {

                public void playerJoined(byte[] playerID) {}

                public void playerLeft(byte[] playerID) {}

                public void dataArrived(byte[] from, ByteBuffer data,
                        boolean reliable) {
                    byte[] bytes = new byte[data.remaining()];
                    data.get(bytes);
                    JOptionPane.showMessageDialog(ChatTestClient.this,
                            new String(bytes), "Message from "
                                    + StringUtils.bytesToHex(from,
                                            from.length - 4),
                            JOptionPane.INFORMATION_MESSAGE);

                }

                public void channelClosed() {}
            });
        } else {
            ChatChannelFrame cframe = new ChatChannelFrame(channel);
            desktop.add(cframe);
            desktop.repaint();
        }
    }

    /**
     * Starting point for the client app.
     * 
     * @param args
     */
    public static void main(String[] args) {
        new ChatTestClient();

    }

    public void failOverInProgress() {
        // TODO Auto-generated method stub
    }

    public void reconnected() {
        // TODO Auto-generated method stub
    }

    /**
     * This method is called whenever an attempted join/leave fails due
     * to the target channel being locked.
     * 
     * @param channelName the name of the channel.
     * @param userID the ID of the user attemping to join/leave
     */
    public void channelLocked(String channelName, byte[] userID) {
        System.err.println("ChatTestClient received locked notification: " +
                channelName);
    }
}
