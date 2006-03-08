package com.sun.gi.framework.interconnect.test;

import javax.swing.JFrame;
import com.sun.gi.framework.interconnect.TransportChannel;
import java.awt.Dimension;
import java.awt.Container;
import java.awt.BorderLayout;
import javax.swing.JTextPane;
import javax.swing.JScrollPane;
import javax.swing.JPanel;
import java.awt.GridLayout;
import javax.swing.JButton;
import javax.swing.JTextField;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.nio.ByteBuffer;
import java.io.*;
import com.sun.gi.framework.interconnect.TransportChannelListener;
import javax.swing.JTextArea;

public class ChatFrame extends JFrame implements TransportChannelListener{
  final TransportChannel channel;
   JTextArea textOut;
   
  public ChatFrame(TransportChannel chan) {
    channel = chan;
    channel.addListener(this);
    Container c = this.getContentPane();
    c.setLayout(new BorderLayout());
    textOut = new JTextArea();
    c.add(new JScrollPane(textOut),BorderLayout.CENTER);
    textOut.setEditable(false);
    JPanel inPanel = new JPanel();
    inPanel.setLayout(new GridLayout(1,2));
    JButton sendButton = new JButton("Send");
    inPanel.add(sendButton);
    final JTextField inText = new JTextField();
    inPanel.add(inText);
    c.add(inPanel,BorderLayout.SOUTH);
    sendButton.addActionListener(new ActionListener(){
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
        }
        catch (IOException ex) {
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
    System.out.println("Weird. Channel "+channel.getName()+" closed.");
  }

  /**
   * dataArrived
   *
   * @param buff ByteBuffer
   */
  public void dataArrived(ByteBuffer buff) {
    byte[] inbytes = new byte[buff.remaining()];
    buff.get(inbytes);
    textOut.append(new String(inbytes)+"\n");
  }
}
