package com.sun.gi.comm.test;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.nio.ByteBuffer;

import javax.swing.DefaultListModel;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.utils.types.BYTEARRAY;

@SuppressWarnings("serial")
public class ChatChannelFrame extends JInternalFrame implements ClientChannelListener{
	final ClientChannel chan;
	JList userList;
	JTextField inputField;
	JTextArea outputArea;
	public ChatChannelFrame(ClientChannel channel){
		super("Channel: "+channel.getName());
		chan = channel;
		Container c = getContentPane();
		c.setLayout(new BorderLayout());
		JPanel eastPanel = new JPanel();
		eastPanel.setLayout(new BorderLayout());
		c.add(eastPanel,BorderLayout.EAST);
		eastPanel.add(new JLabel("Users"),BorderLayout.NORTH);
		userList = new JList();
		eastPanel.add(new JScrollPane(userList),BorderLayout.CENTER);
		JPanel southPanel = new JPanel();
		c.add(southPanel,BorderLayout.SOUTH);
		southPanel.setLayout(new GridLayout(1,0));
		inputField = new JTextField();
		southPanel.add(inputField);
		outputArea = new JTextArea();
		c.add(outputArea,BorderLayout.SOUTH);
		inputField.addActionListener(new ActionListener(){

			public void actionPerformed(ActionEvent e) {
				chan.sendBroadcastData(ByteBuffer.wrap(inputField.getText().getBytes()),true);
				inputField.setText("");
			}});
		setSize(400,400);
		setVisible(true);
		
	}
	public void playerJoined(byte[] playerID) {
		DefaultListModel mdl = (DefaultListModel)userList.getModel();
		mdl.addElement(new BYTEARRAY(playerID));
		
	}
	public void playerLeft(byte[] playerID) {
		DefaultListModel mdl = (DefaultListModel)userList.getModel();
		mdl.removeElement(new BYTEARRAY(playerID));		
	}
	public void dataArrived(byte[] from, ByteBuffer data, boolean reliable) {
		outputArea.append(new BYTEARRAY(from).toString()+": "+ new String(data.array()));	
	}
	public void channelClosed() {
		getDesktopPane().remove(this);
	}
}
