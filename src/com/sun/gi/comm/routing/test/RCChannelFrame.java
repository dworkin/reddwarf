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

import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.routing.UserID;

public class RCChannelFrame extends JInternalFrame {

    private static final long serialVersionUID = 1L;

    private SGSChannel chan;

    private JList userList = new JList(new DefaultListModel());

    private JTextArea textOut = new JTextArea();

    private JTextField textIn = new JTextField();
    private UserID uid;
    private ByteBuffer xmitBuff = ByteBuffer.allocate(2048);

    public RCChannelFrame(UserID userID, SGSChannel channel) {
        super(channel.getName());
        chan = channel;
        uid = userID;
        setSize(100, 100);
        setResizable(true);
        Container c = getContentPane();
        c.setLayout(new BorderLayout());
        JPanel userListPanel = new JPanel();
        userListPanel.setLayout(new BorderLayout());
        userListPanel.add(new JLabel("Channel Users"), BorderLayout.NORTH);
        userListPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
        c.add(userListPanel, BorderLayout.EAST);
        JPanel ioPanel = new JPanel();
        ioPanel.setLayout(new BorderLayout());
        ioPanel.add(new JScrollPane(textOut), BorderLayout.CENTER);
        ioPanel.add(textIn, BorderLayout.SOUTH);
        textIn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doTextIn();
            }
        });
        c.add(ioPanel, BorderLayout.CENTER);
        setVisible(true);

    }

    protected void doTextIn() {
        byte[] t = textIn.getText().getBytes();
        xmitBuff.clear();
        xmitBuff.putInt(t.length);
        xmitBuff.put(t);
        chan.broadcastData(uid, xmitBuff, true);
        textIn.setText("");
    }

    public void receive(byte[] from, boolean reliable, ByteBuffer data) {
        int tlen = data.getInt();
        byte[] t = new byte[tlen];
        data.get(t);
        textOut.append(new String(t));
        textOut.append("\n");
    }

    public void userJoined(byte[] user) {
        try {
            ((DefaultListModel) (userList.getModel())).addElement(new UserID(
                    user));
            userList.repaint();
        } catch (InstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void userLeft(byte[] user) {
        try {
            ((DefaultListModel) userList.getModel()).removeElement(new UserID(
                    user));
            userList.repaint();
        } catch (InstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void close() {
        close();

    }

}
