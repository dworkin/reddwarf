/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.service.data.store.cache.queue;

import com.sun.sgs.impl.service.data.store.cache.CacheConsistencyException;
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
import java.util.Arrays;

/**
 * The subclass of all requests used by {@link UpdateQueue}, with nested
 * classes that implement the specific requests.
 */
public abstract class UpdateQueueRequest implements Request {

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

    /**
     * The {@code CompletionHandler} to call when the request is completed, or
     * {@code null} if there is no handler.
     */
    private final CompletionHandler completionHandler;

    /**
     * Creates an instance with no completion handler, for use in constructing
     * request objects on the server side.
     */
    UpdateQueueRequest() {
	completionHandler = null;
    }

    /**
     * Creates an instance with the specified completion handler.
     *
     * @param	completionHandler the completion handler
     */
    UpdateQueueRequest(CompletionHandler completionHandler) {
	this.completionHandler = completionHandler;
    }

    /** {@inheritDoc} */
    @Override
    public void completed() {
	if (completionHandler != null) {
	    completionHandler.completed();
	}
    }

    /**
     * Performs a request using the specified server and node ID.
     *
     * @param	server the underlying server
     * @param	nodeId the node ID associated with the update queue
     * @throws	Exception if the request fails
     */
    abstract void performRequest(UpdateQueueServer server, long nodeId)
	throws Exception;

    /** The request handler used to implement {@link UpdateQueue}. */
    public static class UpdateQueueRequestHandler
	implements Request.RequestHandler<UpdateQueueRequest>
    {
	/** The underlying server that handles requests. */
	private final UpdateQueueServer server;

	/** The node ID associated with this queue. */
	private final long nodeId;

	/**
	 * Creates an instance of this class.
	 *
	 * @param	server the underlying server
	 * @param	nodeId the node ID associated with this queue
	 */
	public UpdateQueueRequestHandler(
	    UpdateQueueServer server, long nodeId)
	{
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

    /** Represents a call to {@link UpdateQueue#commit}. */
    static class Commit extends UpdateQueueRequest {
	/** The context ID of the associated transaction, or -1 if unknown. */
	private final long contextId;
	private final long[] oids;
	private final byte[][] oidValues;
	private final int newOids;
	private final String[] names;
	private final long[] nameValues;

	Commit(long contextId,
	       long[] oids,
	       byte[][] oidValues,
	       int newOids,
	       String[] names,
	       long[] nameValues,
	       CompletionHandler completionHandler)
	{
	    super(completionHandler);
	    this.contextId = contextId;
	    this.oids = oids;
	    this.oidValues = oidValues;
	    this.newOids = newOids;
	    this.names = names;
	    this.nameValues = nameValues;
	}

	Commit(DataInput in) throws IOException {
	    contextId = -1;
	    oids = readLongs(in);
	    oidValues = readByteArrays(in);
	    newOids = in.readInt();
	    names = readStrings(in);
	    nameValues = readLongs(in);
	}

	@Override
	public String toString() {
	    return "Commit[" +
		(contextId != -1 ? "contextId:" + contextId + ", " : "") +
		"oids:" + Arrays.toString(oids) +
		", newOids:" + newOids +
		", names:" + Arrays.toString(names) +
		"]";
	}

	@Override
	void performRequest(UpdateQueueServer server, long nodeId)
	    throws CacheConsistencyException
	{
	    server.commit(nodeId, oids, oidValues, newOids, names, nameValues);
	}

	@Override
	public void writeRequest(DataOutput out) throws IOException {
	    out.write(COMMIT);
	    writeLongs(oids, out);
	    writeByteArrays(oidValues, out);
	    out.writeInt(newOids);
	    writeStrings(names, out);
	    writeLongs(nameValues, out);
	}
    }

    /** Represents a call to {@link UpdateQueue#evictObject}. */
    static class EvictObject extends UpdateQueueRequest {
	private final long oid;

	EvictObject(long oid, CompletionHandler completionHandler) {
	    super(completionHandler);
	    this.oid = oid;
	}

	EvictObject(DataInput in) throws IOException {
	    oid = in.readLong();
	}

	@Override
	public String toString() {
	    return "EvictObject[oid:" + oid + "]";
	}

	@Override
	void performRequest(UpdateQueueServer server, long nodeId)
	    throws CacheConsistencyException
	{
	    server.evictObject(nodeId, oid);
	}

	@Override
	public void writeRequest(DataOutput out) throws IOException {
	    out.write(EVICT_OBJECT);
	    out.writeLong(oid);
	}
    }

    /** Represents a call to {@link UpdateQueue#downgradeObject}. */
    static class DowngradeObject extends UpdateQueueRequest {
	private final long oid;

	DowngradeObject(long oid, CompletionHandler completionHandler) {
	    super(completionHandler);
	    this.oid = oid;
	}

	DowngradeObject(DataInput in) throws IOException {
	    oid = in.readLong();
	}

	@Override
	public String toString() {
	    return "DowngradeObject[oid:" + oid + "]";
	}

	@Override
	void performRequest(UpdateQueueServer server, long nodeId)
	    throws CacheConsistencyException
	{
	    server.downgradeObject(nodeId, oid);
	}

	@Override
	public void writeRequest(DataOutput out) throws IOException {
	    out.write(DOWNGRADE_OBJECT);
	    out.writeLong(oid);
	}
    }

    /** Represents a call to {@link UpdateQueue#evictBinding}. */
    static class EvictBinding extends UpdateQueueRequest {
	private final String name;

	EvictBinding(String name, CompletionHandler completionHandler) {
	    super(completionHandler);
	    this.name = name;
	}

	EvictBinding(DataInput in) throws IOException {
	    name = readString(in);
	}

	@Override
	public String toString() {
	    return "EvictBinding[name:" + name + "]";
	}

	@Override
	void performRequest(UpdateQueueServer server, long nodeId)
	    throws CacheConsistencyException
	{
	    server.evictBinding(nodeId, name);
	}

	@Override
	public void writeRequest(DataOutput out) throws IOException {
	    out.write(EVICT_BINDING);
	    writeString(name, out);
	}
    }

    /** Represents a call to {@link UpdateQueue#downgradeBinding}. */
    static class DowngradeBinding extends UpdateQueueRequest {
	private final String name;

	DowngradeBinding(String name, CompletionHandler completionHandler) {
	    super(completionHandler);
	    this.name = name;
	}

	DowngradeBinding(DataInput in) throws IOException {
	    name = readString(in);
	}

	@Override
	public String toString() {
	    return "DowngradeBinding[name:" + name + "]";
	}

	@Override
	void performRequest(UpdateQueueServer server, long nodeId)
	    throws CacheConsistencyException
	{
	    server.downgradeBinding(nodeId, name);
	}

	@Override
	public void writeRequest(DataOutput out) throws IOException {
	    out.write(DOWNGRADE_BINDING);
	    writeString(name, out);
	}
    }
}
