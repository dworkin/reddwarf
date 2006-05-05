/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

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

public class ClientConnectionManagerImpl
        implements ClientConnectionManager, UserManagerClientListener {

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
    private Map<BYTEARRAY, ClientChannelImpl> channelMap =
        new HashMap<BYTEARRAY, ClientChannelImpl>();

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

    public boolean connect(String userManagerClassName, int connectAttempts,
            long msBetweenAttempts) throws ClientAlreadyConnectedException {
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
        connAttempts = connectAttempts;
        connWaitMS = msBetweenAttempts;
        connAttemptCounter = 0;
        reconnecting = false;
        return connect(umanager);
    }

    private boolean connect(UserManagerClient umgr) {
        connAttemptCounter++;
        exiting = false;
        DiscoveredGame game = discoverGame(gameName);
        DiscoveredUserManager choice = policy.choose(game,
                umgr.getClass().getName());
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
	
				try {
				    Thread.sleep(connWaitMS);
				} catch (InterruptedException e) {
				    // doesn't matter.
				}
	
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

    // package private methods intended for use by ClientChannelImpl

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
     * Called whenever an attempted join/leave fails due to the target
     * channel being locked.
     * 
     * @param channelName the name of the channel.
     * @param userID the ID of the user attemping to join/leave
     */
    public void channelLocked(String channelName, byte[] userID) {
        listener.channelLocked(channelName, userID);
    }
}
