/*
 * JMEChatClient.java
 *
 * Created on February 14, 2006, 8:25 AM
 */

package com.sun.gi.apps.commtest.client;

import com.sun.gi.comm.users.client.JMEClientListenerInterface;
import com.sun.gi.comm.users.client.impl.JMEClientManager;
import com.sun.gi.comm.discovery.impl.JMEDiscoverer;
import com.sun.gi.utils.jme.ByteBuffer;
import com.sun.gi.utils.jme.Callback;
import com.sun.gi.utils.jme.NameCallback;
import com.sun.gi.utils.jme.PasswordCallback;
import com.sun.gi.utils.jme.StringUtils;
import com.sun.gi.utils.jme.UnsupportedCallbackException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

/**
 *
 * @author as93050
 */
public class JMEChatClient extends MIDlet implements CommandListener, JMEClientListenerInterface {
    
    private JMEClientManager clientMgr;    
    private Command logoutCommand;
    private Form loginForm;
    private Command exitCommand;
    private TextField userNameField;
    private TextField passwordField;
    private Command loginCommand;
    private Form postLoginForm;
    private TextField channelNameField;
    private StringItem myUserIDItem;
    private Command channelOk;
    private Command channelSend;
    private Command serverMessage;
    private Hashtable users;
    private Hashtable channels;
    private Alert genericAlert;
    private Command sendCommand;
    private Displayable shouldBeCurrent;
    private ChoiceGroup usersList;
    private TextField messageField;
    private ChoiceGroup channelList;
    
    private byte[] channelID;
    private String userid;
    private static String DCC_CHAN_NAME = "__DCC_Chan";
    private byte[] dccChannelId;
    private int currentState = LOGGED_OUT;
    
    private final static int LOGGED_OUT = 0;
    private final static int LOGGED_IN = 1;
    private final static int OPENED_CHANNEL = 2;
    
    private final static int LOGON = 0;
    private final static int LOGON_REJECT = 1;
    private final static int CHANNEL_OPENED = 2;
    private final static int SENT_MESSAGE = 3;
    private final static int LOGOUT = 4;
    private final static int USER_LOGGED_IN = 5;
    private final static int USER_LOGGED_OUT = 6;
    private final static int USER_JOINED_CHAN = 7;
    private final static int USER_LEFT_CHAN = 8;
    private final static int JOINED_CHAN = 9;
    private final static int EXCEPTION_OCCURRED = 10;
    private final static int START_APP = 11;

    /** Creates a new instance of HelloMidlet */
    public JMEChatClient() {
        users = new Hashtable();
        channels = new Hashtable();
    }
    
    /** This method initializes UI of the application.
     */
    private void initialize() {
        createGuiComponents();
        getDisplay().setCurrent(changeState(START_APP));
        shouldBeCurrent = getDisplay().getCurrent();
    }
    
    /** Called by the system to indicate that a command has been invoked on a particular displayable.
     * @param command the Command that ws invoked
     * @param displayable the Displayable on which the command was invoked
     */
    public void commandAction(Command command, Displayable displayable) {
        if (displayable == loginForm) {
            if (command == exitCommand) {
                doLogout();
                exitMIDlet();
            } else if (command == loginCommand) {
                initializeClientManager();
            }
        } else if (displayable == postLoginForm) {
            if (command == channelOk) {
                clientMgr.openChannel(channelNameField.getString());
            } else if (command == logoutCommand) {
                doLogout();
                getDisplay().setCurrent(loginForm);
            } else if (command == sendCommand) {
                sendMessageToUser();
            } else if (command == channelSend) {
                sendChannelMessage();
            } else if (command == serverMessage) {
                sendServerMessage();
            }
        }
    }
    
    /**
     * This method should return an instance of the display.
     */
    public Display getDisplay() {
        return Display.getDisplay(this);
    }
    
    private void createGuiComponents() {
        createLoginForm();
        createPostLogonScreen();
        
    }
    /**
     * This method should exit the midlet.
     */
    public void exitMIDlet() {
        getDisplay().setCurrent(null);
        destroyApp(true);
        notifyDestroyed();
    }
    
    /** This method returns instance for LoginForm component and should be called instead of accessing LoginForm field directly.
     * @return Instance for LoginForm component
     */
    public void createLoginForm() {
        createLoginFormComponents();
        loginForm = new Form("Login Screen", new Item[] {
            userNameField,
            passwordField
        });
        loginForm.addCommand(exitCommand);
        loginForm.addCommand(loginCommand);
        loginForm.setCommandListener(this);
    }
    
    private void createLoginFormComponents() {
        exitCommand = new Command("Exit", Command.EXIT, 1);
        userNameField = new TextField("Userid", "Ari", 16, TextField.ANY);
        passwordField = new TextField("Password", "password", 16, TextField.PASSWORD);
        loginCommand = new Command("Ok", Command.OK, 1);
    }
    
    private void createPostLogonScreen() {
        createPostLogonComponents();
        postLoginForm = new Form(null, new Item[] {channelNameField,
        usersList,messageField,myUserIDItem});
        postLoginForm.addCommand(channelOk);
        postLoginForm.addCommand(sendCommand);
        postLoginForm.addCommand(logoutCommand);
        postLoginForm.addCommand(serverMessage);
        postLoginForm.setCommandListener(this);
    }
    
    private void createPostLogonComponents() {
        channelNameField = new TextField("Channel Name", null, 120, TextField.ANY);
        usersList = new ChoiceGroup("Logged In Users",ChoiceGroup.MULTIPLE);
        messageField = new TextField("Message", "", 120, TextField.ANY);
        channelOk = new Command("OpenChannel", Command.OK, 1);
        sendCommand = new Command("Send message", Command.OK, 2);
        logoutCommand = new Command("Logout",Command.CANCEL,2);
        channelSend = new Command("Send to Channel", Command.OK, 1);
        serverMessage = new Command("Server Message", Command.OK,3);
        channelList = new ChoiceGroup("Open Channels",ChoiceGroup.EXCLUSIVE);
        usersList = new ChoiceGroup("Logged In Users",ChoiceGroup.MULTIPLE);
        myUserIDItem = new StringItem("My User ID ","");
    }
    
    private void updatePostLoginScreen() {
        postLoginForm.addCommand(channelSend);
        postLoginForm.append(channelList);
    }
    
    private Alert getGenericAlert(String alertText) {
        if (genericAlert == null) {
            genericAlert = new Alert(null, alertText, null, AlertType.INFO);
            genericAlert.setTimeout(-2);
        } else {
            genericAlert.setString(alertText);
        }
        return genericAlert;
    }
    
    private Displayable changeState(int event) {
        Displayable newScreen = shouldBeCurrent;
        switch (currentState) {
            case LOGGED_OUT:
                if (event == LOGON) {
                    currentState = LOGGED_IN;
                    newScreen = postLoginForm;
                    shouldBeCurrent = newScreen;
                } else if (event == LOGON_REJECT) {
                    newScreen = loginForm;
                    shouldBeCurrent = newScreen;
                } else if (event == START_APP) {
                    newScreen = loginForm;
                    shouldBeCurrent = newScreen;
                }
                break;
            case LOGGED_IN:
                if (event == LOGOUT) {
                    currentState = LOGGED_OUT;
                    newScreen = loginForm;
                    shouldBeCurrent = newScreen;
                } else if (event == JOINED_CHAN) {
                    currentState = CHANNEL_OPENED;
                    updatePostLoginScreen();
                    newScreen = postLoginForm;
                    shouldBeCurrent = newScreen;
                } else if (event == START_APP) {
                    newScreen = postLoginForm;
                    shouldBeCurrent = newScreen;
                }
            case OPENED_CHANNEL:
                if (event == START_APP) {
                    currentState = CHANNEL_OPENED;
                    updatePostLoginScreen();
                    newScreen = postLoginForm;
                    shouldBeCurrent = newScreen;
                }
            default:
                
        }
        return newScreen;
    }
    
    public void startApp() {
        initialize();
    }
    
    public void pauseApp() {
        clientMgr.stopPolling();
    }
    
    public void destroyApp(boolean unconditional) {
    }
    
    /**
     * This event informs us that further information is
     * needed in order to validate the user.
     * @param cbs An array of Callback structures to be filled out and sent back.
     */
    public void validationDataRequest(Callback[] callbacks) {
        dealWithCallbacks(callbacks);
        try {
            clientMgr.sendValidationResponse(callbacks);
        } catch (UnsupportedCallbackException ex) {
            exceptionOccurred(ex);
        }
    }
    
    /**
     * This event informs us that the user has successfully logged in
     *
     * @param userID An ID issued to represent this user.
     * <b>UserIDs are not guaranteed to remain the same between logons.</b>
     */
    public void loginAccepted(byte[] userID) {
        clientMgr.openChannel(DCC_CHAN_NAME);
        Displayable newScreen = changeState(LOGON);
        myUserIDItem.setText(StringUtils.bytesToHex(userID));
        getDisplay().setCurrent(getGenericAlert("Login Successful for " + StringUtils.bytesToHex(userID)), newScreen);
    }
    
    /**
     * This event informs us that user validation has failed.
     * @param message A string containing an explaination of the failure.
     */
    public void loginRejected(String message) {
        Displayable newScreen = changeState(LOGON_REJECT);
        getDisplay().setCurrent(getGenericAlert(message), newScreen);
    }
    
    /**
     * This event informs us about other users of the game.
     * When logon first succeeds there will be one of these callabcks sent for every currently
     * logged-in user of this game.  As other users join, additional callabcks will be issued for them.
     * @param userID The ID of the other user
     */
    public void userLoggedIn(byte[] userID) {
        String userName = StringUtils.bytesToHex(userID);
        users.put(userName,userID);
        usersList.append(userName,null);
        Displayable newScreen = changeState(USER_LOGGED_IN);
        getDisplay().setCurrent(getGenericAlert("User added " + userName), newScreen);
    }
    
    /**
     * This event informs us that a user has logged out of the game.
     * @param userID The user that logged out.
     */
    public void userLoggedOut(byte[] userID) {
        Displayable newScreen = changeState(USER_LOGGED_OUT);
        usersList.deleteAll();
        String userName = StringUtils.bytesToHex(userID);
        users.remove(userName);
        Enumeration userIds = users.keys();
        while (userIds.hasMoreElements()) {
            usersList.append((String) userIds.nextElement(), null);
        }
        getDisplay().setCurrent(getGenericAlert("User dropped " + StringUtils.bytesToHex(userID)), newScreen);
    }
    
    /**
     * This event informs us of the successful opening of a channel.
     * @param channelID The ID of the newly joined channel
     */
    public void joinedChannel(String name, byte[] channelID) {
        if (name.equals(DCC_CHAN_NAME)) {
            dccChannelId = channelID;
        } else {
            channels.put(name,channelID);
            Displayable newScreen = changeState(JOINED_CHAN);
            channelList.append(name,null);
            getDisplay().setCurrent(getGenericAlert("Connected to Channel " + name), newScreen);
            this.channelID = channelID;
        }
    }
    
    /**
     * This callback informs the user that they have left or been removed from a channel.
     * @param channelID The ID of the channel left.
     */
    public void leftChannel(byte[] channelID) {
        Displayable newScreen = changeState(JOINED_CHAN);
        getDisplay().setCurrent(getGenericAlert("Left channel "+ StringUtils.bytesToHex(channelID)), newScreen);
    }
    
    /**
     * This method is called whenever a new user joins a channel that we have open.
     * A set of these events are sent when we first join a channel-- one for each pre-existing
     * channel mamber.  After that we get thsi event whenever someone new joins the channel.
     * @param channelID The ID of the channel joined
     * @param userID The ID of the user who joined the channel
     */
    public void userJoinedChannel(byte[] channelID, byte[] userID) {
        Displayable newScreen = changeState(USER_JOINED_CHAN);
        getDisplay().setCurrent(getGenericAlert("User joined channel " + StringUtils.bytesToHex(userID) + " channel " + StringUtils.bytesToHex(channelID)), newScreen);
        
    }
    
    /**
     * This method is called whenever another user leaves a channel that we have open.
     * @param channelID The ID of the channel left
     * @param userID The ID of the user leaving the channel
     */
    public void userLeftChannel(byte[] channelID, byte[] userID) {
        Displayable newScreen = changeState(USER_LEFT_CHAN);
        getDisplay().setCurrent(getGenericAlert("User left channel " + StringUtils.bytesToHex(userID) + " channel " + StringUtils.bytesToHex(channelID)), newScreen);
        
    }
    
    /**
     * This event informs the listener that data has arrived from the Darkstar server channels.
     * @param chanID The ID of the channel on which the data has been received
     * @param from The ID of the sender of the data
     * @param data The data itself
     */
    public void dataReceived(byte[] chanID, byte[] from, ByteBuffer data) {
        Displayable newScreen = changeState(USER_LEFT_CHAN);
        byte[] textb = new byte[data.remaining()];
        data.get(textb);
        getDisplay().setCurrent(getGenericAlert("Message from " + StringUtils.bytesToHex(chanID) + " : " + new String(textb)), newScreen);
    }
    
    public void recvServerID(byte[] user) {
    }
    
    /**
     * Send a message to all the suers on a channel
     */
    private void sendChannelMessage() {
        String dataString = messageField.getString();
        String channelName = channelList.getString(channelList.getSelectedIndex());
        ByteBuffer data = ByteBuffer.wrap(dataString.getBytes());
        clientMgr.sendBroadcastMessage((byte[]) channels.get(channelName), data);
        messageField.setString("");
    }
        
    private byte[][] getSelectedUsers(Vector selectedUsers) {
        byte[][] targetList = new byte[selectedUsers.size()][];
        for (int i = 0;i < selectedUsers.size();i++) {
            targetList[i] = (byte[]) users.get(selectedUsers.elementAt(i));
        }
            
        
        return targetList;
    }
    /**
     * Send a message to a specific user or group of users
     * We use the special DCC_CHANNEL for this
     */
    private void sendMessageToUser() {
        boolean [] selected = new boolean[users.size()];
        usersList.getSelectedFlags(selected);
        Vector selectedUsers = new Vector();
        for (int i = 0;i < selected.length;i++) {
            if (selected[i]) {
                selectedUsers.addElement(usersList.getString(i));
            }
        }
        if (selectedUsers.size() == 1) {
            String message = messageField.getString();
            //byte[] user = getSelectedUser((String) selectedUsers.firstElement());
            byte[] user = (byte[]) users.get(selectedUsers.firstElement());
            clientMgr.sendUnicastMsg(dccChannelId,user, ByteBuffer.wrap(message.getBytes()));
        } else if (selectedUsers.size() > 1) {
            byte[][] targetList = getSelectedUsers(selectedUsers);            
            clientMgr.sendMulticastData(dccChannelId,targetList,ByteBuffer.wrap(messageField.getString().getBytes()));
        }
        messageField.setString("");
    }
    /**
     * Log out of the Darkstar server
     */
    private void doLogout() {
        clientMgr.disconnect();
        users.clear();
        createPostLogonScreen();
        channels = new Hashtable();
        currentState = LOGGED_OUT;
    }
    
    /**
     * Initialize the client manager. This will start the process of game discovery.
     * This will go out to the server and discover all the available games and try to 
     * match the game name that was provided to the client manager with an existing game
     * if successful you will receive a callback discoveredGames() and can then proceed to
     * log in
     * 
     */
    private void initializeClientManager() {
        clientMgr = new JMEClientManager("ChatTest",
                new JMEDiscoverer(this.getAppProperty("XML-Discovery-URL")),
                this.getAppProperty("UsrMgrClassName"));
        clientMgr.setListener(this);
        clientMgr.discoverGames();
    }
    
    /**
     * Handle validation callbacks to login to the server
     */
    private void dealWithCallbacks(Callback[] callbacks) {
        //for now hard code dealing with the login callback
        NameCallback nm = (NameCallback)callbacks[0];
        nm.setName(userNameField.getString());
        PasswordCallback pb = (PasswordCallback)callbacks[1];
        pb.setPassword(passwordField.getString().toCharArray());
    }
    
    private boolean equals(byte[] a, byte[] a2) {
        if (a==a2)
            return true;
        if (a==null || a2==null)
            return false;
        
        int length = a.length;
        if (a2.length != length)
            return false;
        
        for (int i=0; i<length; i++)
            if (a[i] != a2[i])
                return false;
        
        return true;
    }
    
    /**
     * We have discovered all the games. We can now attempt to log on to the server with the
     * host, port, etc. that we have discovered.
     */
    public void discoveredGames() {
        try {
            clientMgr.connect();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * Notifies us that an exception occurred when either sending or receiving data
     */
    public void exceptionOccurred(Exception ex) {
        Displayable newScreen = changeState(EXCEPTION_OCCURRED);
        getDisplay().setCurrent(getGenericAlert("Exception occurred " + ex.getMessage()), newScreen);
    }
    
    /**
     * Send a message to the server
     */
    private void sendServerMessage() {
        String dataString = messageField.getString();
        ByteBuffer data = ByteBuffer.wrap(dataString.getBytes());
        clientMgr.sendServerMessage(data);
        messageField.setString("");
    }
}
