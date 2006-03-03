 /*****************************************************************************
     * Copyright (c) 2006 Sun Microsystems, Inc.  All Rights Reserved.
     * Redistribution and use in source and binary forms, with or without
     * modification, are permitted provided that the following conditions are met:
     *
     * - Redistribution of source code must retain the above copyright notice,
     *   this list of conditions and the following disclaimer.
     *
     * - Redistribution in binary form must reproduce the above copyright notice,
     *   this list of conditions and the following disclaimer in the documentation
     *   and/or other materails provided with the distribution.
     *
     * Neither the name Sun Microsystems, Inc. or the names of the contributors
     * may be used to endorse or promote products derived from this software
     * without specific prior written permission.
     *
     * This software is provided "AS IS," without a warranty of any kind.
     * ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
     * ANY IMPLIED WARRANT OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
     * NON-INFRINGEMEN, ARE HEREBY EXCLUDED.  SUN MICROSYSTEMS, INC. ("SUN") AND
     * ITS LICENSORS SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS
     * A RESULT OF USING, MODIFYING OR DESTRIBUTING THIS SOFTWARE OR ITS
     * DERIVATIVES.  IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE FOR ANY LOST
     * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
     * INCIDENTAL OR PUNITIVE DAMAGES.  HOWEVER CAUSED AND REGARDLESS OF THE THEORY
     * OF LIABILITY, ARISING OUT OF THE USE OF OUR INABILITY TO USE THIS SOFTWARE,
     * EVEN IF SUN HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
     *
     * You acknowledge that this software is not designed or intended for us in
     * the design, construction, operation or maintenance of any nuclear facility
     *
     *****************************************************************************/


package com.sun.gi.apps.chattest.client;

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


/**
 * <p>The ChatChannelFrame presents a GUI so that a user can interact with a channel.  The users connected
 * to the channel are displayed in a list on the right side.  Messages can be sent on the channel via an
 * input area on the left side.</p>
 * 
 * <p>This class communicates with its channel by implementing ClientChannelListener, and signing up as
 * a listener on the channel.  As data arrives, and players leave or join, the appropriate call backs are 
 * called.</p>
 */

//@SuppressWarnings("serial")
public class ChatChannelFrame extends JInternalFrame implements ClientChannelListener{
	final ClientChannel chan;
	JList userList;
	JTextField inputField;
	JTextArea outputArea;
	ByteBuffer outbuff;
	
	/**
	 * Constructs a new ChatChannelFrame as a wrapper around the given channel.
	 * 
	 * @param channel		the channel that this class will manage.
	 */
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
	
	
	/**
	 * A call back from ClientChannelListener.  Called when a player/user joins the channel.
	 * This implementation responds by adding the user to the list.
	 */
	public void playerJoined(byte[] playerID) {
		DefaultListModel mdl = (DefaultListModel)userList.getModel();
		mdl.addElement(new BYTEARRAY(playerID));
		
	}
	
	/**
	 * A call back from ClientChannelListener.  Called when a player/user leaves the channel.
	 * This implementation responds by removing the user from the user list.  
	 */
	public void playerLeft(byte[] playerID) {
		DefaultListModel mdl = (DefaultListModel)userList.getModel();
		mdl.removeElement(new BYTEARRAY(playerID));		
	}
	
	/**
	 * A call back from ClientChannelListener.  Called when data arrives on the channel.  
	 * This implementation simply dumps the data to the output area as a String in the form of:
	 * 
	 * <pre>&lt;User who sent the message&lt;: &lt;Message&lt;</pre>
	 */
	public void dataArrived(byte[] from, ByteBuffer data, boolean reliable){	
		byte[] textb =new byte[data.remaining()];
		data.get(textb);
		outputArea.append(StringUtils.bytesToHex(from,from.length-4)+": "+ new String(textb)+"\n");	
	}
	
	/**
	 * Called when the channel is closed.  The frame has no need to exist if the channel is closed,
	 * so it removes itself from the parent.
	 */
	public void channelClosed() {
		if (getDesktopPane() != null) {
			getDesktopPane().remove(this);
		}
	}
}
