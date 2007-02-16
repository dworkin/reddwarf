package lesson2;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.PasswordAuthentication;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.SessionId;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;

@SuppressWarnings("serial")
public class HelloChannelClient extends JFrame implements SimpleClientListener {
	// name of server and TCP/IP port the client will connect to
	// 
	private static final String SERVERNAME = "localhost";
	private static final String SERVERPORT = "1139";
	private SimpleClient simpleClient= null;
	private JTextArea outputText;
	private JTextField inputText;
	private JLabel statusLabel;
	private boolean loggedIn=false;
	private Map<String,ClientChannel> channelMap = new HashMap<String, ClientChannel>();
	private DefaultComboBoxModel channelSelectorModel = new DefaultComboBoxModel();
	private JComboBox channelSelector;

	public HelloChannelClient(){
		Container c = getContentPane();
		JPanel appPanel = new JPanel();
		c.setLayout(new BorderLayout());
		appPanel.setLayout(new BorderLayout());
		outputText = new JTextArea();
		appPanel.add(new JScrollPane(outputText), BorderLayout.CENTER);
		inputText = new JTextField();
		inputText.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent arg0) {
				if (!loggedIn) return;
				try {
					String channelName = (String)channelSelector.getSelectedItem();
					if (channelName.equalsIgnoreCase("<DIRECT>")){
						simpleClient.send(inputText.getText().getBytes());
					} else {
						ClientChannel chan = channelMap.get(channelName);
						chan.send(inputText.getText().getBytes());
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
			
		});
		channelSelectorModel.addElement("<DIRECT>");
		JPanel inputPanel = new JPanel();
		inputPanel.setLayout(new BorderLayout());
		channelSelector = new JComboBox(channelSelectorModel);
		inputPanel.add(channelSelector, BorderLayout.WEST);
		inputPanel.add(inputText,BorderLayout.CENTER);
		appPanel.add(inputPanel, BorderLayout.SOUTH);
		c.add(appPanel,BorderLayout.CENTER);
		statusLabel = new JLabel();
		setStatus("Not Started");
		c.add(statusLabel,BorderLayout.SOUTH);
		setSize(640,480);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setVisible(true);
		initCommunications();
	}

	private void initCommunications() {
		simpleClient = new SimpleClient(this);
		setStatus("Beginning Login");
		Properties connectProperties = new Properties();
		connectProperties.setProperty("host", SERVERNAME);
		connectProperties.setProperty("port", SERVERPORT);
		try {
			simpleClient.login(connectProperties);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	//SImple Client Listener Methods
	public PasswordAuthentication getPasswordAuthentication() {
		// This is called to get the user name and authentication data (eg password)
		// to be authenticated server side.
		// In a real app there would be some sort of user prompt,
		// in this trivial app we've hardcoded it to "guest","guest"
		String user  = "guest"+(int)(Math.random()*1000L);
		setStatus("Submitting username,password (\""+
				user+
				"\",\"guest\")");
		return new PasswordAuthentication(user,"guest".toCharArray());
	}

	public void loggedIn() {
		loggedIn = true;
		setStatus("Logged In");
	}

	private void setStatus(String string) {
		outputText.append("Status Set: "+string+"\n");
		statusLabel.setText("Status: "+string);
		outputText.repaint();
	}

	public void loginFailed(String reason) {
		setStatus("Log in failed: "+reason);
		
	}

	public void disconnected(boolean graceful, String reason) {
		loggedIn = false;
		setStatus("Disconnected: "+reason);
		
	}

	public ClientChannelListener joinedChannel(ClientChannel channel) {
		channelMap.put(channel.getName(),channel);
		outputText.append("Joined to channel "+channel.getName()+"\n");
		channelSelectorModel.addElement(channel.getName());
		return new HelloChannelListener(channel);
	}

	public void receivedMessage(byte[] message) {
		outputText.append("Server: "+new String(message)+"\n");
		
	}

	public void reconnected() {
		setStatus("re-connected");
		
	}

	public void reconnecting() {
		setStatus("reconnecting");
		
	}
	
	public static void main(String[] args){
		new HelloChannelClient();
	}
	
	private class HelloChannelListener implements ClientChannelListener {

		private ClientChannel channel;

		public HelloChannelListener(ClientChannel channel) {
			this.channel = channel;
		}

		public void leftChannel(ClientChannel channel) {
			outputText.append("Removed from channel "+channel.getName()+"\n");
			
		}

		public void receivedMessage(ClientChannel channel, SessionId sender, byte[] message) {
			outputText.append(channel.getName()+"("+
					new String(sender.toBytes())+"): "+
					new String(message)+"\n");			
		}
		
	}

}
