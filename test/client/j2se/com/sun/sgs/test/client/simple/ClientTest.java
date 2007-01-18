package com.sun.sgs.test.client.simple;

import java.io.IOException;
import java.net.PasswordAuthentication;
import java.util.Properties;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.ServerSession;
import com.sun.sgs.client.SessionId;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;

/**
 * A basic test harness for the Client API.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class ClientTest implements SimpleClientListener, ClientChannelListener {

    private SimpleClient client;
    
    public ClientTest() {
        //super("Simple Client Test");
        client = new SimpleClient(this);
        
       /* JButton startButton = new JButton("Connect");
        startButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                start();
            }
        });
        
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        
        addWindowListener(new WindowAdapter() {
           public void windowClosing(WindowEvent e) {
               shutdown();
           }
        });
        
        JPanel bottomPanel = new JPanel();
        bottomPanel.add(startButton);
        
        add(bottomPanel, BorderLayout.SOUTH);
        
        setBounds(200, 200, 600, 300);
        
        setVisible(true);
         
         */
    }
    
    private void shutdown() {
        System.exit(0);
    }
    
    private void start() {
        Properties props = new Properties();
        props.put("host", "");
        props.put("port", "10002");
        
        try {
            client.login(props);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    
    public final static void main(String[] args) {
        new ClientTest().start();
    }

    public void disconnected(boolean graceful) {
        System.out.println("disconnected graceful: " + graceful);
        System.exit(0);
    }

    public PasswordAuthentication getPasswordAuthentication(String prompt) {
        PasswordAuthentication auth = new PasswordAuthentication("guest", 
                new char[] {'g', 'u', 'e', 's', 't'});
        return auth;
    }

    public void loggedIn() {
        System.out.println("Logged In");
        client.send("some message".getBytes());
    }

    public void loginFailed(String reason) {
        System.out.println("Log in Failed " + reason);
    }

    public void reconnected() {
        // TODO Auto-generated method stub
        
    }

    public void reconnecting() {
        // TODO Auto-generated method stub
        
    }

    public void connected(ServerSession session) {
        // TODO Auto-generated method stub
        System.out.println("connected");
        
    }

    public ClientChannelListener joinedChannel(ClientChannel channel) {
        System.out.println("ClientTest joinedChannel: " + channel.getName());
        
        return this;
    }

    public void receivedMessage(byte[] message) {
        System.out.println("Received general server message size " + 
            message.length + " " + new String(message, 4, message.length - 4));        
    }

    // methods inherited from ClientChannelListener
    
    public void leftChannel(ClientChannel channel) {
        System.out.println("ClientTest leftChannel " + channel.getName());
        client.logout(false);
    }

    public void receivedMessage(ClientChannel channel, SessionId sender, byte[] message) {
        System.out.println("ClientTest receivedChannelMessage " + channel.getName() + 
                " from " + (sender != null ? sender.toString() : " Server ") + 
                new String(message));
        
        
        channel.send("client message".getBytes());
    }
}
