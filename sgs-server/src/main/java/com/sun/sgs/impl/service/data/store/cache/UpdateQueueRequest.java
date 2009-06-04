/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.data.store.cache;

import static com.sun.sgs.impl.util.DataStreamUtil.readByteArrays;
import static com.sun.sgs.impl.util.DataStreamUtil.readLongs;
import static com.sun.sgs.impl.util.DataStreamUtil.readString;
import static com.sun.sgs.impl.util.DataStreamUtil.readStrings;
import static com.sun.sgs.impl.util.DataStreamUtil.writeByteArrays;
import static com.sun.sgs.impl.util.DataStreamUtil.writeLongs;
import static com.sun.sgs.impl.util.DataStreamUtil.writeString;
import static com.sun.sgs.impl.util.DataStreamUtil.writeStrings;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;

/**
 * The subclass of all requests used to implement {@link UpdateQueue}.
 */
abstract class UpdateQueueRequest implements Request {

    /** The identifier for {@link Commit} requests. */
    static final byte COMMIT = 1;

    /** The identifier for {@link EvictObject} requests. */
    static final byte EVICT_OBJECT = 2;

    /** The identifier for {@link DowngradeObject} requests. */
    static final byte DOWNGRADE_OBJECT = 3;

    /** The identifier for {@link EvictBinding} requests. */
    static final byte EVICT_BINDING = 4;

    /** The identifier for {@link DowngradeBinding} requests. */
    static final byte DOWNGRADE_BINDING = 5;

    /** The request handler used to implement {@link UpdateQueue}. */
    static class UpdateQueueRequestHandler
	implements Request.RequestHandler<UpdateQueueRequest>
    {
	/** The underlying server that handles requests. */
	private final CachingDataStoreServerImpl server;

	/** The node ID associated with this queue. */
	private final long nodeId;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	server the underlying server
	 * @param	nodeId the node ID associated with this queue
	 */
	UpdateQueueRequestHandler(
	    CachingDataStoreServerImpl server, long nodeId) {
	    this.server = server;
	    this.nodeId = nodeId;
	}

	/* -- Implement RequestHandler -- */

	/** {@inheritDoc} */
	public UpdateQueueRequest readRequest(DataInput in)
	    throws IOException
	{
	    byte requestType = in.readByte();
	    switch (requestType) {
	    case COMMIT:
		return new Commit(in);
	    case EVICT_OBJECT:
		return new EvictObject(in);
	    case DOWNGRADE_OBJECT:
		return new DowngradeObject(in);
	    case EVICT_BINDING:
		return new EvictBinding(in);
	    case DOWNGRADE_BINDING:
		return new DowngradeBinding(in);
	    case -1:
		throw new EOFException("End of file while reading request");
	    default:
		throw new IOException("Unknown request type: " + requestType);
	    }
	}

	/** {@inheritDoc} */
	public void performRequest(UpdateQueueRequest request)
	    throws Exception
	{
	    request.performRequest(server, nodeId);
	}
    }

    /** {@inheritDoc} */
    public void failed(Throwable exception) {
	/*
	 * Shutdown node?  This shouldn't happen for IOExceptions, which should
	 * get retried.
	 */
    }

    /**
     * Performs a request using the specified server and node ID.
     *
     * @param	server the underlying server
     * @param	nodeId the node ID associated with the update queue
     * @throws	Exception if the request fails
     */
    abstract void performRequest(
	CachingDataStoreServerImpl server, long nodeId)
	throws Exception;

    /**
     * A subclass of {@code UpdateQueueRequest} for requests that contain a
     * completion handler.
     *
     * @param	<T> the type value associated with this request
     */
    abstract static class UpdateQueueRequestWithCompletion<T>
	extends UpdateQueueRequest
    {
	/** The value associated with the request. */
	final T value;

	/** The completion handler or {@code null}. */
	private final UpdateQueue.CompletionHandler<T> handler;

	/**
	 * Creates an instance with no completion handler.
	 *
	 * @param	value the value associated with this request
	 */
	UpdateQueueRequestWithCompletion(T value) {
	    this.value = value;
	    handler = null;
	}

	/**
	 * Creates an instance with the specified completion handler.
	 *
	 * @param	value the value associated with this request
	 * @param	handler the completion handler
	 */
	UpdateQueueRequestWithCompletion(
	    T value, UpdateQueue.CompletionHandler<T> handler)
	{
	    this.value = value;
	    this.handler = handler;
	}

	public void completed(Throwable exception) {
	    if (handler != null) {
		handler.completed(value);
	    }
	}
    }

    /** Represents a call to {@link UpdateQueue#commit}. */
    static class Commit extends UpdateQueueRequest {
	private final long[] oids;
	private final byte[][] oidValues;
	private final String[] names;
	private final long[] nameValues;

	Commit(
	    long[] oids, byte[][] oidValues, String[] names, long[] nameValues)
	{
	    this.oids = oids;
	    this.oidValues = oidValues;
	    this.names = names;
	    this.nameValues = nameValues;
	}

	Commit(DataInput in) throws IOException {
	    oids = readLongs(in);
	    oidValues = readByteArrays(in);
	    names = readStrings(in);
	    nameValues = readLongs(in);
	}

	void performRequest(
	    CachingDataStoreServerImpl server, long nodeId)
	    throws CacheConsistencyException
	{
	    server.commit(nodeId, oids, oidValues, names, nameValues);
	}

	public void writeRequest(DataOutput out) throws IOException {
	    out.write(COMMIT);
	    writeLongs(oids, out);
	    writeByteArrays(oidValues, out);
	    writeStrings(names, out);
	    writeLongs(nameValues, out);
	}

	public void completed(Throwable exception) {
	    /* Nothing to do */
	}
    }

    /** Represents a call to {@link UpdateQueue#evictObject}. */
    static class EvictObject extends UpdateQueueRequestWithCompletion<Long> {

	EvictObject(long oid, UpdateQueue.CompletionHandler<Long> handler) {
	    super(oid, handler);
	}

	EvictObject(DataInput in) throws IOException {
	    super(in.readLong());
	}

	void performRequest(CachingDataStoreServerImpl server, long nodeId)
	    throws CacheConsistencyException
	{
	    server.evictObject(nodeId, value);
	}

	public void writeRequest(DataOutput out) throws IOException {
	    out.write(EVICT_OBJECT);
	    out.writeLong(value);
	}
    }

    /** Represents a call to {@link UpdateQueue#downgradeObject}. */
    static class DowngradeObject
	extends UpdateQueueRequestWithCompletion<Long>
    {
	DowngradeObject(long oid, UpdateQueue.CompletionHandler<Long> handler)
	{
	    super(oid, handler);
	}

	DowngradeObject(DataInput in) throws IOException {
	    super(in.readLong());
	}

	void performRequest(CachingDataStoreServerImpl server, long nodeId) 
	    throws CacheConsistencyException
	{
	    server.downgradeObject(nodeId, value);
	}

	public void writeRequest(DataOutput out) throws IOException {
	    out.write(DOWNGRADE_OBJECT);
	    out.writeLong(value);
	}
    }

    /** Represents a call to {@link UpdateQueue#evictBinding}. */
    static class EvictBinding
	extends UpdateQueueRequestWithCompletion<String>
    {
	EvictBinding(
	    String name, UpdateQueue.CompletionHandler<String> handler)
	{
	    super(name, handler);
	}

	EvictBinding(DataInput in) throws IOException {
	    super(readString(in));
	}

	void performRequest(CachingDataStoreServerImpl server, long nodeId)
	    throws CacheConsistencyException
	{
	    server.evictBinding(nodeId, value);
	}

	public void writeRequest(DataOutput out) throws IOException {
	    out.write(EVICT_BINDING);
	    writeString(value, out);
	}
    }

    /** Represents a call to {@link UpdateQueue#downgradeBinding}. */
    static class DowngradeBinding
	extends UpdateQueueRequestWithCompletion<String>
    {
	DowngradeBinding(
	    String name, UpdateQueue.CompletionHandler<String> handler)
	{
	    super(name, handler);
	}

	DowngradeBinding(DataInput in) throws IOException {
	    super(readString(in));
	}

	void performRequest(CachingDataStoreServerImpl server, long nodeId)
	    throws CacheConsistencyException
	{
	    server.downgradeBinding(nodeId, value);
	}

	public void writeRequest(DataOutput out) throws IOException {
	    out.write(DOWNGRADE_BINDING);
	    writeString(value, out);
	}
    }
}
