package lesson1;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.PasswordAuthentication;
import java.util.Properties;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;

@SuppressWarnings("serial")
public class HelloUserClient extends JFrame implements SimpleClientListener {
	// name of server and TCP/IP port the client will connect to
	// 
	private static final String SERVERNAME = "localhost";
	private static final String SERVERPORT = "1139";
	private SimpleClient simpleClient= null;
	private JTextArea outputText;
	private JTextField inputText;
	private JLabel statusLabel;
	private boolean loggedIn=false;

	public HelloUserClient(){
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
					simpleClient.send(inputText.getText().getBytes());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
			
		});
		appPanel.add(inputText, BorderLayout.SOUTH);
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
		// in this trivial app we've hardcoded it to "guest+<random>","guest"
		String player = "guest"+(int)(Math.random()*1000L);
		setStatus("Submitting username,password (\""+player+"\"guest\")");
		return new PasswordAuthentication(player,"guest".toCharArray());
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
		// TODO Auto-generated method stub
		return null;
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
		new HelloUserClient();
	}

}
