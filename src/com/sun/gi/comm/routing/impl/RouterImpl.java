package com.sun.gi.comm.routing.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.RouterListener;
import com.sun.gi.comm.routing.SGSChannel;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.server.SGSUser;
import com.sun.gi.framework.interconnect.TransportChannel;
import com.sun.gi.framework.interconnect.TransportChannelListener;
import com.sun.gi.framework.interconnect.TransportManager;
import com.sun.gi.framework.logging.SGSERRORCODES;
import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;
import com.sun.gi.utils.types.BYTEARRAY;

public class RouterImpl implements Router {

	private Map<UserID, SGSUser> userMap = new HashMap<UserID, SGSUser>();

	private TransportManager transportManager;

	private TransportChannel routerControlChannel;

	private Map<ChannelID, SGSChannel> channelMap = new HashMap<ChannelID, SGSChannel>();

	private Map<String, SGSChannel> channelNameMap = new HashMap<String, SGSChannel>();

	private Map<UserID, BYTEARRAY> currentKeys = new HashMap<UserID, BYTEARRAY>();

	private Map<UserID, BYTEARRAY> previousKeys = new HashMap<UserID, BYTEARRAY>();

	private ByteBuffer hdr = ByteBuffer.allocate(256);

	private RouterListener listener;
	private static final boolean TRACEKEYS=true;

	protected int keySecondsToLive;

	private enum OPCODE {
		UserJoined, UserLeft, UserJoinedChannel, UserLeftChannel, ReconnectKey
	};
	


	public RouterImpl(TransportManager cmgr) throws IOException {
		transportManager = cmgr;

		routerControlChannel = transportManager
				.openChannel("__SGS_ROUTER_CONTROL");
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
							currentKeys.put(uid,ba);
						}
						if (TRACEKEYS){
							System.out.println("Received key "+ba.toHex()+
									" for user "+uid.toString());
						}
					} catch (InstantiationException e) {
						e.printStackTrace();
					}
					break;
				}

			}

			public void channelClosed() {
				SGSERRORCODES.FatalErrors.RouterFailure
						.fail("Router control channel failed.");
			}
		});
		// initialize key TTL
		keySecondsToLive = 120; // 2 min lifetime by default
		String ttlStr = System.getProperty("sgs.router.keyTTL");
		if (ttlStr != null) {
			keySecondsToLive = Integer.parseInt(ttlStr);
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
					if (lastTicTime + (keySecondsToLive * 1000) >= System
							.currentTimeMillis()) {
						issueNewKeys();
						lastTicTime = System.currentTimeMillis();
					}
				}
			}
		}).start();
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
		synchronized(currentKeys){
			SGSUUID key = new StatisticalUUID();
			BYTEARRAY keybytes = new BYTEARRAY(key.toByteArray());
			currentKeys.put(user.getUserID(), keybytes);
			try {
				user.reconnectKeyReceived(keybytes.data(),
								keySecondsToLive);
			} catch (IOException e) {
				e.printStackTrace();
			}
			fireConnectKey(user.getUserID(), keybytes);
			if (TRACEKEYS){
				System.out.println("Generated key "+keybytes.toString()+
						" for user "+user.toString());
			}
		}
		
	}

	/**
	 * @param uid
	 * @param keybytes	 
	 */
	private void fireConnectKey(UserID uid, BYTEARRAY key) {
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

	private void reportUserLeft(byte[] uidbytes) {
		for (SGSUser user : userMap.values()) {
			try {
				user.userLeftSystem(uidbytes);
			} catch (IOException e) {
				System.out.println("Exception sending UserLeft to user id="
						+ user.getUserID());
				e.printStackTrace();
			}
		}

	}

	private void reportUserJoined(byte[] uidbytes) {
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
				System.out.println("Exception sending UserJOined to user id="
						+ user.getUserID());
				e.printStackTrace();
			}
		}

	}

	private void reportUserJoinedChannel(byte[] chanID, byte[] uidbytes) {
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
					user.userJoinedChannel(chanID, uidbytes);
				}
			} catch (IOException e) {
				System.out.println("Exception sending UserJOined to user id="
						+ user.getUserID());
				e.printStackTrace();
			}
		}

	}

	public void registerUser(SGSUser user) throws InstantiationException,
			IOException {
		userMap.put(user.getUserID(), user);
		fireUserJoined(user.getUserID());
		issueNewKey(user);
		reportUserJoined(user.getUserID().toByteArray());
		// send already connected users to new joiner
		for (UserID oldUserID : userMap.keySet()) {
			if (oldUserID != user.getUserID()) {
				user.userJoinedSystem(oldUserID.toByteArray());
			}
		}
	}

	private void fireUserJoined(UserID uid) {
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

	private void fireUserJoinedChannel(ChannelID cid, UserID uid) {
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

	public void deregisterUser(SGSUser user) {
		UserID id = user.getUserID();
		userMap.remove(id);
		for (SGSChannel chan : channelMap.values()) {
			chan.leave(user);
		}
		user.deregistered();
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

	private void fireUserLeft(UserID uid) {
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
	
	public SGSChannel getChannel(ChannelID id){
		return channelMap.get(id);
	}

	public SGSChannel openChannel(String channelName) {
		SGSChannel sgschan = channelNameMap.get(channelName);
		if (sgschan == null) {
			TransportChannel tchan;
			try {
				tchan = transportManager.openChannel(channelName);
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
			try {
				sgschan = new ChannelImpl(this, tchan);
				channelMap.put(sgschan.channelID(), sgschan);
				channelNameMap.put(channelName, sgschan);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return sgschan;
	}

	protected void closeChannel(ChannelImpl channel) {
		channelNameMap.remove(channel.getName());
		channelMap.remove(channel.channelID());
	}

	public boolean validateReconnectKey(UserID uid, byte[] key) {
		synchronized (currentKeys) {
			BYTEARRAY currentKey = currentKeys.get(uid);
			if (currentKey == null){
				if (TRACEKEYS){
					System.out.println("No key available for ID: "+uid.toString());
				}
				return false;
			}
			if (currentKey.equals(key)) {
				if (TRACEKEYS){
					System.out.println("Current Key validated for ID: "+uid.toString());
				}
				return true;
			}
			BYTEARRAY pastKey = previousKeys.get(uid);
			if ((pastKey!=null)&&(pastKey.equals(key))) {
				if (TRACEKEYS){
					System.out.println("Past Key validated for ID: "+uid.toString());
				}
				return true;
			}
			if (TRACEKEYS){
				System.out.println("Incorrect Key for ID: "+uid.toString());
			}
			return false;
		}
	}

	public void setRouterListener(RouterListener l) {
		listener = l;
	}

	public void serverMessage(boolean reliable, UserID userID,
			ByteBuffer databuff) {
		listener.serverMessage(userID, databuff, reliable);

	}

}
