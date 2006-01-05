/**
 *
 * <p>Title: InMemoryDataSpaceTransaction.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.objectstore.tso.dataspace;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.ObjectInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.tso.DataSpaceTransaction;
import com.sun.gi.utils.classes.CLObjectInputStream;

/**
 * 
 * <p>
 * Title: InMemoryDataSpaceTransaction.java
 * </p>
 * <p>
 * Description:
 * </p>
 * <p>
 * Copyright: Copyright (c) 2004 Sun Microsystems, Inc.
 * </p>
 * <p>
 * Company: Sun Microsystems, Inc
 * </p>
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public class DataSpaceTransactionImpl implements DataSpaceTransaction {
	private DataSpace dataSpace;


	private ClassLoader loader;	

	private Map<String, Long> newNames = new HashMap<String, Long>();

	private Set<Long> deleteSet = new HashSet<Long>();
	
	private Map<Long, Serializable> localObjectCache = new HashMap<Long,Serializable>();
	
	private Map<Long, byte[]> updateMap = new HashMap<Long,byte[]>();
	
	private Set<Long> locksHeld = new HashSet<Long>();
	private Set<Long> insertSet = new HashSet<Long>();
	
	private boolean clear = false;
	
	
	
	/**
	 * @param appID
	 * @param loader2
	 * @param dataSpace
	 * @param backup
	 */
	public DataSpaceTransactionImpl(ClassLoader loader,
			DataSpace dataSpace) {
		this.dataSpace = dataSpace;
		this.loader = loader;
	
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.DataSpaceTransaction#create(java.io.Serializable)
	 */
	public long create(Serializable object) {
		Long objID = new Long(dataSpace.getNextID());
		localObjectCache.put(objID,object);
		updateMap.put(objID,serialize(object));
		insertSet.add(objID);
		return objID;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.DataSpaceTransaction#destroy(long)
	 */
	public void destroy(long objectID) {
		Long id = new Long(objectID);
		deleteSet.add(id);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.DataSpaceTransaction#read(long)
	 */
	public Serializable read(long objectID) throws NonExistantObjectIDException {
		Long id = new Long(objectID);
		Serializable obj = localObjectCache.get(id);
		if ((obj == null)&&(!clear)) { // if clear, pretend nothing in the data space
			byte[] objbytes = dataSpace.getObjBytes(objectID);			
			if (objbytes==null){
				throw new NonExistantObjectIDException();
			}
			obj = deserialize(objbytes);
			localObjectCache.put(new Long(objectID), obj);
		}
		return obj;
	}

	/**
	 * @param objbytes
	 * @param loader2
	 * @return
	 */
	private Serializable deserialize(byte[] objbytes) {
		ByteArrayInputStream bais = new ByteArrayInputStream(objbytes);
		try {			
			ObjectInputStream ois = new CLObjectInputStream(bais,loader);
			Serializable obj = (Serializable) ois.readObject();
			ois.close();
			return obj;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();		
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.DataSpaceTransaction#registerName(java.lang.String,
	 *      long)
	 */
	public void registerName(String name, long tsID) {
		newNames.put(name, new Long(tsID));
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.DataSpaceTransaction#lock(long)
	 */
	public void lock(long objectID) throws NonExistantObjectIDException {
		dataSpace.lock(objectID);
		locksHeld.add(objectID);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.DataSpaceTransaction#release(long)
	 */
	public void release(long objectID) {
		dataSpace.release(objectID);
		locksHeld.remove(objectID);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.DataSpaceTransaction#write(long,
	 *      com.sun.gi.objectstore.tso.TSOTransaction.DataHeader)
	 */
	public void write(long objectID, Serializable obj) {		
		updateMap.put(new Long(objectID), serialize(obj));
	}

	/**
	 * @param obj
	 * @return
	 */
	private byte[] serialize(Serializable obj) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			ObjectOutputStream oas = new ObjectOutputStream(baos);
			oas.writeObject(obj);
			oas.flush();
			oas.close();
			return baos.toByteArray();
		} catch (IOException e) {			
			e.printStackTrace();
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.DataSpaceTransaction#clear()
	 */
	public void clear() {
		clear  = true;
		resetTransaction();
	}
	
	private void resetTransaction(){
		newNames.clear();
		deleteSet.clear();
		insertSet.clear();
		updateMap.clear();
		localObjectCache.clear();
		//release left over locks
		for(Long id : locksHeld){
			dataSpace.release(id);
		}
		locksHeld.clear();
		
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.DataSpaceTransaction#commit()
	 */
	public void commit() {
		dataSpace.atomicUpdate(clear,newNames,deleteSet,updateMap,insertSet);
		resetTransaction();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.DataSpaceTransaction#abort()
	 */
	public void abort() {
		resetTransaction();
		
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.objectstore.tso.DataSpaceTransaction#lookupName(java.lang.String)
	 */
	public long lookupName(String name) {		
		Long l = newNames.get(name);
		if (l==null){ // not in transaction, check data space
			l = dataSpace.lookup(name);
		}
		if (l == null){
			return DataSpace.INVALID_ID;
		}
		return l.longValue();
	}

	/**
	 * 
	 */
	public void close() {
		abort();		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.objectstore.tso.DataSpaceTransaction#clear(long)
	 */


}
