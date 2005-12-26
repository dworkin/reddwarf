package com.sun.gi.logic.impl;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;
import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.objectstore.NonExistantObjectIDException;

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

public class GLOReferenceImpl implements GLOReference, Serializable, Comparable {
	long objID;

	transient boolean peeked;

	transient Serializable objectCache;

	public GLOReferenceImpl(long id) {
		objID = id;
		objectCache = null;
	}

	private void initTransients() {
		peeked = false;
		objectCache = null;
	}

	private void readObject(ObjectInputStream in) throws IOException,
			ClassNotFoundException {
		in.defaultReadObject();
		initTransients();
	}
	
	/* (non-Javadoc)
	 * @see com.sun.gi.logic.GLOReference#delete(com.sun.gi.logic.SimTask)
	 */
	public void delete(SimTask task) {
		try {
			task.getTransaction().destroy(objID);
		} catch (DeadlockException e) {
			
			e.printStackTrace();
		} catch (NonExistantObjectIDException e) {
			
			e.printStackTrace();
		}
		
	}

	public Serializable get(SimTask task) {
		if ((objectCache == null) || (peeked == true)) {
			try {
				objectCache = task.getTransaction().lock(objID);
			} catch (DeadlockException e) {
				
				e.printStackTrace();
			} catch (NonExistantObjectIDException e) {
				
				e.printStackTrace();
			}
			task.registerGLOID(objID, objectCache, ACCESS_TYPE.GET);
			peeked = false;
		}
		return objectCache;
	}

	public Serializable peek(SimTask task) {
		if (objectCache == null) {
			try {
				objectCache = task.getTransaction().peek(objID);
			} catch (NonExistantObjectIDException e) {
				
				e.printStackTrace();
			}
			task.registerGLOID(objID, objectCache, ACCESS_TYPE.PEEK);
			peeked = true;
		}
		return objectCache;
	}

	/**
	 * shallowCopy
	 * 
	 * @return SOReference
	 */
	public GLOReference shallowCopy() {
		return new GLOReferenceImpl(objID);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.logic.GLOReference#attempt(com.sun.gi.logic.SimTask)
	 */
	public Serializable attempt(SimTask task) {
		if ((objectCache == null) || (peeked == true)) {
			try {
				objectCache = task.getTransaction().lock(objID,false);
			} catch (DeadlockException e) {
				
				e.printStackTrace();
			} catch (NonExistantObjectIDException e) {
				
				e.printStackTrace();
			}
			if (objectCache==null){
				return null;
			}
			task.registerGLOID(objID, objectCache, ACCESS_TYPE.ATTEMPT);// was gotten with ATTEMPT
			peeked = false;
		}
		return objectCache;
	}

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(T)
	 */
	public int compareTo(Object arg0) {
		GLOReferenceImpl other = (GLOReferenceImpl)arg0;
		if (objID< other.objID ){
			return -1;
		} else if (objID > other.objID) {
			return 1;
		} else {
			return 0;
		}
	}
	
	public boolean equals(Object other){
		return (compareTo(other)==0);
	}

	
}
