package com.sun.sgs.test.client.simple;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.PasswordAuthentication;
import java.util.Properties;

import javax.swing.*;

import com.sun.sgs.app.ChannelListener;
import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.SessionId;
import com.sun.sgs.client.simple.SimpleClient;
import com.sun.sgs.client.simple.SimpleClientListener;

public class SimpleClientTestUI extends JFrame {

    private static final long serialVersionUID = 1L;

    private final static String TITLE = "Simple Client Test";
    
    private SimpleClient client;
    private DefaultListModel channelModel;
    private JTextArea channelField;
    
    
    public SimpleClientTestUI() {
        super(TITLE);
        
        client = new SimpleClient(new TestSimpleClientListener());
        
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        
        JButton loginButton = new JButton("Login");
        loginButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Properties props = new Properties();
                props.put("host", "");
                props.put("port", "10002");
                
                try {
                    client.login(props);
                }
                catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        });

        JButton reconnectButton = new JButton("Reconnect");
        reconnectButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                sendServerMessage("Reconnect");
            }
        });
        
        JButton disconnectButton = new JButton("Disconnect");
        disconnectButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                client.logout(false);
            }
        });
        
        
        JPanel southPanel = new JPanel();
        southPanel.add(loginButton);
        southPanel.add(reconnectButton);
        southPanel.add(disconnectButton);
        
        add(doNorthLayout(), BorderLayout.NORTH);
        add(doCenterLayout(), BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
        
        addWindowListener(new WindowAdapter() {
           public void windowClosing(WindowEvent e) {
               shutdown();
           }
        });
        
        int size = 400;
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        setBounds((screenSize.width / 2) - (size / 2), (screenSize.height / 2) -
                  (size / 2), 300, 400);
        
        setVisible(true);
    }
    
    private void sendServerMessage(String message) {
        try {
            sendMessage(message.getBytes("UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }
    
    private JPanel doCenterLayout() {
        JPanel p = new JPanel();
        
        JButton joinButton = new JButton("Join Channel");
        joinButton.addActionListener(new ActionListener() {
           public void actionPerformed(ActionEvent e) {
               sendServerMessage("Join Channel");
           }
        });
        
        JButton leaveButton = new JButton("Leave Channel");
        leaveButton.addActionListener(new ActionListener() {
           public void actionPerformed(ActionEvent e) {
               sendServerMessage("Leave Channel");
           }
        });
        
        p.add(joinButton);
        p.add(leaveButton);
        
        return p;
    }
    
    private void sendMessage(byte[] message) {
        client.send(message);
    }
    
    private JPanel doNorthLayout() {
        JPanel p = new JPanel(new BorderLayout());

        JList channelList = new JList(channelModel = new DefaultListModel());
        
        //JSplitPane splitPane = new JSplitPane();
        //splitPane.setLeftComponent(new JScrollPane(channelList));
        
        //p.add(splitPane);
        p.add(new JScrollPane(channelList), BorderLayout.NORTH);
        
        channelField = new JTextArea(5, 10);
        channelField.setLineWrap(true);
        channelField.setWrapStyleWord(true);
        
        p.add(new JScrollPane(channelField), BorderLayout.CENTER);
        
        
        return p;
    }
    
    private void addChannelMessage(ClientChannel channel, String message) {
        channelField.setText(channelField.getText() + "<" + channel.getName() +
                ">: " + message + "\n");
    }
    
    private void shutdown() {
        System.exit(0);
    }
    
    private void setStatus(String status) {
        setTitle(TITLE + " : " + status);
    }

    
    private class TestSimpleClientListener implements SimpleClientListener, 
                                                        ClientChannelListener {
        
        public PasswordAuthentication getPasswordAuthentication(String prompt) {
            JTextField usernameField = new JTextField("sten");
            JPasswordField passwordField = new JPasswordField("secret");
            JPanel authPanel = new JPanel(new GridLayout(2, 2));
            authPanel.add(new JLabel("Username:"));
            authPanel.add(usernameField);
            authPanel.add(new JLabel("Password:"));
            authPanel.add(passwordField);

            final JOptionPane optionPane = new JOptionPane(authPanel, 
                        JOptionPane.QUESTION_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
            JDialog dialog = optionPane.createDialog(null, "Enter Credentials");
            dialog.setVisible(true);
            Integer ret = (Integer) optionPane.getValue();
            if (ret == null|| ret.intValue() == JOptionPane.CANCEL_OPTION) {
                return null;
            }
            PasswordAuthentication auth = new PasswordAuthentication(
                            usernameField.getText(), passwordField.getPassword());
            
            return auth;
        }

        public void loggedIn() {
            setStatus("Logged In");
            
        }
    
        public void loginFailed(String reason) {
            setStatus("Login Failed");
        }
    
        public void disconnected(boolean graceful) {
            setStatus("Disconnected");
                
        }
    
        public ClientChannelListener joinedChannel(ClientChannel channel) {
            channelModel.addElement(channel.getName());
            
            return this;
        }
    
        public void receivedMessage(byte[] message) {
    
        }
    
        public void reconnected() {
            setStatus("Reconnected");
        }
    
        public void reconnecting() {
            setStatus("Reconnecting...");
        }
        
        public void leftChannel(ClientChannel channel) {
            channelModel.removeElement(channel.getName());
        }

        public void receivedMessage(ClientChannel channel, SessionId sender, 
                                                                byte[] message) {
            try {
                addChannelMessage(channel, DataInputStream.readUTF(
                        new DataInputStream(new ByteArrayInputStream(message))));
            }
            catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
    }
    
    public final static void main(String arg[]) {
        new SimpleClientTestUI();
    }

}
