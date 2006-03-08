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

package com.sun.gi.framework.interconnect.test;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.ByteBuffer;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.sun.gi.framework.interconnect.TransportChannel;
import com.sun.gi.framework.interconnect.TransportChannelListener;

public class ChatFrame extends JFrame implements TransportChannelListener {

    private static final long serialVersionUID = 1L;

    final TransportChannel channel;
    JTextArea textOut;

    public ChatFrame(TransportChannel chan) {
        channel = chan;
        channel.addListener(this);
        Container c = this.getContentPane();
        c.setLayout(new BorderLayout());
        textOut = new JTextArea();
        c.add(new JScrollPane(textOut), BorderLayout.CENTER);
        textOut.setEditable(false);
        JPanel inPanel = new JPanel();
        inPanel.setLayout(new GridLayout(1, 2));
        JButton sendButton = new JButton("Send");
        inPanel.add(sendButton);
        final JTextField inText = new JTextField();
        inPanel.add(inText);
        c.add(inPanel, BorderLayout.SOUTH);
        sendButton.addActionListener(new ActionListener() {
            /**
             * actionPerformed
             * 
             * @param e ActionEvent
             */
            public void actionPerformed(ActionEvent e) {
                String txt = inText.getText();
                byte[] tbytes = txt.getBytes();
                ByteBuffer buff = ByteBuffer.wrap(tbytes);
                buff.position(tbytes.length);
                try {
                    channel.sendData(buff);
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                inText.setText("");
            }

        });
        this.setSize(new Dimension(400, 300));
        this.setTitle(chan.getName());
    }

    /**
     * channelClosed
     */
    public void channelClosed() {
        System.out.println("Weird. Channel " + channel.getName() + " closed.");
    }

    /**
     * dataArrived
     * 
     * @param buff ByteBuffer
     */
    public void dataArrived(ByteBuffer buff) {
        byte[] inbytes = new byte[buff.remaining()];
        buff.get(inbytes);
        textOut.append(new String(inbytes) + "\n");
    }
}
