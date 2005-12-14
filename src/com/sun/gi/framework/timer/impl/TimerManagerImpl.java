/**
 *
 * <p>Title: TimerManagerImpl.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
package com.sun.gi.framework.timer.impl;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.sun.gi.framework.timer.TimerManager;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

/**
 *
 * <p>Title: TimerManagerImpl.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class TimerManagerImpl implements TimerManager{
	
	// classes for use as Object Store data
	
	class TimerEvent implements Serializable{
		/**
		 * Hard serial version UID
		 */
		private static final long serialVersionUID = 1L;
		private long appID;
		private long objID;
		private String methodName;
		private Serializable[] arguments;
		private long delayMS;
		private boolean repeat;
		private boolean active;
		
		public TimerEvent(long appID, long objID, String methodName, Serializable[] args,
				long delayMS,boolean repeat){
			this.appID = appID;
			this.objID = objID;
			this.methodName = methodName;
			this.arguments = args;
			this.delayMS = delayMS;
			this.repeat = repeat;
			active=true;
		}
	}
	
	class TimerBucket implements Serializable {
		/**
		 * Fixed serial version UID
		 */
		private static final long serialVersionUID = 1L;
		private long bucketMultiple;
		private List<GLOReference> timerEvents = new ArrayList<GLOReference>();
		
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.framework.timer.TimerManager#registerEvent(long, long, java.lang.reflect.Method, java.lang.Object[], long, boolean)
	 */
	public long registerEvent(long appID, long startObjectID, Method startMethod, Object[] startArgs, long startTime, boolean repeating) {
		// TODO Auto-generated method stub
		return 0;
	}

	/* (non-Javadoc)
	 * @see com.sun.gi.framework.timer.TimerManager#removeEvent(long)
	 */
	public void removeEvent(long eventID) {
		// TODO Auto-generated method stub
		
	}

	

	
}
