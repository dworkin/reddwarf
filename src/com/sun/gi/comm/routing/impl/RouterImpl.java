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

package com.sun.gi.comm.routing.impl;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.Subject;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.RouterListener;
import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.filter.ChannelFilter;
import com.sun.gi.comm.users.server.SGSUser;
import com.sun.gi.framework.install.ChannelFilterRec;
import com.sun.gi.framework.interconnect.TransportChannel;
import com.sun.gi.framework.interconnect.TransportChannelListener;
import com.sun.gi.framework.interconnect.TransportManager;
import com.sun.gi.framework.logging.SGSERRORCODES;
import com.sun.gi.logic.GLO;
import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;
import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;
import com.sun.gi.utils.types.BYTEARRAY;

class CommPersistantData implements Serializable{
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    List<UserID> connectedUsers = new ArrayList<UserID>();
    Map<String,ChannelID> allocatedChannelIDs = new HashMap<String,ChannelID>();
    Map<ChannelID,String> allocatedChannelIDNames = new HashMap<ChannelID,String>();
}

public class RouterImpl implements Router {

    private static final String PERSISTANTCOMMDATANAME = "_SYS_CommPersistantData";

    static Logger log = Logger.getLogger("com.sun.gi.comm.routing");

    private Map<UserID, SGSUser> userMap = new ConcurrentHashMap<UserID, SGSUser>();

    private TransportManager transportManager;
    private TransportChannel routerControlChannel;

    private Map<ChannelID, SGSChannel> channelMap = new ConcurrentHashMap<ChannelID, SGSChannel>();
    private Map<String, SGSChannel> channelNameMap = new ConcurrentHashMap<String, SGSChannel>();
    Map<UserID, BYTEARRAY> currentKeys = new ConcurrentHashMap<UserID, BYTEARRAY>();
    private Map<UserID, BYTEARRAY> previousKeys = new ConcurrentHashMap<UserID, BYTEARRAY>();

    private ByteBuffer hdr = ByteBuffer.allocate(256);
    private List<RouterListener> listeners = new ArrayList<RouterListener>();
    private List<ChannelFilterRec> channelFilters;

    protected int keySecondsToLive;
    
    private boolean shutdown = false;
    private ObjectStore ostore;
    private CommPersistantData persistantData=null;
    private long persistantDataID = ObjectStore.INVALID_ID;
    

    private enum OPCODE {
        UserJoined,
        UserLeft,
        UserJoinedChannel,
        UserLeftChannel,
        ReconnectKey
    }

    public RouterImpl(TransportManager cmgr, ObjectStore objectStore) throws IOException {
        transportManager = cmgr;
        ostore = objectStore;
        channelFilters = new ArrayList<ChannelFilterRec>();
        
        routerControlChannel = transportManager.openChannel("__SGS_ROUTER_CONTROL");
        routerControlChannel.addListener(new TransportChannelListener() {
            public void dataArrived(ByteBuffer buff) {
                OPCODE opcode = OPCODE.values()[(int) buff.get()];
                switch (opcode) {
                    case UserJoined:
                        int idlen = buff.getInt();
                        byte[] idbytes = new byte[idlen];
                        buff.get(idbytes);
                        reportUserJoined(idbytes);
                        break;
                    case UserLeft:
                        idlen = buff.getInt();
                        idbytes = new byte[idlen];
                        buff.get(idbytes);
                        reportUserLeft(idbytes);
                        break;
                    case UserJoinedChannel:
                        // TODO
                        break;
                    case UserLeftChannel:
                        // TODO
                        break;
                    case ReconnectKey:
                        idlen = buff.getInt();
                        idbytes = new byte[idlen];
                        buff.get(idbytes);
                        try {
                            UserID uid = new UserID(idbytes);
                            idlen = buff.getInt();
                            idbytes = new byte[idlen];
                            buff.get(idbytes);
                            BYTEARRAY ba = new BYTEARRAY(idbytes);
                            synchronized (currentKeys) {
                                currentKeys.put(uid, ba);
                            }
                            if (log.isLoggable(Level.FINER)) {
                                log.finer("Received key " + ba.toHex()
                                        + " for user " + uid.toString());
                            }
                        } catch (InstantiationException e) {
                            e.printStackTrace();
                        }
                        break;
                }

            }

            public void channelClosed() {
                SGSERRORCODES.FatalErrors.RouterFailure.fail(
                        "Router control channel failed.");
            }
        });
        // initialize key TTL
        keySecondsToLive = 120; // 2 min lifetime by default
        String ttlStr = System.getProperty("sgs.router.keyTTL");
        if (ttlStr != null) {
            keySecondsToLive = Integer.parseInt(ttlStr);
        }
        synchronized(ostore){
            Transaction trans = ostore.newTransaction(this.getClass().getClassLoader());
            trans.start();
            persistantDataID = trans.lookup(PERSISTANTCOMMDATANAME);
            if (persistantDataID == ObjectStore.INVALID_ID){
                persistantData = new CommPersistantData();
                persistantDataID = trans.create(persistantData,PERSISTANTCOMMDATANAME);
                trans.commit();
            } else {
                trans.abort();
            }
        }
        // key issuance thread
        new Thread(new Runnable() {
            public void run() {
                long lastTicTime = System.currentTimeMillis();
                while (true) {
                    try {
                        Thread.sleep(keySecondsToLive * 1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if ((lastTicTime + (keySecondsToLive * 1000)) >= 
                            System.currentTimeMillis())
                    {
                        issueNewKeys();
                        lastTicTime = System.currentTimeMillis();
                    }
                }
            }
        }).start();
        
    }
    
    public void cleanUpOldUsers(){
        synchronized(ostore){
            Transaction trans = ostore.newTransaction(this.getClass().getClassLoader());
            trans.start();           
            try {
                persistantData = (CommPersistantData) trans.lock(persistantDataID);
                for(UserID oldID : persistantData.connectedUsers){
                    System.err.println("Foudn old ID: "+oldID);
                    fireUserLeft(oldID);
                }
                persistantData.connectedUsers.clear();
                trans.commit();
            } catch (DeadlockException e) { 
                trans.abort();
                e.printStackTrace();
            } catch (NonExistantObjectIDException e) {
                trans.abort();
                e.printStackTrace();
            }
                       
        }
    }
    
    public void setChannelFilters(List<ChannelFilterRec> filters) {
    	this.channelFilters = filters;
    }
    
    public void shutdown() {
    	shutdown = true;
    	synchronized (userMap) {
    		for (SGSUser user : userMap.values()) {
    			user.forceDisconnect(null);
    		}
    	}
    	
    	// close any channels. First copy the ChannelIDs to a temp
    	// list since closing the channel removes it from the
        // channelMap.
    	List<ChannelID> channelIDList = null;
    	synchronized (channelMap) {
    		channelIDList = new ArrayList<ChannelID>(channelMap.keySet());
    	}
    	for (ChannelID curChannelID : channelIDList) {
    		closeChannel(curChannelID);
    	}
    }

    /**
     * 
     */
    protected void issueNewKeys() {
        synchronized (currentKeys) {
            previousKeys.clear();
            previousKeys.putAll(currentKeys);
            currentKeys.clear();
            for (SGSUser user : userMap.values()) {
                issueNewKey(user);
            }
        }
    }

    /**
     * @param user
     */
    private void issueNewKey(SGSUser user) {
        synchronized (currentKeys) {
            SGSUUID key = new StatisticalUUID();
            BYTEARRAY keybytes = new BYTEARRAY(key.toByteArray());
            currentKeys.put(user.getUserID(), keybytes);
            try {
                user.reconnectKeyReceived(keybytes.data(), keySecondsToLive);
            } catch (IOException e) {
                e.printStackTrace();
            }
            xmitConnectKey(user.getUserID(), keybytes);
            if (log.isLoggable(Level.FINER)) {
                log.finer("Generated key " + keybytes.toString()
                        + " for user " + user.toString());
            }
        }
    }

    private void xmitConnectKey(UserID uid, BYTEARRAY key) {
        byte[] uidbytes = uid.toByteArray();
        byte[] keybytes = key.data();
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte) OPCODE.ReconnectKey.ordinal());
            hdr.putInt(uidbytes.length);
            hdr.put(uidbytes);
            hdr.putInt(keybytes.length);
            hdr.put(keybytes);
            try {
                routerControlChannel.sendData(hdr);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    void reportUserLeft(byte[] uidbytes) {
        for (SGSUser user : userMap.values()) {
            try {
                user.userLeftSystem(uidbytes);
            } catch (IOException e) {
                log.warning("Exception sending UserLeft to user id "
                        + user.getUserID());
                e.printStackTrace();
            }
        }
    }

    void reportUserJoined(byte[] uidbytes) {
        UserID sentID = null;
        try {
            sentID = new UserID(uidbytes);
        } catch (InstantiationException e1) {
            e1.printStackTrace();
            return;
        }
        for (SGSUser user : userMap.values()) {
            try {
                if (!user.getUserID().equals(sentID)) {
                    user.userJoinedSystem(uidbytes);
                }
            } catch (IOException e) {
                log.warning("Exception sending UserJoined to user id "
                        + user.getUserID());
                e.printStackTrace();
            }
        }
    }

    /*
     * private void reportUserJoinedChannel(byte[] chanID, byte[]
     * uidbytes) { UserID sentID = null; try { sentID = new
     * UserID(uidbytes); } catch (InstantiationException e1) {
     * e1.printStackTrace(); return; } for (SGSUser user :
     * userMap.values()) { try { if (!user.getUserID().equals(sentID)) {
     * user.userJoinedChannel(chanID, uidbytes); } } catch (IOException
     * e) { System.out.println("Exception sending UserJOined to user
     * id=" + user.getUserID()); e.printStackTrace(); } } }
     */

    public void registerUser(SGSUser user, Subject subject)
            throws InstantiationException, IOException {
    	
    	if (shutdown) {
    		return;
    	}
        userMap.put(user.getUserID(), user);
        synchronized(ostore){
            Transaction trans = ostore.newTransaction(this.getClass().getClassLoader());
            trans.start();
            try {
                persistantData = (CommPersistantData) trans.lock(persistantDataID);
                persistantData.connectedUsers.add(user.getUserID());
                trans.commit();
            } catch (DeadlockException e) {
                trans.abort();
                e.printStackTrace();
            } catch (NonExistantObjectIDException e) {
                trans.abort();
                e.printStackTrace();
            }
            
        }
        xmitUserJoined(user.getUserID());
        issueNewKey(user);
        reportUserJoined(user.getUserID().toByteArray());
        fireUserJoined(user.getUserID(), subject);
        // send already-connected users to new joiner
        for (UserID oldUserID : userMap.keySet()) {
            if (oldUserID != user.getUserID()) {
                user.userJoinedSystem(oldUserID.toByteArray());
            }
        }
    }

    private void xmitUserJoined(UserID uid) {
        byte[] uidbytes = uid.toByteArray();
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte) OPCODE.UserJoined.ordinal());

            hdr.putInt(uidbytes.length);
            hdr.put(uidbytes);
            try {
                routerControlChannel.sendData(hdr);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void xmitUserJoinedChannel(ChannelID cid, UserID uid) {
        byte[] uidbytes = uid.toByteArray();
        byte[] cidbytes = cid.toByteArray();
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte) OPCODE.UserJoinedChannel.ordinal());
            hdr.putInt(uidbytes.length);
            hdr.put(uidbytes);
            hdr.putInt(cidbytes.length);
            hdr.put(cidbytes);
            try {
                routerControlChannel.sendData(hdr);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void xmitUserLeftChannel(ChannelID cid, UserID uid) {
        byte[] uidbytes = uid.toByteArray();
        byte[] cidbytes = cid.toByteArray();
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte) OPCODE.UserLeftChannel.ordinal());
            hdr.putInt(uidbytes.length);
            hdr.put(uidbytes);
            hdr.putInt(cidbytes.length);
            hdr.put(cidbytes);
            try {
                routerControlChannel.sendData(hdr);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void deregisterUser(SGSUser user) {
        UserID id = user.getUserID();
        userMap.remove(id);
        synchronized(ostore){
            Transaction trans = ostore.newTransaction(this.getClass().getClassLoader());
            trans.start();
            try {
                persistantData = (CommPersistantData) trans.lock(persistantDataID);
                persistantData.connectedUsers.remove(user.getUserID());
                trans.commit();
            } catch (DeadlockException e) {
                trans.abort();
                e.printStackTrace();
            } catch (NonExistantObjectIDException e) {
                trans.abort();
                e.printStackTrace();
            }
            
        }
        for (SGSChannel chan : channelMap.values()) {
            chan.leave(user);
        }
        
        user.deregistered();
        xmitUserLeft(id);
        fireUserLeft(id);
        for (SGSUser localUser : userMap.values()) {
            if (localUser != user) {
                try {
                    localUser.userLeftSystem(id.toByteArray());
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    private void xmitUserLeft(UserID uid) {
        byte[] uidbytes = uid.toByteArray();
        synchronized (hdr) {
            hdr.clear();
            hdr.put((byte) OPCODE.UserLeft.ordinal());
            hdr.putInt(uidbytes.length);
            hdr.put(uidbytes);
            try {
                routerControlChannel.sendData(hdr);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public SGSChannel getChannel(ChannelID cid) {
        synchronized(channelMap){
            SGSChannel chan = channelMap.get(cid);
            if (chan==null){ // check if known in persistant data
                synchronized(ostore){
                    Transaction trans = ostore.newTransaction(this.getClass().getClassLoader());
                    trans.start();
                    ChannelID id;
                    try {
                        persistantData = (CommPersistantData) trans.peek(persistantDataID);
                        String chanName = persistantData.allocatedChannelIDNames.get(cid);
                        if (chanName != null) { // already mapped
                            TransportChannel tchan = transportManager.openChannel(chanName);
                            chan = new ChannelImpl(this, tchan, cid, createFilters());
                            channelMap.put(cid,chan);
                            channelNameMap.put(chanName,chan);
                        }   
                        return chan;
                    } catch (DeadlockException e) {
                        e.printStackTrace();
                    } catch (NonExistantObjectIDException e) {
                        e.printStackTrace();
                    } catch (IOException e) {                    
                        e.printStackTrace();
                    } finally {
                        trans.abort();
                    }              
                }
            }
            return chan;
        }
    }

    public SGSChannel openChannel(String channelName) {
        synchronized(channelNameMap){
            SGSChannel sgschan = channelNameMap.get(channelName);
            // TODO: currently it is possible for an app to open another
            // channel
            // while the router is being shutdown. We should close this
            // hole during the next pass.
            if (sgschan == null) {
                TransportChannel tchan;
                try {
                    tchan = transportManager.openChannel(channelName);
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
                try {
                    for (ChannelFilterRec curFilter : channelFilters) {
                        curFilter.createChannelFilter();
                    }
                    ChannelID channelID = resolveChannelID(channelName);                
                    sgschan = new ChannelImpl(this, tchan, channelID, createFilters());
                    channelMap.put(sgschan.channelID(), sgschan);
                    channelNameMap.put(channelName, sgschan);                
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return sgschan;
        }
    }
    
    /**
     * @param channelName
     * @param channelID
     */
    private ChannelID resolveChannelID(String channelName) {
        synchronized(ostore){
            Transaction trans = ostore.newTransaction(this.getClass().getClassLoader());
            trans.start();
            ChannelID id;
            try {
                persistantData = (CommPersistantData) trans.peek(persistantDataID);
                id = persistantData.allocatedChannelIDs.get(channelName);
                if (id != null){
                    trans.abort();
                    return id;
                }
                // now lock and check again
                persistantData = (CommPersistantData) trans.lock(persistantDataID);
                persistantData.allocatedChannelIDs.get(channelName);
                id = persistantData.allocatedChannelIDs.get(channelName);
                if (id != null){
                    trans.abort();
                    return id;
                }
                // need to create and register a new one
                id = new ChannelID();
                persistantData.allocatedChannelIDs.put(channelName, id);
                persistantData.allocatedChannelIDNames.put(id,channelName);
                trans.commit();
                return id;
            } catch (DeadlockException e) {
                trans.abort();
                e.printStackTrace();
            } catch (NonExistantObjectIDException e) {
                trans.abort();
                e.printStackTrace();
            }
            return null;
            
        }
        
    }

    

    /**
     * Creates a List of ChannelFilters based on the List of channel
     * filter descriptors.
     * 
     * @return a List of ChannelFilters
     */
    private List<ChannelFilter> createFilters() {
    	if (channelFilters == null) {
    		return null;
    	}
    	List<ChannelFilter> filters = new ArrayList<ChannelFilter>();
    	for (ChannelFilterRec curRec : channelFilters) {
    		filters.add(curRec.createChannelFilter());
    	}
    	return filters;
    }

    /**
     * Removes the given Channel from the various maps.
     * 
     * @param channel the channel to remove.
     */
    protected void removeChannel(ChannelImpl channel) {
        synchronized (channelNameMap) {
            channelNameMap.remove(channel.getName());
        }
        synchronized (channelMap) {
            channelMap.remove(channel.channelID());
        }
    }

    public boolean validateReconnectKey(UserID uid, byte[] key) {
        synchronized (currentKeys) {
            BYTEARRAY currentKey = currentKeys.get(uid);
            if (currentKey == null) {
                if (log.isLoggable(Level.FINER)) {
                    log.finer("No key available for ID: "
                            + uid.toString());
                }
                return false;
            }
            if (currentKey.equals(key)) {
                if (log.isLoggable(Level.FINER)) {
                    log.finer("Current Key validated for ID: "
                            + uid.toString());
                }
                return true;
            }
            BYTEARRAY pastKey = previousKeys.get(uid);
            if ((pastKey != null) && (pastKey.equals(key))) {
                if (log.isLoggable(Level.FINER)) {
                    log.finer("Past Key validated for ID: "
                            + uid.toString());
                }
                return true;
            }
            if (log.isLoggable(Level.FINER)) {
                log.finer("Incorrect Key for ID: " + uid.toString());
            }
            return false;
        }
    }

    public void addRouterListener(RouterListener l) {
        listeners.add(l);
    }

    public void serverMessage(boolean reliable, UserID uid,
            ByteBuffer databuff) {
        synchronized (listeners) {
            for (RouterListener listener : listeners) {
                listener.serverMessage(uid, databuff, reliable);
            }
        }
    }

    public void fireUserJoined(UserID uid, Subject subject) {
        synchronized (listeners) {
            for (RouterListener listener : listeners) {
                listener.userJoined(uid, subject);
            }
        }
    }

    public void fireUserLeft(UserID uid) {
        synchronized (listeners) {
            for (RouterListener listener : listeners) {
                listener.userLeft(uid);
            }
        }
    }

    public void fireUserJoinedChannel(UserID uid, ChannelID cid) {
        synchronized (listeners) {
            for (RouterListener listener : listeners) {
                listener.userJoinedChannel(uid, cid);
            }
        }
    }

    public void fireUserLeftChannel(UserID uid, ChannelID cid) {
        synchronized (listeners) {
            for (RouterListener listener : listeners) {
                listener.userLeftChannel(uid, cid);
            }
        }
    }

    public void fireChannelDataPacket(ChannelID cid, UserID from,
            ByteBuffer buff) {
        synchronized (listeners) {
            for (RouterListener listener : listeners) {
                listener.channelDataPacket(cid, from, buff.duplicate());
            }
        }
    }

    public void userJoinedChan(ChannelImpl chan, SGSUser user) {
        xmitUserJoinedChannel(chan.channelID(), user.getUserID());
        fireUserJoinedChannel(user.getUserID(), chan.channelID());

    }

    public void userLeftChan(ChannelImpl chan, SGSUser user) {
        xmitUserLeftChannel(chan.channelID(), user.getUserID());
        fireUserLeftChannel(user.getUserID(), chan.channelID());

    }

    public void channelDataPacket(ChannelImpl chan, UserID from,
            ByteBuffer data, boolean reliable) {
        fireChannelDataPacket(chan.channelID(), from, data);
    }

    /**
     * Joins the specified user to the Channel referenced by the given
     * ChannelID.
     * 
     * @param uid the user
     * @param cid the ChannelID
     */
    public void join(UserID uid, ChannelID cid) {
        SGSChannel channel = getChannel(cid);
        if (channel == null) { // channel not opened or otherwise
            // mapped
            return;
        }
        SGSUser sgsUser = userMap.get(uid);
        if (sgsUser != null) {
            channel.join(sgsUser);
        }
    }

    /**
     * Removes the specified user from the Channel referenced by the
     * given ChannelID.
     * 
     * @param uid the user
     * @param cid the ChannelID
     */
    public void leave(UserID uid, ChannelID cid) {
        SGSChannel channel = getChannel(cid);
        if (channel == null) { // channel not opened or otherwise
            // mapped
            return;
        }
        SGSUser sgsUser = userMap.get(uid);
        if (sgsUser != null) {
            channel.leave(sgsUser);
        }
    }

    /**
     * Locks the given channel based on shouldLock. Users cannot
     * join/leave locked channels except by way of the Router.
     * 
     * @param cid the channel ID
     * @param shouldLock if true, will lock the channel, otherwise
     * unlock it.
     */
    public void lock(ChannelID cid, boolean shouldLock) {
        SGSChannel channel = getChannel(cid);
        if (channel == null) { // no channel in map
            return;
        }
        channel.setLocked(shouldLock);
    }

    /**
     * Closes the local view of the channel mapped to ChannelID. Any
     * remaining users will be notified as the channel is closing.
     * 
     * @param cid the ID of the channel to close.
     */
    public void closeChannel(ChannelID cid) {
        SGSChannel channel = getChannel(cid);
        if (channel == null) { // no channel in map
            return;
        }
        channel.close();
    }
}
