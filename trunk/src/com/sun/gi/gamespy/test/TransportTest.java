package com.sun.gi.gamespy.test;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.text.*;

import com.sun.gi.gamespy.*;

public class TransportTest
    extends JFrame
    implements TransportListener {
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
    socketHandle = JNITransport.gt2CreateSocket("127.0.0.1:7777", 256,
                                                256);

    if (socketHandle != 0) { // were first
      master = true;
      JNITransport.gt2Listen(socketHandle);
    }
    else { // error on create
      // we must be the second client
      socketHandle = JNITransport.gt2CreateSocket("", 256,
                                                  256);
      byte[] msg = "Connection Attempt!".getBytes();
      JNITransport.gt2Connect(socketHandle,"127.0.0.1:7777",msg,msg.length,0);
      System.out.println("Connect result = "+JNITransport.lastResult());
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
        while(true){
          JNITransport.gt2Think(socketHandle);
          try {
            Thread.sleep(100);
          }
          catch (InterruptedException ex) {
            ex.printStackTrace();
          }
        }
      }
    }).start();
  }

  private void doSendReliable() {

  }

  private void doSendUnreliable() {

  }

  public static void main(String[] args) {
    JNITransport.initialize();
    new TransportTest();
  }

  /**
   * closed
   *
   * @param connectionHandle long
   * @param reason long
   */
  public void closed(long connectionHandle, long reason) {
  }

  /**
   * connectAttempt
   *
   * @param socketHandle long
   * @param connectionHandle long
   * @param ip long
   * @param port short
   * @param latency int
   * @param message byte[]
   * @param msgLength int
   */
  public void connectAttempt(long socketHandle, long connectionHandle, long ip,
                             short port, int latency, byte[] message,
                             int msgLength) {
    outputText("ConnectionAttempt: "+new String(message,0,msgLength));
    JNITransport.gt2Accept(connectionHandle);
  }

  /**
   * outputText
   *
   * @param string String
   */
  private void outputText(String txt) {
    Document doc = output.getDocument();
    try {
      doc.insertString(doc.getEndPosition().getOffset() - 1, txt,
                       new SimpleAttributeSet());
    }
    catch (BadLocationException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * connected
   *
   * @param connectionHandle long
   * @param result long
   * @param message byte[]
   * @param msgLength int
   */
  public void connected(long connectionHandle, long result, byte[] message,
                        int msgLength) {
     outputText("Connected: "+new String(message,0,msgLength));
  }

  /**
   * ping
   *
   * @param connectionHandle long
   * @param latency int
   */
  public void ping(long connectionHandle, int latency) {
     outputText("Ping: "+latency);
  }

  /**
   * socketError
   *
   * @param socketHandle long
   */
  public void socketError(long socketHandle) {
     outputText("Socket Error: ");
  }

}
