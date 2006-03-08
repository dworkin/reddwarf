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

package com.sun.gi.gamespy.test;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;

import com.sun.gi.gamespy.JNITransport;
import com.sun.gi.gamespy.TransportListener;

public class TransportTest extends JFrame implements TransportListener {

    private static final long serialVersionUID = 1L;

    JTextArea output = new JTextArea();
    JTextField input = new JTextField();

    long connectionHandle;
    long socketHandle;
    boolean master = false;

    public TransportTest() {
        super("GT2 Transport Test");
        JNITransport.addListener(this);
        Container c = this.getContentPane();
        c.setLayout(new BorderLayout());
        c.add(new JScrollPane(output), BorderLayout.CENTER);
        JPanel p = new JPanel();
        p.setLayout(new FlowLayout());
        JButton button = new JButton("Send Reliable");
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doSendReliable();
            }
        });
        p.add(button);
        button = new JButton("Send Uneliable");
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                doSendUnreliable();
            }
        });
        p.add(button);
        JPanel p2 = new JPanel(new BorderLayout());
        p2.add(input, BorderLayout.CENTER);
        p2.add(p, BorderLayout.EAST);
        c.add(p2, BorderLayout.SOUTH);
        pack();
        setSize(400, 400);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        socketHandle = JNITransport.gt2CreateSocket("127.0.0.1:7777", 256, 256);

        if (socketHandle != 0) { // were first
            master = true;
            JNITransport.gt2Listen(socketHandle);
        } else { // error on create
            // we must be the second client
            socketHandle = JNITransport.gt2CreateSocket("", 256, 256);
            byte[] msg = "Connection Attempt!".getBytes();
            JNITransport.gt2Connect(socketHandle, "127.0.0.1:7777", msg,
                    msg.length, 0);
            System.out.println("Connect result = " + JNITransport.lastResult());
        }
        startThinkThread();
        setVisible(true);
    }

    /**
     * startThinkThread
     */
    private void startThinkThread() {
        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    JNITransport.gt2Think(socketHandle);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }).start();
    }

    void doSendReliable() {
        // no-op
    }

    void doSendUnreliable() {
        // no-op
    }

    public static void main(String[] args) {
        JNITransport.initialize();
        new TransportTest();
    }

    /**
     * closed
     * 
     * @param aConnectionHandle long
     * @param reason long
     */
    public void closed(long aConnectionHandle, long reason) {
        // no-op
    }

    /**
     * connectAttempt
     * 
     * @param aSocketHandle long
     * @param aConnectionHandle long
     * @param ip long
     * @param port short
     * @param latency int
     * @param message byte[]
     * @param msgLength int
     */
    public void connectAttempt(long aSocketHandle, long aConnectionHandle,
            long ip, short port, int latency, byte[] message, int msgLength) {
        outputText("ConnectionAttempt: " + new String(message, 0, msgLength));
        JNITransport.gt2Accept(aConnectionHandle);
    }

    /**
     * outputText
     * 
     * @param txt String
     */
    private void outputText(String txt) {
        Document doc = output.getDocument();
        try {
            doc.insertString(doc.getEndPosition().getOffset() - 1, txt,
                    new SimpleAttributeSet());
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * connected
     * 
     * @param aConnectionHandle long
     * @param result long
     * @param message byte[]
     * @param msgLength int
     */
    public void connected(long aConnectionHandle, long result, byte[] message,
            int msgLength) {
        outputText("Connected: " + new String(message, 0, msgLength));
    }

    /**
     * ping
     * 
     * @param aConnectionHandle long
     * @param latency int
     */
    public void ping(long aConnectionHandle, int latency) {
        outputText("Ping: " + latency);
    }

    /**
     * socketError
     * 
     * @param aSocketHandle long
     */
    public void socketError(long aSocketHandle) {
        outputText("Socket Error: ");
    }

}
