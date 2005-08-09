package com.sun.gi.framework.interconnect.test;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import com.sun.gi.framework.interconnect.TransportManager;
import com.sun.gi.framework.interconnect.impl.LRMPTransportManager;
import com.sun.gi.framework.interconnect.TransportChannel;
import java.io.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class Frame1 extends JFrame {
  JPanel contentPane;
  TransportManager mgr = new LRMPTransportManager();

  //Construct the frame
  public Frame1() {
    enableEvents(AWTEvent.WINDOW_EVENT_MASK);
    try {
      jbInit();
    }
    catch(Exception e) {
      e.printStackTrace();
    }
  }

  //Component initialization
  private void jbInit() throws Exception  {
    contentPane = (JPanel) this.getContentPane();
    contentPane.setLayout(new GridLayout(1,2));
    JButton openChannelButton = new JButton("Open Channel");
    final JTextField channelName = new JTextField();
    contentPane.add(openChannelButton);
    contentPane.add(channelName);
    openChannelButton.addActionListener(new ActionListener() {
      /**
       * actionPerformed
       *
       * @param e ActionEvent
       */
      public void actionPerformed(ActionEvent e) {
        try {
          TransportChannel chan = mgr.openChannel(channelName.getText());
          ChatFrame chat = new ChatFrame(chan);
          chat.setVisible(true);
          channelName.setText("");
        }
        catch (IOException ex) {
          ex.printStackTrace();
        }

      }

    });
    this.setSize(new Dimension(400, 60));
    this.setTitle("Interconnect Test");
  }

  //Overridden so we can exit when window is closed
  protected void processWindowEvent(WindowEvent e) {
    super.processWindowEvent(e);
    if (e.getID() == WindowEvent.WINDOW_CLOSING) {
      System.exit(0);
    }
  }
}
