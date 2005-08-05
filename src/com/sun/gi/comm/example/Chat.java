package com.sun.gi.comm.example;

import javax.swing.UIManager;
import java.awt.*;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.impl.DefaultUserManagerPolicy;
import java.net.URL;
import java.net.*;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import java.nio.ByteBuffer;
import javax.security.auth.callback.Callback;
import java.io.File;
import com.sun.gi.comm.users.client.*;

/**
 *
 * <p>Title: Chat</p>
 * <p>Description: This is an example of a game that only uses the comm
 * level of the Sun Game Server.  It is designed to fully exercise and test
 * that level as well as being an example of how to code to the client
 * side interface.  </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author Jeff Kessselman
 * @version 1.0
 */

public class Chat implements ClientConnectionManagerListener {
  boolean packFrame = false;
  ChatMainFrame frame;
  ClientConnectionManager mgr;

  //Construct the application
  public Chat() {
    frame = new ChatMainFrame();
    //Validate frames that have preset sizes
    //Pack frames that have useful preferred size info, e.g. from their layout
    if (packFrame) {
      frame.pack();
    }
    else {
      frame.validate();
    }
    //Center the window
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    Dimension frameSize = frame.getSize();
    if (frameSize.height > screenSize.height) {
      frameSize.height = screenSize.height;
    }
    if (frameSize.width > screenSize.width) {
      frameSize.width = screenSize.width;
    }
    frame.setLocation((screenSize.width - frameSize.width) / 2, (screenSize.height - frameSize.height) / 2);
    frame.setVisible(true);
    // start login process
    try {
      mgr = new ClientConnectionManager(1,
          new URLDiscoverer(new File("Discovery.xml").toURL()),
          new DefaultUserManagerPolicy());
    }
    catch (MalformedURLException ex) {
      ex.printStackTrace();
      System.exit(1001);
    }
    mgr.setListener(this);
    try {
      if (mgr.connect("com.sun.gi.comm.users.client.TCPIPUserManagerClient")) {
        statusMessage("Starting connection process....");
      }
      else {
        statusMessage("Attempt to start connection failed.");
      }
    }
    catch (ClientAlreadyConnectedException ex1) {
      ex1.printStackTrace();
      System.exit(1);
    }
  }

  //Main method
  public static void main(String[] args) {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    }
    catch(Exception e) {
      e.printStackTrace();
    }
    new Chat();
  }

  private String idToString(byte[] myID){
    String id = ""+myID[0];
    for(int i=1;i<myID.length;i++){
      id = id+":"+myID[i];
    }
    return id;
  }
  //  ClientManagerConnectionListener callbacks
  /**
   * connected
   *
   * @param myID byte[]
   */
  public void connected(byte[] myID) {
    statusMessage("Logged in as: "+idToString(myID));
  }

  /**
   * statusMessage
   *
   * @param string String
   */
  private void statusMessage(String string) {
    frame.systemMessages.append(string);
  }

  /**
   * connectionRefused
   */
  public void connectionRefused(String message) {
   statusMessage("Login rejected: "+message);
  }

  /**
   * dataRecieved
   *
   * @param from byte[]
   * @param data ByteBuffer
   */
  public void dataRecieved(byte[] from, ByteBuffer data) {
    int textlength = data.getInt();
    byte[] strbytes = new byte[textlength];
    data.get(strbytes);
    frame.textOut.append(idToString(from)+" - "+new String(strbytes));
  }

  /**
   * disconnected
   */
  public void disconnected() {
    statusMessage("Disconnected");
  }

  /**
   * userJoined
   *
   * @param userID byte[]
   */
  public void userJoined(byte[] userID) {
    statusMessage("User joined: "+idToString(userID));
  }

  /**
   * userLeft
   *
   * @param userID byte[]
   */
  public void userLeft(byte[] userID) {
    statusMessage("User left: "+idToString(userID));
  }

  /**
   * validationRequest
   *
   * @param callbacks Callback[]
   */
  public void validationRequest(Callback[] callbacks) {
    ValidationDialog vd = new ValidationDialog(frame,"Login Information",callbacks,
                                               true);
    vd.show();
    mgr.sendValidationResponse(vd.getCallbacks());

  }

  /**
   * failOverInProgress
   *
   * @param userID byte[]
   */
  public void failOverInProgress(byte[] userID) {
  }
}
