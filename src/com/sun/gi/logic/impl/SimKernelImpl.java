package com.sun.gi.logic.impl;

import java.util.HashMap;
import java.util.Map;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.Router;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.SOReference;
import com.sun.gi.logic.SimFinder;
import com.sun.gi.logic.SimKernel;
import com.sun.gi.logic.SimThread;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;
import com.sun.multicast.util.UnimplementedOperationException;


/**
 * <p>
 * Title:
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2003
 * </p>
 * <p>
 * Company:
 * </p>
 * 
 * @author not attributable
 * @version 1.0
 */

public class SimKernelImpl implements SimKernel {
	private ObjectStore ostore;

	

	public SimKernelImpl(ObjectStore ostore) {
		this.ostore = ostore;
		

	}

	public Transaction newTransaction(long appID, ClassLoader loader) {
		return ostore.newTransaction(appID, loader);
	}

	public SimThread getSimThread() {
		SimThread st = new SimThreadImpl(this);
		return st;
	}

	public ObjectStore getOstore() {
		return ostore;
	}

	
	/**
	 * createUser
	 */
	public UserID createUser() {
		// return router.createUser();
		throw new UnimplementedOperationException();
	}

	/**
	 * sendData
	 * 
	 * @param targets
	 *            UserID[]
	 * @param from
	 *            UserID
	 * @param bs
	 *            byte[]
	 */
	public void sendData(ChannelID cid, UserID[] targets, UserID from, byte[] bs) {
		throw new UnimplementedOperationException();
	}

}
