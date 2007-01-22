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
public class ClientTest
    implements SimpleClientListener, ClientChannelListener
{
    private SimpleClient client;
    
    public ClientTest() {
        client = new SimpleClient(this);
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
        ClientTest client = new ClientTest();
        synchronized(client) {
            client.start();
            try {
                client.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
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
        try {
            client.send("Join Channel".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        
        
        try {
            channel.send("client message".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
