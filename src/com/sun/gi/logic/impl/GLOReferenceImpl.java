package com.sun.gi.logic.impl;

import java.io.Serializable;
import java.io.ObjectInputStream;
import java.io.IOException;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

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
		task.getTransaction().destroy(objID);
		
	}

	public Serializable get(SimTask task) {
		if ((objectCache == null) || (peeked == true)) {
			objectCache = task.getTransaction().lock(objID);
			task.registerGLOID(objID, objectCache);
			peeked = false;
		}
		return objectCache;
	}

	public Serializable peek(SimTask task) {
		if (objectCache == null) {
			objectCache = task.getTransaction().peek(objID);
			task.registerGLOID(objID, objectCache);
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
		throw new UnsupportedOperationException("Not yet implemented");
		// return null;
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
