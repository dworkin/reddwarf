/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import java.io.Serializable;

/**
 * Utility class for reserving blocks of non-reusable IDs.
 */
public class IdGenerator {

    /** The minimum number of IDs to reserve. */
    public static int MIN_BLOCK_SIZE = 8;

    private final String name;
    private final int blockSize;
    private final TransactionProxy txnProxy;
    private final NonDurableTaskScheduler scheduler;
    private final TransactionContextFactory<Context> contextFactory;
    private final Object lock = new Object();
    private ReserveIdBlockTask reserveTask = null;
    private long nextId = 1;
    private long lastReservedId = 0;

    /**
     * Constructs an instance with the specified {@code name}, {@code
     * blockSize}, transaction {@code proxy}, and {@code scheduler}.
     *
     * @param	name the service binding name for this generator
     * @param	blockSize the block size for ID reservation
     * @param	proxy the transaction proxy
     * @param	scheduler a task scheduler
     *
     * @throws	IllegalArgumentException if the specified {@code name}
     *		is empty or if the specified {@code blockSize} is less
     *		than the minimum allowed
     */
    public IdGenerator(
	String name, int blockSize,
	TransactionProxy proxy,
	NonDurableTaskScheduler scheduler)
    {
	if (name == null) {
	    throw new NullPointerException("null name");
	} else if (name.equals("")) {
	    throw new IllegalArgumentException("empty name");
	} else if (blockSize < MIN_BLOCK_SIZE) {
	    throw new IllegalArgumentException("invalid block size");
	} else if (proxy == null) {
	    throw new NullPointerException("null transaction proxy");
	} else if (scheduler == null) {
	    throw new NullPointerException("null scheduler");
	}
	this.name = name;
	this.blockSize = blockSize;
	this.scheduler = scheduler;
	this.txnProxy = proxy;
	this.contextFactory = new ContextFactory(proxy);
    }

    /**
     * Returns the next ID.  This method may block if the current
     * block of IDs is exhausted, while it waits for a task to reserve
     * another block of IDs.  This method may be called whether or not
     * a transaction is active.  If a new block of IDs needs to be
     * reserved, the block will be reserved regardless of the state or
     * outcome of the current transaction (if any).
     *
     * @return	the next ID
     * @throws	InterruptedException if this method is interrupted
     *		while waiting for the task to reserve a block of IDs
     */
    public long next() throws InterruptedException {
	synchronized (lock) {
	    while (nextId > lastReservedId) {
		if (reserveTask == null) {
		    // schedule task to reserve next block
		    reserveTask = new ReserveIdBlockTask();
		    scheduler.scheduleTask(reserveTask);
		} else {
		    lock.wait();
		}
	    }
	    return nextId++;
	}
    }

    /**
     * Returns the next ID in a byte array in network byte order.  This
     * is equivalent to invoking {@link #next next} and storing the
     * result in a byte array in network byte order.
     *
     * @return	the next ID in a byte array
     * @throws	InterruptedException if this method is interrupted
     *		while waiting for the task to reserve a block of IDs
     */
    public byte[] nextBytes() throws InterruptedException {
	long id = next();
	byte[] idBytes = new byte[8];
	for (int i = idBytes.length-1; i >=0; i--) {
	    idBytes[i] = (byte) id;
	    id >>>= 8;
	}
	return idBytes;
    }

    /* -- Other classes -- */

    
    private class ContextFactory extends TransactionContextFactory<Context> {
	ContextFactory(TransactionProxy txnProxy) {
	    super(txnProxy);
	}
	
	/** {@inheritDoc} */
	public Context createContext(Transaction txn) {
	    return new Context(txn);
	}
    }

    private final class Context extends TransactionContext {
	
	private long firstId;
	private long lastId;

	/**
	 * Constructs a context with the specified transaction.
	 */
        private Context(Transaction txn) {
	    super(txn);
	}

	public void abort(boolean retryable) {
	    synchronized (lock) {
		if (! retryable) {
		    reserveTask = null;
		}
		lock.notifyAll();
	    }
	}

	public void commit() {
	    synchronized (lock) {
		nextId = firstId;
		lastReservedId = lastId;
		reserveTask = null;
		lock.notifyAll();
	    }
        }
    }

    /**
     * Task to reserve the next block of IDs for this generator.
     */
    private class ReserveIdBlockTask extends AbstractKernelRunnable {

	/** {@inheritDoc} */
	public void run() {
	    Context context = contextFactory.joinTransaction();
	    DataService dataService = txnProxy.getService(DataService.class);
	    State state;
	    try {
		state = dataService.getServiceBinding(name, State.class);
	    } catch (NameNotBoundException e) {
		state = new State(0);
		dataService.setServiceBinding(name, state);
	    }
	    dataService.markForUpdate(state);
	    context.firstId = state.lastId + 1;
	    context.lastId = state.reserve(blockSize);
	}
    }

    /**
     * {@code IdGenerator} state.
     */
    private static class State implements ManagedObject, Serializable {

	/** The serialVersionUID for this class. */
	private static final long serialVersionUID = 1L;

	/** The last reserved ID. */
	private long lastId;

	/**
	 * Constructs an instance of this class with the specified {@code id}.
	 */
	State(int id) {
	    this.lastId = id;
	}

	/**
	 * Reserves a block of IDs, advancing the last ID by the
	 * specified {@code blockSize}.
	 */
	long reserve(int blockSize) {
	    lastId += blockSize;
	    return lastId;
	}
    }
}
