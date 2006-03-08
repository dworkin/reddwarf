package com.sun.gi.gloutils.pdtimer;

import java.lang.reflect.Method;
import java.util.logging.Logger;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;

/**
 *
 * <p>Title: PDTimerEvent.java</p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004 Sun Microsystems, Inc.</p>
 * <p>Company: Sun Microsystems, Inc</p>
 * @author Jeff Kesselman
 * @version 1.0
 */
public class PDTimerEvent implements GLO {

	/**
	 * hardwired serial version UID
	 */
	private static final long serialVersionUID = 1L;
        private static Logger log = Logger.getLogger("com.sun.gi.gloutils.pdtimer");
	public boolean isActive=true;
	ACCESS_TYPE accessType;
	long delay;
	boolean repeat;
	GLOReference target;
	String methodName;
	Object[] parameters;

	
	/**
	 * @param access
	 * @param delay
	 * @param repeat
	 * @param target
	 * @param methodName
	 * @param parameters
	 */
	public PDTimerEvent(ACCESS_TYPE access, long delay, boolean repeat, GLOReference target, String methodName, Object[] parameters) {
		// TODO Auto-generated constructor stub
		this.accessType = access;
		this.delay = delay;
		this.repeat = repeat;
		this.target = target;
		this.methodName = methodName;
		this.parameters = parameters;
	}

	/**
	 * @param task
	 */
	public void fire(SimTask task) {
		if (!isActive){
			System.err.println("ERROR: Nonactive timer event fired!");
			return;
		}
		Object targetObject = target.peek(task);
		Class[] classes = new Class[parameters.length];
		for(int i=0;i<parameters.length;i++){
			classes[i]=parameters[i].getClass();
		}
		try {
			Method method = targetObject.getClass().getMethod(methodName,classes);
			task.queueTask(accessType,target,method,parameters);
			isActive = false;
		} catch (SecurityException e) {
			
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			
			e.printStackTrace();
		}
		
	}

	public long delayTime() {		
		return delay;
	}

	public boolean requiresCleanup() {		
		return !isActive;
	}

	public boolean isRepeating() {		
		return repeat;
	}

	public void reset(SimTask task) {
		task.access_check(ACCESS_TYPE.GET,this);
		log.finest("Restting event");
		isActive=true;
		
	}

	public boolean isMoribund() {
		return (!isActive)&&(!repeat);
	}

	public boolean isActive() {	
		return isActive;
	}
}
