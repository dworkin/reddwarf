package com.sun.gi.comm.example;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class ChatMainFrame extends JFrame {
  JPanel contentPane;
  BorderLayout borderLayout1 = new BorderLayout();
  JPanel interfacePanel = new JPanel();
  JPanel inputPanel = new JPanel();
  BorderLayout borderLayout2 = new BorderLayout();
  JTextArea textOut = new JTextArea();
  BorderLayout borderLayout3 = new BorderLayout();
  JTextField textIn = new JTextField();
  JScrollPane jScrollPane1 = new JScrollPane();
  JScrollPane jScrollPane2 = new JScrollPane();
  JTextArea systemMessages = new JTextArea();
  JPanel jPanel1 = new JPanel();
  JList channelList = new JList(new Object[]{"No Channels Yet"});
  BorderLayout borderLayout4 = new BorderLayout();
  JList userList = new JList(new Object[]{"No Users Yet"});
  DefaultListModel defaultListModel1 = new DefaultListModel();
  Border border1;
  JScrollPane jScrollPane3 = new JScrollPane();
  JScrollPane jScrollPane4 = new JScrollPane();

  //Construct the frame
  public ChatMainFrame() {
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
    border1 = BorderFactory.createBevelBorder(BevelBorder.LOWERED,Color.white,Color.white,new Color(117, 118, 118),new Color(168, 170, 170));
    contentPane.setLayout(borderLayout1);
    this.setSize(new Dimension(400, 300));
    this.setTitle("Chat");
    interfacePanel.setLayout(borderLayout2);
    textOut.setText("jTextArea1");
    inputPanel.setLayout(borderLayout3);
    textIn.setText("jTextField1");
    systemMessages.setText("jTextArea2");
    jPanel1.setLayout(borderLayout4);
    jScrollPane1.setBorder(border1);
    jScrollPane2.setBorder(BorderFactory.createLoweredBevelBorder());
    contentPane.add(interfacePanel, BorderLayout.CENTER);
    interfacePanel.add(inputPanel,  BorderLayout.SOUTH);
    inputPanel.add(textIn, BorderLayout.NORTH);
    inputPanel.add(jScrollPane2, BorderLayout.CENTER);
    jScrollPane2.getViewport().add(systemMessages, null);
    interfacePanel.add(jPanel1,  BorderLayout.EAST);
    jPanel1.add(jScrollPane3,  BorderLayout.EAST);
    jPanel1.add(jScrollPane4, BorderLayout.WEST);
    jScrollPane4.getViewport().add(channelList, null);
    jScrollPane3.getViewport().add(userList, null);
    interfacePanel.add(jScrollPane1, BorderLayout.CENTER);
    jScrollPane1.getViewport().add(textOut, null);
  }

  //Overridden so we can exit when window is closed
  protected void processWindowEvent(WindowEvent e) {
    super.processWindowEvent(e);
    if (e.getID() == WindowEvent.WINDOW_CLOSING) {
      System.exit(0);
    }
  }
}
