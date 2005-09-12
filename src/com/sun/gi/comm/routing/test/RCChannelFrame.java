package com.sun.gi.comm.routing.test;

import java.awt.BorderLayout;
import java.awt.Container;
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

import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.routing.UserID;

public class RCChannelFrame extends JInternalFrame {
	private SGSChannel chan;

	private JList userList = new JList(new DefaultListModel());

	private JTextArea textOut = new JTextArea();

	private JTextField textIn = new JTextField();
	private UserID uid;
	private ByteBuffer xmitBuff = ByteBuffer.allocate(2048);

	public RCChannelFrame(UserID userID,SGSChannel channel) {
		super(channel.getName());
		chan = channel;
		uid = userID;
		setSize(100, 100);
		setResizable(true);
		Container c = getContentPane();
		c.setLayout(new BorderLayout());
		JPanel userListPanel = new JPanel();
		userListPanel.setLayout(new BorderLayout());
		userListPanel.add(new JLabel("Channel Users"), BorderLayout.NORTH);
		userListPanel.add(new JScrollPane(userList), BorderLayout.CENTER);
		c.add(userListPanel, BorderLayout.EAST);
		c.add(new JScrollPane(textOut), BorderLayout.CENTER);
		textIn.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				doTextIn();
			}
		});
		setVisible(true);

	}

	protected void doTextIn() {
		byte[] t = textIn.getText().getBytes();
		xmitBuff.clear();
		xmitBuff.putInt(t.length);
		xmitBuff.put(t);
		chan.broadcastData(uid,xmitBuff,true);
		textIn.setText("");
	}

	public void receive(byte[] from, boolean reliable, ByteBuffer data) {
		int tlen = data.getInt();
		byte[] t = new byte[tlen];
		data.get(t);
		textOut.append(new String(t));
		textOut.append("\n");
	}

	public void userJoined(byte[] user) {
		try {
			((DefaultListModel)(userList.getModel())).addElement(new UserID(user));
			userList.repaint();
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void userLeft(byte[] user) {
		try {
			((DefaultListModel)userList.getModel()).removeElement(new UserID(user));
			userList.repaint();
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void close() {
		close();

	}

}
