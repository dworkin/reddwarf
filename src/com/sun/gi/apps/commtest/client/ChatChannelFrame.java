package com.sun.gi.apps.commtest.client;

import java.awt.BorderLayout;
import java.awt.Component;
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
import javax.swing.ListCellRenderer;
import javax.swing.event.InternalFrameEvent;
import javax.swing.event.InternalFrameListener;

import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.utils.types.StringUtils;
import com.sun.gi.utils.types.BYTEARRAY;

//@SuppressWarnings("serial")
public class ChatChannelFrame extends JInternalFrame implements ClientChannelListener{
	final ClientChannel chan;
	JList userList;
	JTextField inputField;
	JTextArea outputArea;
	ByteBuffer outbuff;
	public ChatChannelFrame(ClientChannel channel){
		super("Channel: "+channel.getName());
		outbuff = ByteBuffer.allocate(2048);
		chan = channel;
		chan.setListener(this);
		Container c = getContentPane();
		c.setLayout(new BorderLayout());
		JPanel eastPanel = new JPanel();
		eastPanel.setLayout(new BorderLayout());
		c.add(eastPanel,BorderLayout.EAST);
		eastPanel.add(new JLabel("Users"),BorderLayout.NORTH);
		userList = new JList(new DefaultListModel());
		userList.setCellRenderer(new ListCellRenderer(){
			JLabel text = new JLabel();
			public Component getListCellRendererComponent(JList arg0, Object arg1, int arg2, boolean arg3, boolean arg4) {
				byte[] data = ((BYTEARRAY)arg1).data();
				text.setText(StringUtils.bytesToHex(data,data.length-4));
				return text;
			}});
		eastPanel.add(new JScrollPane(userList),BorderLayout.CENTER);
		JPanel southPanel = new JPanel();		
		c.add(southPanel,BorderLayout.SOUTH);
		southPanel.setLayout(new GridLayout(1,0));
		inputField = new JTextField();
		southPanel.add(inputField);
		outputArea = new JTextArea();
		c.add(new JScrollPane(outputArea),BorderLayout.CENTER);
		inputField.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e) {
				outbuff.clear();
				outbuff.put(inputField.getText().getBytes());
				chan.sendBroadcastData(outbuff,true);
				inputField.setText("");
			}});
		setSize(400,400);
		this.setClosable(true);
		this.setDefaultCloseOperation(JInternalFrame.DISPOSE_ON_CLOSE);
		this.addInternalFrameListener(new InternalFrameListener(){

			public void internalFrameOpened(InternalFrameEvent arg0) {
				// TODO Auto-generated method stub
				
			}

			public void internalFrameClosing(InternalFrameEvent arg0) {
				// TODO Auto-generated method stub
				
			}

			public void internalFrameClosed(InternalFrameEvent arg0) {
				chan.close();
			}

			public void internalFrameIconified(InternalFrameEvent arg0) {
				// TODO Auto-generated method stub
				
			}

			public void internalFrameDeiconified(InternalFrameEvent arg0) {
				// TODO Auto-generated method stub
				
			}

			public void internalFrameActivated(InternalFrameEvent arg0) {
				// TODO Auto-generated method stub
				
			}

			public void internalFrameDeactivated(InternalFrameEvent arg0) {
				// TODO Auto-generated method stub
				
			}});
		setResizable(true);
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
	public void dataArrived(byte[] from, ByteBuffer data, boolean reliable){	
		byte[] textb =new byte[data.remaining()];
		data.get(textb);
		outputArea.append(StringUtils.bytesToHex(from,from.length-4)+": "+ new String(textb)+"\n");	
	}
	public void channelClosed() {
		if (getDesktopPane() != null) {
			getDesktopPane().remove(this);
		}
	}
}
