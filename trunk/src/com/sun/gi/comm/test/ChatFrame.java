package com.sun.gi.comm.test;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import java.net.URL;
import java.io.File;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import java.nio.ByteBuffer;
import javax.security.auth.callback.Callback;
import com.sun.gi.comm.example.ValidationDialog;
import com.sun.gi.utils.types.BYTEARRAY;
import java.util.LinkedList;
import java.util.Iterator;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2005</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class ChatFrame
    extends JFrame implements ClientConnectionManagerListener {
  JPanel contentPane;
  JLabel statusBar = new JLabel();
  BorderLayout borderLayout1 = new BorderLayout();
  JTextField textInputField = new JTextField();
  JScrollPane jScrollPane1 = new JScrollPane();
  JTextArea textOutputArea = new JTextArea();
  private ClientConnectionManager connManager;
  private java.util.List users = new LinkedList();
  private static URL DISCOVERY_URL;
  private static final int CHAT_GID = 1;
  private byte[] myID;
  private static final int CONNECTION_RETRY_PAUSE = 1000;
  private static final boolean UNICASTTEST = true;


  static {
    try {
      DISCOVERY_URL = new URL("http://10.5.34.12/discovery.xml");
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  //Construct the frame
  public ChatFrame() {
    enableEvents(AWTEvent.WINDOW_EVENT_MASK);
    try {
      jbInit();
      // connect to SGS backend
      try {
        connManager = new ClientConnectionManager(CHAT_GID,
                                                  new URLDiscoverer(
            DISCOVERY_URL));
        connManager.setListener(this);
        String[] connectionMethods;
        do {
          connectionMethods = connManager.getUserManagerClassNames();
          if (connectionMethods.length == 0) {
            try {
              Thread.sleep(CONNECTION_RETRY_PAUSE);
            }
            catch (InterruptedException ex1) {
              ex1.printStackTrace();
            }
          }
        }
        while (connectionMethods.length == 0);
        String selectedMethod = (String) JOptionPane.showInputDialog(
            null,
            "Chose a connection method:",
            "User Manager Selection",
            JOptionPane.PLAIN_MESSAGE,
            null, connectionMethods, null);
        connManager.setListener(this);
        if (!connManager.connect(selectedMethod)) {
          System.out.println("ERROR: Connection to SGS backend failed!");
          System.exit(1130);
        }

      }
      catch (Exception ex) {
        ex.printStackTrace();
      }

    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  //Component initialization
  private void jbInit() throws Exception {
    contentPane = (JPanel)this.getContentPane();
    contentPane.setLayout(borderLayout1);
    this.setSize(new Dimension(400, 300));
    this.setTitle("Chat!");
    statusBar.setText(" ");
    textInputField.setEnabled(false);
    textInputField.setText("");
    textInputField.addActionListener(new ChatFrame_textInputField_actionAdapter(this));
    textOutputArea.setEditable(false);
    contentPane.add(statusBar, BorderLayout.NORTH);
    contentPane.add(textInputField, BorderLayout.SOUTH);
    contentPane.add(jScrollPane1, BorderLayout.CENTER);
    jScrollPane1.getViewport().add(textOutputArea, null);
  }

  //Overridden so we can exit when window is closed
  protected void processWindowEvent(WindowEvent e) {
    super.processWindowEvent(e);
    if (e.getID() == WindowEvent.WINDOW_CLOSING) {
      System.exit(0);
    }
  }

  void textInputField_actionPerformed(ActionEvent e) {
    byte[] msgbytes = textInputField.getText().getBytes();
    ByteBuffer buff = ByteBuffer.wrap(msgbytes);
    buff.position(msgbytes.length);
    if (UNICASTTEST){
      for(Iterator i = users.iterator();i.hasNext();){
        byte[] id = ((BYTEARRAY)i.next()).data();
        connManager.sendUnicastData(myID,id,buff,true);
      }
    } else {
      connManager.broadcastData(myID, buff, true);
    }
  }

  //** callabcks from ClientConnectionManager
  /**
   * connected
   *
   * @param myID byte[]
   */
  public void connected(byte[] myID) {
    this.myID = myID;
    users.add(new BYTEARRAY(myID));
    statusBar.setText("Connected to SGS.  ID= "+bytesToString(myID));
    textInputField.setEnabled(true);
  }

  /**
   * connectionRefused
   */
  public void connectionRefused(String msg) {
    statusBar.setText("SGS connection refused: "+msg);
  }

  /**
   * dataRecieved
   *
   * @param from byte[]
   * @param data ByteBuffer
   */
  public void dataRecieved(byte[] from, ByteBuffer data) {
    byte[] msgbytes = new byte[data.remaining()];
    data.get(msgbytes);
    textOutputArea.append(bytesToString(from)+": "+new String(msgbytes)+"\n");
  }

  /**
   * disconnected
   */
  public void disconnected() {
    statusBar.setText("SGS connection dropped.");
  }

  /**
   * userJoined
   *
   * @param userID byte[]
   */
  public void userJoined(byte[] userID) {
    statusBar.setText("User joined: "+bytesToString(userID));
    users.add(new BYTEARRAY(userID));
  }

  /**
   * userLeft
   *
   * @param userID byte[]
   */
  public void userLeft(byte[] userID) {
    statusBar.setText("User left: "+bytesToString(userID));
    users.remove(new BYTEARRAY(userID));
  }

  /**
   * validationRequest
   *
   * @param callbacks Callback[]
   */
  public void validationRequest(Callback[] callbacks) {
    ValidationDialog validator = new ValidationDialog(this,"Validation Request",
        callbacks,true);
    connManager.sendValidationResponse(validator.getCallbacks());
  }


  private String bytesToString(byte[] ba){
    StringBuffer s = new StringBuffer();
    for(int i=0;i<ba.length;i++){
      s.append(Integer.toHexString(((int)(ba[i]))&0xFF));
    }
    return s.toString();
  }

  /**
   * failOverInProgress
   *
   * @param userID byte[]
   */
  public void failOverInProgress(byte[] userID) {
    statusBar.setText("Attempting Failover on ID: "+userID);
  }

}

class ChatFrame_textInputField_actionAdapter
    implements java.awt.event.ActionListener {
  ChatFrame adaptee;

  ChatFrame_textInputField_actionAdapter(ChatFrame adaptee) {
    this.adaptee = adaptee;
  }

  public void actionPerformed(ActionEvent e) {
    adaptee.textInputField_actionPerformed(e);
  }
}
