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
import java.nio.ByteBuffer;

import javax.swing.DefaultListModel;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.WindowConstants;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;

import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.utils.types.BYTEARRAY;
import com.sun.gi.utils.types.StringUtils;

/**
 * <p>
 * The ChatChannelFrame presents a GUI so that a user can interact with
 * a channel. The users connected to the channel are displayed in a list
 * on the right side. Messages can be sent on the channel via an input
 * area on the left side.
 * </p>
 * 
 * <p>
 * This class communicates with its channel by implementing
 * ClientChannelListener, and signing up as a listener on the channel.
 * As data arrives, and players leave or join, the appropriate call
 * backs are called.
 * </p>
 */

public class ChatChannelFrame extends JInternalFrame
        implements ClientChannelListener
{
    private static final long serialVersionUID = 1L;
    final ClientChannel chan;
    JList userList;
    JTextField inputField;
    JTextArea outputArea;
    ByteBuffer outbuff;

    /**
     * Constructs a new ChatChannelFrame as a wrapper around the given
     * channel.
     * 
     * @param channel the channel that this class will manage.
     */
    public ChatChannelFrame(ClientChannel channel) {
        super("Channel: " + channel.getName());
        outbuff = ByteBuffer.allocate(2048);
        chan = channel;
        chan.setListener(this);
        Container c = getContentPane();
        c.setLayout(new BorderLayout());
        JPanel eastPanel = new JPanel();
        eastPanel.setLayout(new BorderLayout());
        c.add(eastPanel, BorderLayout.EAST);
        eastPanel.add(new JLabel("Users"), BorderLayout.NORTH);
        userList = new JList(new DefaultListModel());
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
        JPanel southPanel = new JPanel();
        c.add(southPanel, BorderLayout.SOUTH);
        southPanel.setLayout(new GridLayout(1, 0));
        inputField = new JTextField();
        southPanel.add(inputField);
        outputArea = new JTextArea();
        c.add(new JScrollPane(outputArea), BorderLayout.CENTER);
        inputField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                outbuff.clear();
                outbuff.put(inputField.getText().getBytes());
                chan.sendBroadcastData(outbuff, true);
                inputField.setText("");
            }
        });
        setSize(400, 400);
        this.setClosable(true);
        this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        this.addInternalFrameListener(new InternalFrameListener() {

            public void internalFrameOpened(InternalFrameEvent arg0) {
            // TODO Auto-generated method stub

            }

            public void internalFrameClosing(InternalFrameEvent arg0) {
            // TODO Auto-generated method stub

            }

            public void internalFrameClosed(InternalFrameEvent arg0) {
                chan.close();
            }

            public void internalFrameIconified(InternalFrameEvent arg0) {
            // TODO Auto-generated method stub

            }

            public void internalFrameDeiconified(InternalFrameEvent arg0) {
            // TODO Auto-generated method stub

            }

            public void internalFrameActivated(InternalFrameEvent arg0) {
            // TODO Auto-generated method stub

            }

            public void internalFrameDeactivated(InternalFrameEvent arg0) {
            // TODO Auto-generated method stub

            }
        });
        setResizable(true);
        setVisible(true);

    }

    /**
     * A call back from ClientChannelListener. Called when a player/user
     * joins the channel. This implementation responds by adding the
     * user to the list.
     */
    public void playerJoined(byte[] playerID) {
        DefaultListModel mdl = (DefaultListModel) userList.getModel();
        mdl.addElement(new BYTEARRAY(playerID));

    }

    /**
     * A call back from ClientChannelListener. Called when a player/user
     * leaves the channel. This implementation responds by removing the
     * user from the user list.
     */
    public void playerLeft(byte[] playerID) {
        DefaultListModel mdl = (DefaultListModel) userList.getModel();
        mdl.removeElement(new BYTEARRAY(playerID));
    }

    /**
     * A call back from ClientChannelListener. Called when data arrives
     * on the channel. This implementation simply dumps the data to the
     * output area as a String in the form of:
     * 
     * <pre>
     * &lt;User who sent the message&lt;: &lt;Message&lt;
     * </pre>
     */
    public void dataArrived(byte[] from, ByteBuffer data, boolean reliable) {
        byte[] textb = new byte[data.remaining()];
        data.get(textb);
        outputArea.append(StringUtils.bytesToHex(from, from.length - 4) + ": "
                + new String(textb) + "\n");
    }

    /**
     * Called when the channel is closed. The frame has no need to exist
     * if the channel is closed, so it removes itself from the parent.
     */
    public void channelClosed() {
        if (getDesktopPane() != null) {
            getDesktopPane().remove(this);
        }
    }
}
