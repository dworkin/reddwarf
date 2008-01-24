/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;
import java.io.Serializable;

/**
 * Utility class for reserving blocks of non-reusable IDs.  The
 * generated IDs start at {@code 1L}.
 */
public class IdGenerator {

    /** The minimum number of IDs to reserve. */
    public static final int MIN_BLOCK_SIZE = 8;

    private final String name;
    private final int blockSize;
    private final TransactionProxy txnProxy;
    private final TaskScheduler scheduler;
    private final Identity owner;
    private final Object lock = new Object();
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
	TaskScheduler scheduler)
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
	this.txnProxy = proxy;
	this.scheduler = scheduler;
	this.owner = proxy.getCurrentOwner();
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
     * @throws	Exception if there is a problem reserving a block of IDs
     */
    public long next() throws Exception {
	synchronized (lock) {
	    if (nextId > lastReservedId) {
		ReserveIdBlockTask reserveTask = new ReserveIdBlockTask();
		scheduler.runTransactionalTask(reserveTask, owner);
		nextId = reserveTask.firstId;
		lastReservedId = reserveTask.lastId;
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
     * @throws	Exception if there is a problem reserving a block of IDs
     */
    public byte[] nextBytes() throws Exception {
	long id = next();
	byte[] idBytes = new byte[8];
	for (int i = idBytes.length-1; i >=0; i--) {
	    idBytes[i] = (byte) id;
	    id >>>= 8;
	}
	return idBytes;
    }

    /* -- Other classes -- */

    /**
     * Task to reserve the next block of IDs for this generator.
     */
    private final class ReserveIdBlockTask extends AbstractKernelRunnable {

	volatile long firstId;
	volatile long lastId;

	/** {@inheritDoc} */
	public void run() {
	    DataService dataService = txnProxy.getService(DataService.class);
	    State state;
	    try {
		state = dataService.getServiceBinding(name, State.class);
	    } catch (NameNotBoundException e) {
		state = new State(0);
		dataService.setServiceBinding(name, state);
	    }
	    dataService.markForUpdate(state);
	    firstId = state.lastId + 1;
	    lastId = state.reserve(blockSize);
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
