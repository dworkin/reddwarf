package com.sun.gi.comm.users.test;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

import com.sun.gi.gamespy.*;
import javax.swing.text.Document;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.*;

public class GamespyCommTest extends JFrame implements TransportListener {
  JTextArea output;
  JTextField input;
  long socketHandle;
  long connectionHandle;

  public GamespyCommTest() {
    // set up connection
    JNITransport.initialize();
    socketHandle = JNITransport.gt2CreateSocket("",2048,2048);
    byte[] buff =  "Attemtping to connect to 1140".getBytes();
    if (JNITransport.gt2Connect(socketHandle,"localhost:1140",
       buff,buff.length,0)==0){
      System.out.println("Error conencting to server, gt2Connect returned "+
                         JNITransport.lastResult());
      System.exit(1);

    }
    // set up interface
    Container c = this.getContentPane();
    c.setLayout(new BorderLayout());
    output = new JTextArea();
    c.add(new JScrollPane(output),BorderLayout.CENTER);
    input = new JTextField();
    input.addActionListener(new ActionListener() {
      /**
       * actionPerformed
       *
       * @param e ActionEvent
       */
      public void actionPerformed(ActionEvent e) {
        doInput();
      }
    });
    c.add(input,BorderLayout.SOUTH);
    pack();
    setSize(400,400);
    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    input.setEnabled(false);
    JNITransport.addListener(this);
    setVisible(true);
    new Thread(new Runnable(){
      /**
       * run
       */
      public void run() {
        while(true) {
          JNITransport.gt2Think(socketHandle);
          try {
            Thread.sleep(10);
          }
          catch (InterruptedException ex) {
            ex.printStackTrace();
          }
        }
      }

    }).start();
  }

  private void doInput(){
    byte[] buff = input.getText().getBytes();
    JNITransport.gt2Send(connectionHandle,buff,buff.length,true);
    input.setText("");
  }


  // gamespy transport callbacks

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
    this.connectionHandle = connectionHandle;
    doOutput("System Message: Conencted to server.\n");
    input.setEnabled(true);
  }

  /**
   * doOutput
   *
   * @param string String
   */
  private void doOutput(String text) {
    Document doc = output.getDocument();
    try {
      doc.insertString(doc.getEndPosition().getOffset()-1, text,
                       new SimpleAttributeSet());
    }
    catch (BadLocationException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * ping
   *
   * @param connectionHandle long
   * @param latency int
   */
  public void ping(long connectionHandle, int latency) {
  }

  /**
   * receive
   *
   * @param connectionHandle long
   * @param message byte[]
   * @param length int
   * @param reliable boolean
   */
  public void receive(long connectionHandle, byte[] message, int length,
                      boolean reliable) {
    doOutput(new String(message,0,length)+"\n");
  }

  /**
   * socketError
   *
   * @param socketHandle long
   */
  public void socketError(long socketHandle) {
  }

  public static void main(String[] args){
    new GamespyCommTest();
  }
}
