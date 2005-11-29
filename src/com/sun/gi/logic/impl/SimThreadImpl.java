package com.sun.gi.logic.impl;

import com.sun.gi.logic.SimThread;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimKernel;

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

public class SimThreadImpl extends Thread implements SimThread {
	SimTask task = null;

	private boolean result;

	private SimKernel kernel;

	private boolean reused = true;

	public SimThreadImpl(SimKernel kernel) {
		super();
		this.kernel = kernel;
		this.start();
	}

	public void run() {
		do {
			kernel.returnToThreadPool(this);
			synchronized (this) {
				while (task == null) {
					try {
						wait();
					} catch (InterruptedException ex) {
						ex.printStackTrace();
					}
				}
			}	
			task.execute();
			synchronized (this) {
				task = null;
				notifyAll();
			}
		} while (reused);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.sun.gi.logic.SimThread#execute(com.sun.gi.logic.SimTask)
	 */
	public void execute(SimTask task) {
		synchronized(this){
			if (this.task!=null){
				throw new RuntimeException("Illegal attempt to reuse thread that is not finished");
			}
			this.task = task;
			notifyAll();
		}
		

	}
}
