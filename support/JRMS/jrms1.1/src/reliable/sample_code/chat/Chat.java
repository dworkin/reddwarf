/* 
 * Copyright (c) 2001, Sun Microsystems Laboratories 
 * All rights reserved. 
 * 
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met: 
 * 
 *     Redistributions of source code must retain the 
 *     above copyright notice, this list of conditions 
 *     and the following disclaimer. 
 *             
 *     Redistributions in binary form must reproduce 
 *     the above copyright notice, this list of conditions 
 *     and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution. 
 *             
 *     Neither the name of Sun Microsystems, Inc. nor 
 *     the names of its contributors may be used to endorse 
 *     or promote products derived from this software without 
 *     specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND 
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE. 
 */

/*
 * Chat.java
 */
package com.sun.multicast.reliable.applications.chat;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import com.sun.multicast.util.AssertFailedException;
import com.sun.multicast.util.ImpossibleException;
import com.sun.multicast.reliable.RMException;
import com.sun.multicast.util.UnsupportedException;
import com.sun.multicast.reliable.transport.InvalidMulticastAddressException;
import com.sun.multicast.reliable.transport.InvalidTransportProfileException;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.transport.lrmp.LRMPTransportProfile;

/**
 * A chat application that uses reliable multicast.
 */
public class Chat extends Frame {
    boolean done = false;
    Frame window = null;
    TextField messageField;
    TextArea chatArea;
    String userName;
    String prefix;
    String transportName = "LRMP";
    boolean verbose = false;
    InetAddress ia;
    int port;
    RMPacketSocket socket = null;
    ReceiverThread receiver = null;

    /**
     * Inner class to tell us when the window is closing.
     */
    class WL extends WindowAdapter {

        /**
         * If window closes, exit.
         */
        public void windowClosing(WindowEvent e) {
            synchronized (Chat.this) {
                done = true;

                Chat.this.notifyAll();
            }
        }

    }

    /**
     * Inner class to receive messages and display them
     */
    class ReceiverThread extends Thread {
        private boolean done = false;

        public void run() {
            while (!done) {
                try {
                    DatagramPacket dp = socket.receive();
                    String message = new String(dp.getData(), "UTF8");

                    chatArea.append(message + "\n");
		} catch (Exception ex) {}
            }
        }

        public void terminate() {
            done = true;
        }

    }

    /**
     * Inner class to send a message.
     */
    class SendMessage implements ActionListener {

        /**
         * When a message is entered, send it.
         */
        public void actionPerformed(ActionEvent e) {
            try {
                String message = prefix + messageField.getText();

                messageField.setText("");

                byte data[] = message.getBytes("UTF8");
                DatagramPacket dp = new DatagramPacket(data, data.length, ia, 
                                                       port);

                socket.send(dp);
                chatArea.append(message + "\n");
            } catch (Exception ex) {}
        }

    }

    /**
     * Creates a new Chat object.
     * @exception IllegalArgumentException if there is a problem 
     * parsing the args
     * (also prints the usage to System.out)
     * @exception IOException if an I/O error occurs
     * @exception RMException if a reliable mulicast related exception occurs
     */
    Chat(String[] args) 
            throws IllegalArgumentException, IOException, RMException {
        parseArgs(args);
        createWindow();
        createSocket();
    }

    /**
     * Creates a new chat window and other AWT components.
     */
    void createWindow() {
        window = new Frame();

        window.addWindowListener(new WL());
        window.setSize(400, 300);
        window.setTitle("Chat for " + userName);

        chatArea = new TextArea();

        chatArea.setEditable(false);
        window.add("Center", chatArea);

        Panel p = new Panel();

        p.setLayout(new BorderLayout());
        p.add("West", new Label("Enter a message:"));

        messageField = new TextField();

        messageField.addActionListener(new SendMessage());
        p.add("Center", messageField);
        window.add("South", p);
        window.setVisible(true);
        messageField.requestFocus();
    }

    /**
     * Closes a Chat object.
     */
    void close() {
        if (window != null) {
            window.dispose();
        } 
        if (receiver != null) {
            receiver.terminate();
        } 
        if (socket != null) {
	    sendSignoff();
            socket.close();
        } 
    }

    /**
     * Sends a final signoff message when window is closing
     */
    void sendSignoff() {
	String message = prefix + " signing off";

	try {
	byte data[] = message.getBytes("UTF8");
	DatagramPacket dp = new DatagramPacket(data, data.length, ia, port);

	socket.send(dp);
	} catch (Exception ex) {}
    }

    /**
     * Creates an RMPacketSocket.
     * @exception IOException if an I/O error occurs
     * @exception RMException if a reliable multicast related exception occurs
     */
    void createSocket() throws IOException, RMException {
        TransportProfile tp = null;

        if (transportName.equals("LRMP")) {

            /*
             * Obtain a new LRMPTransportProfile with the address and
             * port specified.
             */
            LRMPTransportProfile lrmptp;

            try {
                lrmptp = new LRMPTransportProfile(ia, port);
            } catch (InvalidMulticastAddressException e) {
                throw new ImpossibleException(e);
            }

            tp = lrmptp;

            lrmptp.setTTL((byte) 1);
            lrmptp.setOrdered(false);
        } else {
            throw new AssertFailedException();
        }

        try {
            socket = tp.createRMPacketSocket(TransportProfile.SEND_RECEIVE);
        } catch (InvalidTransportProfileException e) {
            throw new ImpossibleException(e);
        } catch (UnsupportedException e) {
            throw new ImpossibleException(e);
        }

        receiver = new ReceiverThread();

        receiver.start();
    }

    /**
     * Prints the usage message to System.out.
     */
    void usage() {
        System.out.println(
            "Usage: java com.sun.multicast.reliable.applications." +
	    "chat.Chat [flags] addr port userName");
        System.out.println(" where flags may include:");
        System.out.println(
            "         -transport name to set the transport (default is LRMP)");
    }

    /**
     * Parse the command line arguments.
     * 
     * @param args the command line arguments
     * @exception IllegalArgumentException if there is a problem 
     * parsing the args
     * (also prints the usage to System.out)
     */
    void parseArgs(String[] args) throws IllegalArgumentException {
        int mainArgs = 0;

        try {
            for (int i = 0; i < args.length; i++) {

                // @@@ Should check for duplicate flags

                if (args[i].startsWith("-")) {
                    if (args[i].equals("-transport")) {
                        if (i + 1 >= args.length) {
                            throw new IllegalArgumentException();
                        } 

                        i += 1;
                        transportName = args[i];

                        if (!transportName.equals("LRMP")) {
                            throw new IllegalArgumentException();
                        } 
                    } else if (args[i].equals("-verbose")) {
                        verbose = true;
                    } else {
                        throw new IllegalArgumentException();
                    }
                } else {
                    switch (mainArgs) {

                    case 0: 
                        try {
                            ia = InetAddress.getByName(args[i]);
                        } catch (UnknownHostException e) {
                            throw new IllegalArgumentException();
                        }

                        if (!ia.isMulticastAddress()) {
                            throw new IllegalArgumentException();
                        } 

                        break;

                    case 1: 
                        port = Integer.parseInt(args[i]);

                        break;

                    case 2: 
                        userName = args[i];
                        prefix = userName + ": ";

                        break;

                    default: 
                        throw new IllegalArgumentException();
                    }

                    mainArgs += 1;
                }
            }

            if (mainArgs != 3) {
                throw new IllegalArgumentException();
            } 
            if (verbose) {
                System.out.println("Multicast Address   = " 
                                   + ia.getHostAddress());
                System.out.println("Multicast Port      = " + port);
                System.out.println("Transport Name      = " + transportName);
                System.out.println("User Name           = " + userName);
            }
        } catch (IllegalArgumentException e) {
            usage();

            throw e;
        }
    }

    /**
     * Lets the chat run until the window is closed.
     */
    void runChat() {
        try {
            synchronized (this) {
                while (!done) {
                    wait();
                }
            }
        } catch (InterruptedException e) {}
        finally {
            close();
        }
    }

    /**
     * Runs the chat application.
     * @param args command line arguments
     */
    public static void main(String[] args) {
        boolean succeeded = true;

        try {
            Chat c = new Chat(args);

            c.runChat();
        } catch (Exception e) {
            e.printStackTrace();

            succeeded = false;
        }

        if (succeeded) {
            System.exit(0);
        } else {
            System.exit(1);
        }
    }

}
