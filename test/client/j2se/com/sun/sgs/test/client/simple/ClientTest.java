package com.sun.sgs.test.client.simple;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.PasswordAuthentication;
import java.util.Properties;


import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.ServerSession;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;

/**
 * A basic test harness for the Client API.
 * 
 * @author      Sten Anderson
 * @version     1.0
 */
public class ClientTest implements SimpleClientListener {

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
        props.put("host", "127.0.0.1");
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
        // TODO Auto-generated method stub
        
    }

    public PasswordAuthentication getPasswordAuthentication(String prompt) {
        PasswordAuthentication auth = new PasswordAuthentication("guest", 
                new char[] {'g', 'u', 'e', 's', 't'});
        return auth;
    }

    public void loggedIn() {
        System.out.println("Logged In");
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
        // TODO Auto-generated method stub
        return null;
    }

    public void receivedMessage(byte[] message) {
        // TODO Auto-generated method stub
        
    }
}
