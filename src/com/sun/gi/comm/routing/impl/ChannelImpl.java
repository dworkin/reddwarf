package com.sun.gi.comm.routing.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.server.SGSUser;
import com.sun.gi.framework.interconnect.TransportChannel;
import com.sun.gi.framework.interconnect.TransportChannelListener;
import com.sun.gi.utils.types.BYTEARRAY;

public class ChannelImpl implements SGSChannel, TransportChannelListener {
	private TransportChannel transportChannel;
	private ChannelID localID; // NOTE: Local to router
	private byte[] localIDbytes;
	private ByteBuffer hdr = ByteBuffer.allocate(1024);
	private ByteBuffer[] buffs = new ByteBuffer[2];
	private Map<UserID,SGSUser> localUsers = new HashMap<UserID,SGSUser>();
	private RouterImpl router;
	private boolean locked;
	
	private enum OPCODE {
		UserJoinedChan, UserLeftChan, 
		UnicastMessage, MulticastMessage, BroadcastMessage
	}
	
	// only instanceable by RouterImpl
	ChannelImpl(RouterImpl r, TransportChannel chan) throws IOException {
		transportChannel = chan;
		transportChannel.addListener(this);
		localID = new ChannelID();
		localIDbytes = localID.toByteArray();
		buffs[0]=hdr;		
		router = r;
		locked = false;
	}

	public void unicastData(UserID from, UserID to, ByteBuffer message, boolean reliable) {
		synchronized(hdr){
			hdr.clear();
			hdr.put((byte)OPCODE.UnicastMessage.ordinal());
			hdr.put((byte)(reliable?0:1));
			byte[] frombytes = from.toByteArray();
			hdr.putInt(frombytes.length);
			hdr.put(frombytes);
			byte[] tobytes = to.toByteArray();
			hdr.putInt(tobytes.length);
			hdr.put(tobytes);
			buffs[1] = message;
			transportChannel.sendData(buffs);
		}	
		sendToLocalUser(from.toByteArray(),to.toByteArray(),message,reliable);	
		router.channelDataPacket(this,from,message,reliable);
	}
	
	public void multicastData(UserID from, UserID[] tolist, ByteBuffer message, boolean reliable) {
		multicastData(from,tolist,message,reliable,true);
	}
	
	public void multicastData(UserID from, UserID[] tolist, ByteBuffer message, boolean reliable,
			boolean sendToListeners) {
		synchronized(hdr){
			hdr.clear();
			hdr.put((byte)OPCODE.MulticastMessage.ordinal());
			hdr.put((byte)(reliable?0:1));
			byte[] frombytes = from.toByteArray();
			hdr.putInt(frombytes.length);
			hdr.put(frombytes);
			hdr.putInt(tolist.length);
			for(int i=0;i<tolist.length;i++){
				byte[] tobytes = tolist[i].toByteArray();
				hdr.putInt(tobytes.length);
				hdr.put(tobytes);
			}
			buffs[1] = message;
			transportChannel.sendData(buffs);
		}
		byte[][] toArray = new byte[tolist.length][];
		for(int i=0;i<toArray.length;i++){
			toArray[i] = tolist[i].toByteArray();
		}
		multicastToLocalUsers(from.toByteArray(),toArray,message,reliable);
		if (sendToListeners){
			router.channelDataPacket(this,from,message,reliable);
		}
	}

	public void broadcastData(UserID from, ByteBuffer message, boolean reliable) {
		synchronized(hdr){
			hdr.clear();
			hdr.put((byte)OPCODE.BroadcastMessage.ordinal());
			hdr.put((byte)(reliable?0:1));
			byte[] frombytes = from.toByteArray();
			hdr.putInt(frombytes.length);
			hdr.put(frombytes);			
			buffs[1] = message;
			transportChannel.sendData(buffs);
		}
		broadcastToLocalUsers(from.toByteArray(),message,reliable);
		router.channelDataPacket(this,from,message,reliable);
	}

	public void join(SGSUser user) {
		synchronized(hdr){
			hdr.clear();
			hdr.put((byte)OPCODE.UserJoinedChan.ordinal());
			byte[] userbytes = user.getUserID().toByteArray();
			hdr.putInt(userbytes.length);
			hdr.put(userbytes);			
			buffs[1] = null;
			try {
				transportChannel.sendData(hdr);
			} catch (IOException e) {				
				e.printStackTrace();
			}
		}		
		try {
			user.joinedChan(this);
			for(SGSUser existingUser : localUsers.values()){
				user.userJoinedChannel(localIDbytes, existingUser.getUserID().toByteArray());
			}
			sendJoinToLocalUsers(user.getUserID().toByteArray());
			localUsers.put(user.getUserID(),user);	
			router.userJoinedChan(this,user);
		} catch (IOException e) {			
			e.printStackTrace();
		}
		
	}
	
	public void leave(SGSUser user) {
        // if the user isn't on this channel, then ignore them
		if (localUsers.remove(user.getUserID()) == null) {
            return;
        }

		byte[] userbytes = user.getUserID().toByteArray();
		synchronized(hdr){
			hdr.clear();
            hdr.put((byte)OPCODE.UserLeftChan.ordinal());
			hdr.putInt(userbytes.length);
			hdr.put(userbytes);			
			buffs[1] = null;
			try {
				transportChannel.sendData(hdr);
			} catch (IOException e) {				
				e.printStackTrace();
			}
		}
		try {			
			user.leftChan(this);
		} catch (IOException e) {		
			e.printStackTrace();
		}
		sendLeaveToLocalUsers(userbytes);
		router.userLeftChan(this,user);
	}

	public ChannelID channelID() {
		return localID;
	}

	//	 Transport Channel Listener
	
	public synchronized void dataArrived(ByteBuffer buff) {
		int opcode = (int)buff.get();
		OPCODE code = OPCODE.values()[opcode];
		switch(code) {
			case BroadcastMessage:
				boolean reliable = (buff.get()==1);
				int frombytelen = buff.getInt();				
				byte[] frombytes = new byte[frombytelen];
				buff.get(frombytes);
				broadcastToLocalUsers(frombytes,buff,reliable);	
				break;
			case MulticastMessage:
				reliable = (buff.get()==1);
				frombytelen = buff.getInt();				
				frombytes = new byte[frombytelen];
				buff.get(frombytes);
				int tolistlen = buff.getInt();
				byte[][] tolist = new byte[tolistlen][];
				for(int i=0;i<tolistlen;i++){					
					int tobytelen = hdr.getInt();
					tolist[i] = new byte[tobytelen];
					buff.get(tolist[i]);
				}	
				multicastToLocalUsers(frombytes,tolist,buff,reliable);
				break;
			case UnicastMessage:
				reliable = (buff.get()==1);
				frombytelen = buff.getInt();				
				frombytes = new byte[frombytelen];
				buff.get(frombytes);
				int tobytelen = buff.getInt();
				byte[] tobytes = new byte[tobytelen];
				buff.get(tobytes);
				sendToLocalUser(frombytes,tobytes,buff,reliable);
				break;
			case UserJoinedChan:
				frombytelen = buff.getInt();				
				frombytes = new byte[frombytelen];
				buff.get(frombytes);
				sendJoinToLocalUsers(frombytes);
				break;
			case UserLeftChan:
				frombytelen = buff.getInt();				
				frombytes = new byte[frombytelen];
				buff.get(frombytes);
				sendLeaveToLocalUsers(frombytes);
				break;
		}
	}


	private void sendLeaveToLocalUsers(byte[] frombytes) {		
		for(SGSUser user : localUsers.values()){
			try {
				user.userLeftChannel(localIDbytes,frombytes);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
	}

	private void sendJoinToLocalUsers(byte[] frombytes) {
		for(SGSUser user : localUsers.values()){
			try {
				user.userJoinedChannel(localIDbytes,frombytes);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void sendToLocalUser(byte[] frombytes, byte[] tobytes, ByteBuffer buff, boolean reliable) {
		try {
			SGSUser user = localUsers.get(new UserID(tobytes));
			if (user != null){ // our user
				user.msgReceived(localIDbytes,frombytes,reliable,buff.duplicate());
			}
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
	}

	private void multicastToLocalUsers(byte[] frombytes, byte[][] tolist, ByteBuffer buff, boolean reliable) {
		for(byte[] tobytes : tolist){
			sendToLocalUser(frombytes,tobytes,buff,reliable);
		}
		
	}

	private void broadcastToLocalUsers(byte[] frombytes, ByteBuffer buff, boolean reliable) {
		UserID fromID;
		try {
			fromID = new UserID(frombytes);
			for(SGSUser user : localUsers.values()){
				if (!user.getUserID().equals(fromID)){// dont echo to sender
					sendToLocalUser(frombytes,user.getUserID().toByteArray(),buff,reliable);
				}
			}
		} catch (InstantiationException e) {
			
			e.printStackTrace();
		}
				
	}

	public void channelClosed() {
		for(SGSUser user : localUsers.values()){
			try {
				user.leftChan(this);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}	
		router.removeChannel(this);
	}

	// only for use by RouterImpl
	public String getName() {
		return transportChannel.getName();
	}	

	/**
	 * Returns this channel's lock status.  Users cannot join/leave locked channels
	 * except by way of the GLE.
	 * 
	 * @return		true if this channel is locked.
	 */
	public boolean isLocked() {
		return locked;
	}
	
	
	/**
	 * Sets this channel's lock status.  Users cannot join/leave locked channels
	 * except by way of the GLE.
	 * 
	 * @param lock		if true, will lock the channel.
	 */
	public void setLocked(boolean lock) {
		this.locked = lock;
	}
	
	/**
	 * Signals to the underlying transport channel that this object is
	 * finished using it.  Clients should call this when they are finished
	 * using the channel.  Users will be notified that the channel is closing.
	 */
	public void close() {
		transportChannel.removeListener(this);
		channelClosed();
	}
	
}
