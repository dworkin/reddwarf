package com.sun.gi.comm.routing.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import javax.security.auth.Subject;

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

	private Map<UserID, SGSUser> userMap = new ConcurrentHashMap<UserID, SGSUser>();

	private TransportManager transportManager;

	private TransportChannel routerControlChannel;

	private Map<ChannelID, SGSChannel> channelMap = new ConcurrentHashMap<ChannelID, SGSChannel>();

	private Map<String, SGSChannel> channelNameMap = new ConcurrentHashMap<String, SGSChannel>();

	Map<UserID, BYTEARRAY> currentKeys = new ConcurrentHashMap<UserID, BYTEARRAY>();

	private Map<UserID, BYTEARRAY> previousKeys = new ConcurrentHashMap<UserID, BYTEARRAY>();

	private ByteBuffer hdr = ByteBuffer.allocate(256);

	private List<RouterListener> listeners = new ArrayList<RouterListener>();

	private static final boolean TRACEKEYS = true;

	protected int keySecondsToLive;

	private enum OPCODE {
		UserJoined, UserLeft, UserJoinedChannel, UserLeftChannel, ReconnectKey
	}

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
							currentKeys.put(uid, ba);
						}
						if (TRACEKEYS) {
							System.out.println("Received key " + ba.toHex()
									+ " for user " + uid.toString());
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
			if (TRACEKEYS) {
				System.out.println("Generated key " + keybytes.toString()
						+ " for user " + user.toString());
			}
		}

	}

	/**
	 * @param uid
	 * @param keybytes
	 */
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
				System.out.println("Exception sending UserLeft to user id="
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
				System.out.println("Exception sending UserJOined to user id="
						+ user.getUserID());
				e.printStackTrace();
			}
		}

	}

	/*
	 * private void reportUserJoinedChannel(byte[] chanID, byte[] uidbytes) {
	 * UserID sentID = null; try { sentID = new UserID(uidbytes); } catch
	 * (InstantiationException e1) { e1.printStackTrace(); return; } for
	 * (SGSUser user : userMap.values()) { try { if
	 * (!user.getUserID().equals(sentID)) { user.userJoinedChannel(chanID,
	 * uidbytes); } } catch (IOException e) { System.out.println("Exception
	 * sending UserJOined to user id=" + user.getUserID()); e.printStackTrace(); } }
	 *  }
	 */

	public void registerUser(SGSUser user, Subject subject)
			throws InstantiationException, IOException {
		userMap.put(user.getUserID(), user);
		xmitUserJoined(user.getUserID());
		issueNewKey(user);
		reportUserJoined(user.getUserID().toByteArray());
		fireUserJoined(user.getUserID(), subject);
		// send already connected users to new joiner
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

	public SGSChannel getChannel(ChannelID id) {
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

	/**
	 * Removes the given Channel from the various maps.
	 * 
	 * @param channel
	 *            the channel to remove.
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
				if (TRACEKEYS) {
					System.out.println("No key available for ID: "
							+ uid.toString());
				}
				return false;
			}
			if (currentKey.equals(key)) {
				if (TRACEKEYS) {
					System.out.println("Current Key validated for ID: "
							+ uid.toString());
				}
				return true;
			}
			BYTEARRAY pastKey = previousKeys.get(uid);
			if ((pastKey != null) && (pastKey.equals(key))) {
				if (TRACEKEYS) {
					System.out.println("Past Key validated for ID: "
							+ uid.toString());
				}
				return true;
			}
			if (TRACEKEYS) {
				System.out.println("Incorrect Key for ID: " + uid.toString());
			}
			return false;
		}
	}

	public void addRouterListener(RouterListener l) {
		listeners.add(l);
	}

	public void serverMessage(boolean reliable, UserID userID,
			ByteBuffer databuff) {
		synchronized (listeners) {
			for (RouterListener listener : listeners) {
				listener.serverMessage(userID, databuff, reliable);
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

	/**
	 * @param impl
	 * @param user
	 */
	public void userJoinedChan(ChannelImpl chan, SGSUser user) {
		xmitUserJoinedChannel(chan.channelID(), user.getUserID());
		fireUserJoinedChannel(user.getUserID(), chan.channelID());

	}

	/**
	 * @param impl
	 * @param user
	 */
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
	 * @param user
	 *            the user
	 * @param id
	 *            the ChannelID
	 */
	public void join(UserID user, ChannelID id) {
		SGSChannel channel = getChannel(id);
		if (channel == null) { // channel not opened or otherwise mapped
			return;
		}
		SGSUser sgsUser = userMap.get(user);
		if (sgsUser != null) {
			channel.join(sgsUser);
		}
	}

	/**
	 * Removes the specified user from the Channel referenced by the given
	 * ChannelID.
	 * 
	 * @param user
	 *            the user
	 * @param id
	 *            the ChannelID
	 */
	public void leave(UserID user, ChannelID id) {
		SGSChannel channel = getChannel(id);
		if (channel == null) { // channel not opened or otherwise mapped
			return;
		}
		SGSUser sgsUser = userMap.get(user);
		if (sgsUser != null) {
			channel.leave(sgsUser);
		}
	}

	/**
	 * Locks the given channel based on shouldLock. Users cannot join/leave
	 * locked channels except by way of the Router.
	 * 
	 * @param cid
	 *            the channel ID
	 * @param shouldLock
	 *            if true, will lock the channel, otherwise unlock it.
	 */
	public void lock(ChannelID cid, boolean shouldLock) {
		SGSChannel channel = getChannel(cid);
		if (channel == null) { // no channel in map
			return;
		}
		channel.setLocked(shouldLock);
	}

	/**
	 * Closes the local view of the channel mapped to ChannelID. Any remaining
	 * users will be notified as the channel is closing.
	 * 
	 * @param id
	 *            the ID of the channel to close.
	 */
	public void closeChannel(ChannelID id) {
		SGSChannel channel = getChannel(id);
		if (channel == null) { // no channel in map
			return;
		}
		channel.close();
	}

}
