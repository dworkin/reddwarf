package com.sun.sgs.test.util;

import java.util.LinkedList;
import java.util.List;

import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.Task;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.service.TransactionProxy;

public class DummyTaskService implements TaskService, TransactionParticipant {
    
    private final List<Runner> tasks;
    
    public DummyTaskService() {
	this.tasks = new LinkedList<Runner>();
    }

    public void scheduleNonDurableTask(KernelRunnable task) {
	tasks.add(new Runner(task));
    }

    public void scheduleNonDurableTask(KernelRunnable task, long delay) {
	tasks.add(new Runner(task, delay));
    }

    public void scheduleNonDurableTask(KernelRunnable task, Priority priority) {
	tasks.add(new Runner(task));
    }

    public void shutdownTransactional() {
	tasks.clear();
    }

    public PeriodicTaskHandle schedulePeriodicTask(Task task, long delay, long period) {
	throw new UnsupportedOperationException("not implemented");
    }

    public void scheduleTask(Task task) {
	throw new UnsupportedOperationException("not implemented");
    }

    public void scheduleTask(Task task, long delay) {
	throw new UnsupportedOperationException("not implemented");
    }

    public void configure(ComponentRegistry registry, TransactionProxy proxy) {
	// no-op
    }

    public String getName() {
	return getClass().getName();
    }

    public void abort(Transaction txn) {
	tasks.clear();
    }

    public void commit(Transaction txn) {
	for (Runner runnable : tasks) {
	    new Thread(runnable).start();
	}
    }

    public boolean prepare(Transaction txn) throws Exception {
	return (! tasks.isEmpty());
    }

    public void prepareAndCommit(Transaction txn) throws Exception {
	if (! prepare(txn))
	    commit(txn);
    }
    
    static class Runner implements Runnable {
	private final KernelRunnable runnable;
	private final long startTime;
	
	Runner(KernelRunnable runnable) {
	    this(runnable, 0);
	}
	
	Runner(KernelRunnable runnable, long delay) {
	    this.runnable = runnable;
	    this.startTime = System.currentTimeMillis() + delay;
	}
	
	public void run() {
	    final long delay = System.currentTimeMillis() - startTime;
	    if (delay > 0) {
		try {
		    Thread.sleep(delay);
		} catch (InterruptedException e) {
		    return;
		}
	    }
	    try {
		runnable.run();
	    } catch (RuntimeException e) {
		throw e;
	    } catch (Exception e) {
		throw new RuntimeException(e);
	    }
	}
    }

}
