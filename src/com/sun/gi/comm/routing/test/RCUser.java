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

package com.sun.gi.comm.routing.test;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.Callback;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.server.SGSUser;
import com.sun.gi.utils.types.BYTEARRAY;

public class RCUser extends JInternalFrame implements SGSUser {

    private static final long serialVersionUID = 1L;

    private JDesktopPane desktop;
    private Map<BYTEARRAY, RCChannelFrame> channelMap = new HashMap<BYTEARRAY, RCChannelFrame>();
    private JLabel status;
    private JList userList;
    private UserID myID;
    final JButton connectButton;
    private Router myRouter;
    private JButton openChanButton;

    public RCUser(Router router, String userName) {
        super(userName);
        myRouter = router;
        try {
			myID = new UserID();
		} catch (InstantiationException e1) {
			
			e1.printStackTrace();
		}
        setSize(200, 200);
        Container c = getContentPane();
        desktop = new JDesktopPane();
        c.setLayout(new BorderLayout());
        c.add(desktop, BorderLayout.CENTER);
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new GridLayout(0, 1));
        status = new JLabel("Status: Not connected");
        controlPanel.add(status);
        connectButton = new JButton("connect");
        controlPanel.add(connectButton);
        connectButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (connectButton.getText().equalsIgnoreCase("connect")) {
                    doConnect();
                } else if (connectButton.getText().equalsIgnoreCase(
                        "disconnect")) {
                    doDisconnect();
                }

            }

        });
        openChanButton = new JButton("open channel");
        controlPanel.add(openChanButton);
        openChanButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doOpenChan();
            }

        });
        openChanButton.setEnabled(false);
        c.add(controlPanel, BorderLayout.SOUTH);
        JPanel usersPanel = new JPanel();
        usersPanel.setLayout(new BorderLayout());
        JLabel userLabel = new JLabel("Users");
        usersPanel.add(userLabel, BorderLayout.NORTH);
        userList = new JList(new DefaultListModel());
        usersPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
        c.add(usersPanel, BorderLayout.EAST);
        this.setResizable(true);
        setVisible(true);

    }

    protected void doOpenChan() {
        String channelName = JOptionPane.showInputDialog(this,
                "Enter channel name");
        SGSChannel chan = myRouter.openChannel(channelName);
        chan.join(this);
    }

    protected void doDisconnect() {
        connectButton.setText("Disconnecting....");
        connectButton.setEnabled(false);
        openChanButton.setEnabled(false);
        myRouter.deregisterUser(this);
    }

    protected void doConnect() {
        try {
            myRouter.registerUser(this, null);
        } catch (InstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void joinedChan(SGSChannel chan) throws IOException {
        RCChannelFrame chanFrame = new RCChannelFrame(myID, chan);
        desktop.add(chanFrame);
        chanFrame.setSize(100, 100);
        chanFrame.setVisible(true);
        channelMap.put(new BYTEARRAY(chan.channelID().toByteArray()), chanFrame);
        chanFrame.userJoined(myID.toByteArray());
        desktop.repaint();
    }

    public void msgReceived(byte[] channel, byte[] from, boolean reliable,
            ByteBuffer data) throws IOException {
        RCChannelFrame cframe = channelMap.get(new BYTEARRAY(channel));
        cframe.receive(from, reliable, data);
    }

    public void validated() throws IOException {
        status.setText("Status: Accepted");
        connectButton.setText("Disconnect");
        connectButton.setEnabled(true);
        openChanButton.setEnabled(true);
        status.repaint();
    }

    public void invalidated(String message) throws IOException {
        status.setText("Status: Rejected");
        connectButton.setText("Connect");
        connectButton.setEnabled(true);
        status.repaint();
    }

    public void validationRequested(Callback[] cbs) throws IOException {
    // TODO Auto-generated method stub

    }

    public void userJoinedSystem(byte[] user) throws IOException {
        try {
            ((DefaultListModel) userList.getModel()).addElement(new UserID(user));
            userList.repaint();
        } catch (InstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    public void userLeftSystem(byte[] user) throws IOException {
        DefaultListModel mdl = ((DefaultListModel) userList.getModel());
        Enumeration e = mdl.elements();
        while (e.hasMoreElements()) {
            UserID id = (UserID) e.nextElement();
            if (id.equals(user)) {
                mdl.removeElement(id);
                userList.repaint();
                return;
            }
        }

    }

    public void userJoinedChannel(byte[] channelID, byte[] user)
            throws IOException {
        RCChannelFrame rcf = channelMap.get(new BYTEARRAY(channelID));
        rcf.userJoined(user);

    }

    public void userLeftChannel(byte[] channel, byte[] user) throws IOException {
        RCChannelFrame rcf = channelMap.get(new BYTEARRAY(channel));
        rcf.userLeft(user);

    }

    public void reconnectKeyReceived(byte[] key, long ttl) throws IOException {
    // TODO Auto-generated method stub

    }

    public void setUserID(UserID id) {
        myID = id;

    }

    public UserID getUserID() {
        // TODO Auto-generated method stub
        return myID;
    }

    public void disconnected() {
    // TODO Auto-generated method stub

    }

    public void leftChan(SGSChannel channel) {
        RCChannelFrame rcf = channelMap.get(new BYTEARRAY(
                channel.channelID().toByteArray()));
        rcf.close();

    }

    public void userDisconnected() {
        status.setText("Status: Disconnected");
        connectButton.setText("Connect");
        connectButton.setEnabled(true);
        ((DefaultListModel) userList.getModel()).clear();
        status.repaint();

    }

    public void deregistered() {
    // TODO Auto-generated method stub

    }

}
