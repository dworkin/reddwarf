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

package com.sun.sgs.impl.service.data.store.net;

import com.sun.sgs.impl.service.data.store.BindingValue;
import static com.sun.sgs.impl.util.DataStreamUtil.readBytes;
import static com.sun.sgs.impl.util.DataStreamUtil.readLongs;
import static com.sun.sgs.impl.util.DataStreamUtil.readString;
import static com.sun.sgs.impl.util.DataStreamUtil.writeBytes;
import static com.sun.sgs.impl.util.DataStreamUtil.writeLongs;
import static com.sun.sgs.impl.util.DataStreamUtil.writeString;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;

/**
 * Defines an experimental network protocol, not currently used, used to
 * transfer DataStoreServer methods over input and output streams, to use
 * instead of RMI.
 */
/*
 * FIXME: Send or check version information on initial connection.  Might need
 * to create separate client and server side classes to do this.
 *
 * RFE: Modify failure() to send the server-side stack trace, and checkResult()
 * to append it to the thrown exception's stack trace.  This would be useful
 * for debugging.
 */
class DataStoreProtocol implements DataStoreServer {

    /* -- Opcodes for the methods -- */

    private static final short NEW_NODE_ID = 1;
    private static final short CREATE_OBJECT = 2;
    private static final short MARK_FOR_UPDATE = 3;
    private static final short GET_OBJECT = 4;
    private static final short SET_OBJECT = 5;
    private static final short SET_OBJECTS = 6;
    private static final short REMOVE_OBJECT = 7;
    private static final short GET_BINDING = 8;
    private static final short SET_BINDING = 9;
    private static final short REMOVE_BINDING = 10;
    private static final short NEXT_BOUND_NAME = 11;
    private static final short GET_CLASS_ID = 12;
    private static final short GET_CLASS_INFO = 13;
    private static final short NEXT_OBJECT_ID = 14;
    private static final short CREATE_TRANSACTION = 100;
    private static final short PREPARE = 101;
    private static final short COMMIT = 102;
    private static final short PREPARE_AND_COMMIT = 103;
    private static final short ABORT = 104;

    /** The input stream. */
    private final DataInputStream in;

    /** The output stream. */
    private final DataOutputStream out;

    /** Creates an instance using the specified streams. */
    DataStoreProtocol(InputStream in, OutputStream out) {
	this.in = new DataInputStream(new BufferedInputStream(in));
	this.out = new DataOutputStream(new BufferedOutputStream(out));
    }

    /** Dispatches a single method call to the server. */
    void dispatch(DataStoreServer server) throws IOException {
	short op = in.readShort();
	switch (op) {
	case NEW_NODE_ID:
	    handleNewNodeId(server);
	    break;
	case CREATE_OBJECT:
	    handleCreateObject(server);
	    break;
	case MARK_FOR_UPDATE:
	    handleMarkForUpdate(server);
	    break;
	case GET_OBJECT:
	    handleGetObject(server);
	    break;
	case SET_OBJECT:
	    handleSetObject(server);
	    break;
	case SET_OBJECTS:
	    handleSetObjects(server);
	    break;
	case REMOVE_OBJECT:
	    handleRemoveObject(server);
	    break;
	case GET_BINDING:
	    handleGetBinding(server);
	    break;
	case SET_BINDING:
	    handleSetBinding(server);
	    break;
	case REMOVE_BINDING:
	    handleRemoveBinding(server);
	    break;
	case NEXT_BOUND_NAME:
	    handleNextBoundName(server);
	    break;
	case GET_CLASS_ID:
	    handleGetClassId(server);
	    break;
	case GET_CLASS_INFO:
	    handleGetClassInfo(server);
	    break;
	case NEXT_OBJECT_ID:
	    handleNextObjectId(server);
	    break;
	case CREATE_TRANSACTION:
	    handleCreateTransaction(server);
	    break;
	case PREPARE:
	    handlePrepare(server);
	    break;
	case COMMIT:
	    handleCommit(server);
	    break;
	case PREPARE_AND_COMMIT:
	    handlePrepareAndCommit(server);
	    break;
	case ABORT:
	    handleAbort(server);
	    break;
	default:
	    failure(new IOException("Unknown operation: " + op));
	}
    }

    /* -- Implement methods for the client and server sides -- */

    public long newNodeId() throws IOException {
	out.writeShort(NEW_NODE_ID);
	checkResult();
	return in.readLong();
    }

    private void handleNewNodeId(DataStoreServer server) throws IOException {
	try {
	    long result = server.newNodeId();
	    out.writeBoolean(true);
	    out.writeLong(result);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }

    public long createObject(long tid) throws IOException {
	out.writeShort(CREATE_OBJECT);
	out.writeLong(tid);
	checkResult();
	return in.readLong();
    }

    private void handleCreateObject(DataStoreServer server)
	throws IOException
    {
	try {
	    long tid = in.readLong();
	    long result = server.createObject(tid);
	    out.writeBoolean(true);
	    out.writeLong(result);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }

    public void markForUpdate(long tid, long oid) throws IOException {
	out.writeShort(MARK_FOR_UPDATE);
	out.writeLong(tid);
	out.writeLong(oid);
	checkResult();
    }

    private void handleMarkForUpdate(DataStoreServer server)
	throws IOException
    {
	try {
	    long tid = in.readLong();
	    long oid = in.readLong();
	    server.markForUpdate(tid, oid);
	    out.writeBoolean(true);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }

    public byte[] getObject(long tid, long oid, boolean forUpdate)
	throws IOException
    {
	out.writeShort(GET_OBJECT);
	out.writeLong(tid);
	out.writeLong(oid);
	out.writeBoolean(forUpdate);
	checkResult();
	return readBytes(in);
    }

    private void handleGetObject(DataStoreServer server) throws IOException {
	try {
	    long tid = in.readLong();
	    long oid = in.readLong();
	    boolean forUpdate = in.readBoolean();
	    byte[] result = server.getObject(tid, oid, forUpdate);
	    out.writeBoolean(true);
	    writeBytes(result, out);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }

    public void setObject(long tid, long oid, byte[] data)
	throws IOException
    {
	out.writeShort(SET_OBJECT);
	out.writeLong(tid);
	out.writeLong(oid);
	writeBytes(data, out);
	checkResult();
    }

    private void handleSetObject(DataStoreServer server) throws IOException {
	try {
	    long tid = in.readLong();
	    long oid = in.readLong();
	    byte[] data = readBytes(in);
	    server.setObject(tid, oid, data);
	    out.writeBoolean(true);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }

    public void setObjects(long tid, long[] oids, byte[][] dataArray)
	throws IOException
    {
	out.writeShort(SET_OBJECTS);
	out.writeLong(tid);
	writeLongs(oids, out);
	out.writeInt(dataArray.length);
	for (int i = 0; i < dataArray.length; i++) {
	    writeBytes(dataArray[i], out);
	}
	checkResult();
    }

    private void handleSetObjects(DataStoreServer server) throws IOException {
	try {
	    long tid = in.readLong();
	    long[] oids = readLongs(in);
	    int dataArrayLength = in.readInt();
	    byte[][] dataArray = new byte[dataArrayLength][];
	    for (int i = 0; i < dataArrayLength; i++) {
		dataArray[i] = readBytes(in);
	    }
	    server.setObjects(tid, oids, dataArray);
	    out.writeBoolean(true);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }

    public void removeObject(long tid, long oid) throws IOException {
	out.writeShort(REMOVE_OBJECT);
	out.writeLong(tid);
	out.writeLong(oid);
	checkResult();
    }

    private void handleRemoveObject(DataStoreServer server)
	throws IOException
    {
	try {
	    long tid = in.readLong();
	    long oid = in.readLong();
	    server.removeObject(tid, oid);
	    out.writeBoolean(true);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }

    public BindingValue getBinding(long tid, String name) throws IOException {
	out.writeShort(GET_BINDING);
	out.writeLong(tid);
	writeString(name, out);
	checkResult();
	return readBindingValue();
    }

    private void handleGetBinding(DataStoreServer server) throws IOException {
	try {
	    long tid = in.readLong();
	    String name = readString(in);
	    BindingValue result = server.getBinding(tid, name);
	    out.writeBoolean(true);
	    writeBindingValue(result);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }

    public BindingValue setBinding(long tid, String name, long oid)
	throws IOException
    {
	out.writeShort(SET_BINDING);
	out.writeLong(tid);
	writeString(name, out);
	out.writeLong(oid);
	checkResult();
	return readBindingValue();
    }

    private void handleSetBinding(DataStoreServer server) throws IOException {
	try {
	    long tid = in.readLong();
	    String name = readString(in);
	    long oid = in.readLong();
	    BindingValue result = server.setBinding(tid, name, oid);
	    out.writeBoolean(true);
	    writeBindingValue(result);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }

    public BindingValue removeBinding(long tid, String name)
	throws IOException
    {
	out.writeShort(REMOVE_BINDING);
	out.writeLong(tid);
	writeString(name, out);
	checkResult();
	return readBindingValue();
    }

    private void handleRemoveBinding(DataStoreServer server)
	throws IOException
    {
	try {
	    long tid = in.readLong();
	    String name = readString(in);
	    BindingValue result = server.removeBinding(tid, name);
	    out.writeBoolean(true);
	    writeBindingValue(result);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }

    public String nextBoundName(long tid, String name) throws IOException {
	out.writeShort(NEXT_BOUND_NAME);
	out.writeLong(tid);
	writeString(name, out);
	checkResult();
	return readString(in);
    }

    private void handleNextBoundName(DataStoreServer server)
	throws IOException
    {
	try {
	    long tid = in.readLong();
	    String name = readString(in);
	    String result = server.nextBoundName(tid, name);
	    out.writeBoolean(true);
	    writeString(result, out);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }

    public int getClassId(long tid, byte[] classInfo) throws IOException {
	out.writeShort(GET_CLASS_ID);
	out.writeLong(tid);
	writeBytes(classInfo, out);
	checkResult();
	return in.readInt();
    }

    private void handleGetClassId(DataStoreServer server)
	throws IOException
    {
	try {
	    long tid = in.readLong();
	    byte[] classInfo = readBytes(in);
	    int result = server.getClassId(tid, classInfo);
	    out.writeBoolean(true);
	    out.writeInt(result);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }

    public byte[] getClassInfo(long tid, int classId) throws IOException {
	out.writeShort(GET_CLASS_INFO);
	out.writeLong(tid);
	out.writeInt(classId);
	checkResult();
	return readBytes(in);
    }

    private void handleGetClassInfo(DataStoreServer server)
	throws IOException
    {
	try {
	    long tid = in.readLong();
	    int classId = in.readInt();
	    byte[] result = server.getClassInfo(tid, classId);
	    out.writeBoolean(true);
	    writeBytes(result, out);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }

    public long nextObjectId(long tid, long oid) throws IOException {
	out.writeShort(NEXT_OBJECT_ID);
	out.writeLong(tid);
	out.writeLong(oid);
	checkResult();
	return in.readLong();
    }

    private void handleNextObjectId(DataStoreServer server)
	throws IOException
    {
	try {
	    long tid = in.readLong();
	    long oid = in.readLong();
	    long result = server.nextObjectId(tid, oid);
	    out.writeBoolean(true);
	    out.writeLong(result);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }

    public long createTransaction(long timeout) throws IOException {
	out.writeShort(CREATE_TRANSACTION);
	out.writeLong(timeout);
	checkResult();
	return in.readLong();
    }

    private void handleCreateTransaction(DataStoreServer server)
	throws IOException
    {
	try {
	    long timeout = in.readLong();
	    long tid = server.createTransaction(timeout);
	    out.writeBoolean(true);
	    out.writeLong(tid);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }		

    public boolean prepare(long tid) throws IOException {
	out.writeShort(PREPARE);
	out.writeLong(tid);
	checkResult();
	return in.readBoolean();
    }

    private void handlePrepare(DataStoreServer server) throws IOException {
	try {
	    long tid = in.readLong();
	    boolean result = server.prepare(tid);
	    out.writeBoolean(true);
	    out.writeBoolean(result);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }

    public void commit(long tid) throws IOException {
	out.writeShort(COMMIT);
	out.writeLong(tid);
	checkResult();
    }

    private void handleCommit(DataStoreServer server) throws IOException {
	try {
	    long tid = in.readLong();
	    server.commit(tid);
	    out.writeBoolean(true);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }

    public void prepareAndCommit(long tid) throws IOException {
	out.writeShort(PREPARE_AND_COMMIT);
	out.writeLong(tid);
	checkResult();
    }

    private void handlePrepareAndCommit(DataStoreServer server)
	throws IOException
    {
	try {
	    long tid = in.readLong();
	    server.prepareAndCommit(tid);
	    out.writeBoolean(true);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }

    public void abort(long tid) throws IOException {
	out.writeShort(ABORT);
	out.writeLong(tid);
	checkResult();
    }

    private void handleAbort(DataStoreServer server) throws IOException {
	try {
	    long tid = in.readLong();
	    server.abort(tid);
	    out.writeBoolean(true);
	    out.flush();
	} catch (Throwable t) {
	    failure(t);
	}
    }

    /* -- Other methods -- */

    /**
     * Flush output, read the success value, and throw an exception if the
     * method call failed.
     */
    private void checkResult() throws IOException {
	out.flush();
	boolean ok = in.readBoolean();
	if (ok) {
	    return;
	}
	String className = readString(in);
	String message = readString(in);
	Throwable exception;
	try {
	    Class<? extends Throwable> exceptionClass =
		Class.forName(className).asSubclass(Throwable.class);
	    Constructor<? extends Throwable> constructor
		= exceptionClass.getConstructor(String.class);
	    exception = constructor.newInstance(message);
	} catch (Exception e) {
	    IOException ioe = new IOException(
		"Problem deserializing exception: " + e);
	    ioe.initCause(e);
	    throw ioe;
	}
	if (exception instanceof IOException) {
	    throw (IOException) exception;
	} else if (exception instanceof RuntimeException) {
	    throw (RuntimeException) exception;
	} else {
	    throw (Error) exception;
	}
    }

    /**
     * Write the failure value, and the name and message for the throwable, and
     * flush output.
     */
    private void failure(Throwable t) throws IOException {
	out.writeBoolean(false);
	writeString(t.getClass().getName(), out);
	writeString(t.getMessage(), out);
	out.flush();
    }

    /** Read a BindingValue from input. */
    private BindingValue readBindingValue() throws IOException {
	return new BindingValue(in.readLong(), readString(in));
    }

    /** Write a BindingValue to output. */
    private void writeBindingValue(BindingValue result) throws IOException {
	out.writeLong(result.getObjectId());
	writeString(result.getNextName(), out);
    }
}
