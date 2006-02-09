package com.sun.gi.comm.users.client.impl;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.security.auth.callback.Callback;

import com.sun.gi.comm.discovery.DiscoveredGame;
import com.sun.gi.comm.discovery.DiscoveredUserManager;
import com.sun.gi.comm.discovery.Discoverer;
import com.sun.gi.comm.users.client.ClientAlreadyConnectedException;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.UserManagerClient;
import com.sun.gi.comm.users.client.UserManagerClientListener;
import com.sun.gi.comm.users.client.UserManagerPolicy;
import com.sun.gi.utils.types.BYTEARRAY;

public class ClientConnectionManagerImpl implements ClientConnectionManager,
        UserManagerClientListener {

    private byte[] server_id;

    private Discoverer discoverer;

    private UserManagerPolicy policy;

    private UserManagerClient umanager;

    private Class umanagerClass;

    private String gameName;

    private byte[] reconnectionKey = null;

    private ClientConnectionManagerListener listener;

    private byte[] myID;

    private boolean reconnecting = false;

    private boolean connected = false;

    private Map<BYTEARRAY, ClientChannelImpl> channelMap = new HashMap<BYTEARRAY, ClientChannelImpl>();

    private long keyTimeout = 0;

    // used to do multiple conn attempts
    private long connAttempts;

    private long connAttemptCounter;

    private long connWaitMS;

    private boolean exiting;

    public ClientConnectionManagerImpl(String gameName, Discoverer disco) {
        this(gameName, disco, new DefaultUserManagerPolicy());
    }

    public ClientConnectionManagerImpl(String gameName, Discoverer disco,
            UserManagerPolicy policy) {
        discoverer = disco;
        this.policy = policy;
        this.gameName = gameName;
    }

    public void setListener(ClientConnectionManagerListener l) {
        listener = l;
    }

    public String[] getUserManagerClassNames() {
        DiscoveredGame game = discoverGame(gameName);
        if (game == null) {
            return null;
        }

        DiscoveredUserManager[] umgrs = game.getUserManagers();
        if (umgrs == null) {
            return null;
        }

        Set<String> names = new HashSet<String>();
        for (DiscoveredUserManager umgr : umgrs) {
            names.add(umgr.getClientClass());
        }

        String[] outnames = new String[names.size()];
        return names.toArray(outnames);
    }

    private DiscoveredGame discoverGame(String name) {
        DiscoveredGame[] games = discoverer.games();
        for (DiscoveredGame game : games) {
            if (game.getName().equals(name)) {
                return game;
            }
        }
        System.err.println("Discovery Error: No games discovered!");
        return null;
    }

    public boolean connect(String userManagerClassName)
            throws ClientAlreadyConnectedException {
        int attempts = 10;
        String attstr = System.getProperty("sgs.clientconnmgr.connattempts");
        if (attstr != null) {
            attempts = Integer.parseInt(attstr);
        }
        long sleepTime = 100;
        String sleepstr = System.getProperty("sgs.clientconnmgr.connwait");
        if (sleepstr != null) {
            sleepTime = Long.parseLong(sleepstr);
        }
        return connect(userManagerClassName, attempts, sleepTime);
    }

    public boolean connect(String userManagerClassName, int attempts,
            long sleepTime) throws ClientAlreadyConnectedException {
        if (connected) {
            throw new ClientAlreadyConnectedException();
        }
        try {
            umanagerClass = Class.forName(userManagerClassName);
            umanager = (UserManagerClient) umanagerClass.newInstance();
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        }
        connAttempts = attempts;
        connWaitMS = sleepTime;
        connAttemptCounter = 0;
        reconnecting = false;
        return connect(umanager);
    }

    private boolean connect(UserManagerClient umgr) {
        connAttemptCounter++;
        exiting = false;
        DiscoveredGame game = discoverGame(gameName);
        DiscoveredUserManager choice =
            policy.choose(game, umgr.getClass().getName());
        return umgr.connect(choice.getParameters(), this);
    }

    public void disconnect() {
        exiting = true;
        umanager.logout();
    }

    public void sendValidationResponse(Callback[] cbs) {
        umanager.validationDataResponse(cbs);
    }

    public void openChannel(String channelName) {
        umanager.joinChannel(channelName);
    }

    public void sendToServer(ByteBuffer buff, boolean reliable) {
        umanager.sendToServer(buff, reliable);
    }

    // UserClientManagerListener methods

    public void dataReceived(byte[] chanID, byte[] from, ByteBuffer data,
            boolean reliable) {
        ClientChannelImpl chan = channelMap.get(new BYTEARRAY(chanID));
        chan.dataReceived(from, data, reliable);
    }

    public void disconnected() {
        if (connected == false) { // not yet connected
            if (connAttemptCounter < connAttempts) { // try again
                try {
                    Thread.sleep(connWaitMS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                connect(umanager);
            } else {
                listener.disconnected();
            }
        } else { // lost connection
            if ((!exiting) && (keyTimeout > System.currentTimeMillis())) {
                // valid reconn key
                listener.failOverInProgress();
                reconnecting = true;
                connect(umanager);
            } else { // we cant fail over
                connected = false;
                listener.disconnected();
            }
        }
    }

    public void connected() {
        connected = true;
        if (!reconnecting || (keyTimeout < System.currentTimeMillis())) {
            umanager.login();
        } else {
            umanager.reconnectLogin(myID, reconnectionKey);
        }
    }

    public void validationDataRequest(Callback[] cbs) {
        listener.validationRequest(cbs);
    }

    public void loginAccepted(byte[] userID) {
        myID = new byte[userID.length];
        System.arraycopy(userID, 0, myID, 0, myID.length);
        listener.connected(myID);
    }

    public void loginRejected(String message) {
        listener.connectionRefused(message);
    }

    public void userAdded(byte[] userID) {
        listener.userJoined(userID);
    }

    public void userDropped(byte[] userID) {
        listener.userLeft(userID);
    }

    public void newConnectionKeyIssued(byte[] key, long ttl) {
        reconnectionKey = new byte[key.length];
        System.arraycopy(key, 0, reconnectionKey, 0, key.length);
        keyTimeout = System.currentTimeMillis() + (ttl * 1000);
    }

    public void joinedChannel(String name, byte[] channelID) {
        ClientChannelImpl chan = new ClientChannelImpl(this, name, channelID);
        channelMap.put(new BYTEARRAY(channelID), chan);
        listener.joinedChannel(chan);
    }

    public void leftChannel(byte[] channelID) {
        ClientChannelImpl chan = channelMap.remove(new BYTEARRAY(channelID));
        chan.channelClosed();
    }

    public void userJoinedChannel(byte[] channelID, byte[] userID) {
        ClientChannelImpl chan = channelMap.get(new BYTEARRAY(channelID));
        chan.userJoined(userID);
    }

    public void userLeftChannel(byte[] channelID, byte[] userID) {
        ClientChannelImpl chan = channelMap.get(new BYTEARRAY(channelID));
        chan.userLeft(userID);
    }

    public void recvdData(byte[] chanID, byte[] from, ByteBuffer data,
            boolean reliable) {
        ClientChannelImpl chan = channelMap.get(new BYTEARRAY(chanID));
        chan.dataReceived(from, data, reliable);
    }

    /*
     * The below are all package private and intended just for use by
     * ClientChannelImpl
     */

    void sendUnicastData(byte[] chanID, byte[] to, ByteBuffer data,
            boolean reliable) {
        umanager.sendUnicastMsg(chanID, to, data, reliable);
    }

    void sendMulticastData(byte[] chanID, byte[][] to, ByteBuffer data,
            boolean reliable) {
        umanager.sendMulticastMsg(chanID, to, data, reliable);
    }

    void sendBroadcastData(byte[] chanID, ByteBuffer data, boolean reliable) {
        umanager.sendBroadcastMsg(chanID, data, reliable);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sun.gi.comm.users.client.UserManagerClientListener#recvServerID(byte[])
     */
    public void recvServerID(byte[] user) {
        server_id = user;
    }

    public boolean isServerID(byte[] userid) {
        return Arrays.equals(userid, server_id);
    }

    public void closeChannel(byte[] channelID) {
        umanager.leaveChannel(channelID);
    }

    /**
     * This method is called whenever an attempted join/leave fails due to the
     * target channel being locked.
     * 
     * @param channelName
     *            the name of the channel.
     * @param userID
     *            the ID of the user attemping to join/leave
     */
    public void channelLocked(String channelName, byte[] userID) {
        listener.channelLocked(channelName, userID);
    }
}
