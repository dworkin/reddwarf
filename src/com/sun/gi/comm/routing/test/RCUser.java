package com.sun.gi.comm.routing.test;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.security.auth.callback.Callback;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.server.SGSUser;
import com.sun.gi.utils.types.BYTEARRAY;

@SuppressWarnings("serial")
public class RCUser extends JInternalFrame implements SGSUser {
	private JDesktopPane desktop;
	private Map<BYTEARRAY,RCChannelFrame> channelMap = new HashMap<BYTEARRAY,RCChannelFrame>();
	private JLabel status;
	private JList userList;
	private UserID myID;
	final JButton connectButton;
	private Router myRouter;
	private JButton openChanButton;
	
		
	public RCUser(Router router, String userName){
		super(userName);
		myRouter = router;
		setSize(200,200);
		Container c = getContentPane();	
		desktop = new JDesktopPane();
		c.setLayout(new BorderLayout());
		c.add(desktop,BorderLayout.CENTER);
		JPanel controlPanel = new JPanel();
		controlPanel.setLayout(new GridLayout(0,1));
		status = new JLabel("Status: Not connected");
		controlPanel.add(status);
		connectButton = new JButton("connect");
		controlPanel.add(connectButton);
		connectButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (connectButton.getText().equalsIgnoreCase("connect")){
					doConnect();
				} else if (connectButton.getText().equalsIgnoreCase("disconnect")){
					doDisconnect();
				}
				
			}
			
		});
		openChanButton = new JButton("open channel");
		controlPanel.add(openChanButton);
		openChanButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				doOpenChan();				
			}
			
		});
		openChanButton.setEnabled(false);
		c.add(controlPanel,BorderLayout.SOUTH);
		JPanel usersPanel = new JPanel();
		usersPanel.setLayout(new BorderLayout());
		JLabel userLabel = new JLabel("Users");
		usersPanel.add(userLabel,BorderLayout.NORTH);
		userList = new JList(new DefaultListModel());
		usersPanel.add(new JScrollPane(userList),BorderLayout.CENTER);
		c.add(usersPanel,BorderLayout.EAST);
		this.setResizable(true);
		setVisible(true);
		
	}
	
	protected void doOpenChan() {
		String channelName = JOptionPane.showInputDialog(this,"Enter channel name");				
		SGSChannel chan = myRouter.openChannel(channelName);
		chan.join(this);
	}

	protected void doDisconnect() {
		connectButton.setText("Disconnecting....");
		connectButton.setEnabled(false);
		openChanButton.setEnabled(false);
		myRouter.deregisterUser(this);
	}

	protected void doConnect() {
		try {
			myRouter.registerUser(this);
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}

	public void joinedChan(SGSChannel chan) throws IOException {
		RCChannelFrame chanFrame = new RCChannelFrame(myID,chan);
		desktop.add(chanFrame);
		chanFrame.setSize(100,100);
		chanFrame.setVisible(true);
		channelMap.put(new BYTEARRAY(chan.channelID().toByteArray()),chanFrame);
		chanFrame.userJoined(myID.toByteArray());
		desktop.repaint();
	}

	public void msgReceived(byte[] channel, byte[] from, boolean reliable,
			ByteBuffer data) throws IOException {
		RCChannelFrame cframe = channelMap.get(new BYTEARRAY(channel));
		cframe.receive(from,reliable,data);		
	}

	public void validated() throws IOException {
		status.setText("Status: Accepted");
		connectButton.setText("Disconnect");
		connectButton.setEnabled(true);
		openChanButton.setEnabled(true);
		status.repaint();
	}

	public void invalidated(String message) throws IOException {
		status.setText("Status: Rejected");
		connectButton.setText("Connect");
		connectButton.setEnabled(true);
		status.repaint();
	}

	public void validationRequested(Callback[] cbs) throws IOException {
		// TODO Auto-generated method stub

	}

	public void userJoinedSystem(byte[] user) throws IOException {
		try {
			((DefaultListModel)userList.getModel()).addElement(new UserID(user));
			userList.repaint();
		} catch (InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void userLeftSystem(byte[] user) throws IOException {
		DefaultListModel mdl =((DefaultListModel)userList.getModel());
		Enumeration e = mdl.elements();
		while (e.hasMoreElements()){
			UserID id = (UserID)e.nextElement();
			if (id.equals(user)){
				mdl.removeElement(id);
				userList.repaint();
				return;
			}
		}

	}

	public void userJoinedChannel(byte[] channelID, byte[] user)
			throws IOException {
		RCChannelFrame rcf = channelMap.get(new BYTEARRAY(channelID));
		rcf.userJoined(user);

	}

	public void userLeftChannel(byte[] channel, byte[] user)
			throws IOException {
		RCChannelFrame rcf = channelMap.get(new BYTEARRAY(channel));
		rcf.userLeft(user);

	}

	public void reconnectKeyReceived(byte[] key) throws IOException {
		// TODO Auto-generated method stub

	}

	public void setUserID(UserID id) {
		myID = id;

	}

	public UserID getUserID() {
		// TODO Auto-generated method stub
		return myID;
	}

	public void disconnected() {
		// TODO Auto-generated method stub

	}

	public void leftChan(SGSChannel channel) {
		RCChannelFrame rcf = channelMap.get(new BYTEARRAY(channel.channelID().toByteArray()));
		rcf.close();

	}

	public void userDisconnected() {
		status.setText("Status: Disconnected");
		connectButton.setText("Connect");
		connectButton.setEnabled(true);
		((DefaultListModel)userList.getModel()).clear();
		status.repaint();
		
	}

	public void deregistered() {
		// TODO Auto-generated method stub
		
	}

}
