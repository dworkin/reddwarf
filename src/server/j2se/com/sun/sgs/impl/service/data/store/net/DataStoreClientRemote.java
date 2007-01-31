package com.sun.sgs.impl.service.data.store.net;

import java.io.IOException;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;

class DataStoreClientRemote implements DataStoreServer {
    private final String host;
    private final int port;
    private final Queue<Handler> handlers = new LinkedList<Handler>();
    DataStoreClientRemote(String host, int port) throws IOException {
	this.host = host;
	this.port = port;
    }
    private Handler getHandler() throws IOException {
	synchronized (handlers) {
	    Handler h = handlers.poll();
	    if (h != null) {
		return h;
	    }
	}
	return new Handler(host, port);
    }
    private void returnHandler(Handler h) {
	synchronized (handlers) {
	    handlers.offer(h);
	}
    }
    public long allocateObjects(int count) throws IOException {
	Handler h = getHandler();
	try {
	    return h.allocateObjects(count);
	} finally {
	    returnHandler(h);
	}
    }
    public void markForUpdate(long tid, long oid) throws IOException {
	Handler h = getHandler();
	try {
	    h.markForUpdate(tid, oid);
	} finally {
	    returnHandler(h);
	}
    }
    public byte[] getObject(long tid, long oid, boolean forUpdate)
	throws IOException
    {
	Handler h = getHandler();
	try {
	    return h.getObject(tid, oid, forUpdate);
	} finally {
	    returnHandler(h);
	}
    }
    public void setObject(long tid, long oid, byte[] data) throws IOException {
	Handler h = getHandler();
	try {
	    h.setObject(tid, oid, data);
	} finally {
	    returnHandler(h);
	}
    }
    public void setObjects(long tid, long[] oids, byte[][] dataArray)
	throws IOException
    {
	Handler h = getHandler();
	try {
	    h.setObjects(tid, oids, dataArray);
	} finally {
	    returnHandler(h);
	}
    }
    public void removeObject(long tid, long oid) throws IOException {
	Handler h = getHandler();
	try {
	    h.removeObject(tid, oid);
	} finally {
	    returnHandler(h);
	}
    }
    public long getBinding(long tid, String name) throws IOException {
	Handler h = getHandler();
	try {
	    return h.getBinding(tid, name);
	} finally {
	    returnHandler(h);
	}
    }
    public void setBinding(long tid, String name, long oid)
	throws IOException
    {
	Handler h = getHandler();
	try {
	    h.setBinding(tid, name, oid);
	} finally {
	    returnHandler(h);
	}
    }
    public void removeBinding(long tid, String name) throws IOException {
	Handler h = getHandler();
	try {
	    h.removeBinding(tid, name);
	} finally {
	    returnHandler(h);
	}
    }
    public String nextBoundName(long tid, String name) throws IOException {
	Handler h = getHandler();
	try {
	    return h.nextBoundName(tid, name);
	} finally {
	    returnHandler(h);
	}
    }
    public long createTransaction() throws IOException {
	Handler h = getHandler();
	try {
	    return h.createTransaction();
	} finally {
	    returnHandler(h);
	}
    }
    public boolean prepare(long tid) throws IOException {
	Handler h = getHandler();
	try {
	    return h.prepare(tid);
	} finally {
	    returnHandler(h);
	}
    }
    public void commit(long tid) throws IOException {
	Handler h = getHandler();
	try {
	    h.commit(tid);
	} finally {
	    returnHandler(h);
	}
    }
    public void prepareAndCommit(long tid) throws IOException {
	Handler h = getHandler();
	try {
	    h.prepareAndCommit(tid);
	} finally {
	    returnHandler(h);
	}
    }
    public void abort(long tid) throws IOException {
	Handler h = getHandler();
	try {
	    h.abort(tid);
	} finally {
	    returnHandler(h);
	}
    }
    class Handler extends SocketIO implements DataStoreServer {
	Handler(String host, int port) throws IOException {
	    super(new Socket(host, port));
	}
	public long allocateObjects(int count) throws IOException {
	    out.writeInt(DataStoreServerRemote.ALLOCATE_OBJECTS);
	    out.writeInt(count);
	    checkResult();
	    return in.readLong();
	}
	public void markForUpdate(long tid, long oid) throws IOException {
	    out.writeInt(DataStoreServerRemote.MARK_FOR_UPDATE);
	    out.writeLong(tid);
	    out.writeLong(oid);
	    checkResult();
	}
	public byte[] getObject(long tid, long oid, boolean forUpdate)
	    throws IOException
	{
	    out.writeInt(DataStoreServerRemote.GET_OBJECT);
	    out.writeLong(tid);
	    out.writeLong(oid);
	    out.writeBoolean(forUpdate);
	    checkResult();
	    return readBytes();
	}
	public void setObject(long tid, long oid, byte[] data) throws IOException {
	    out.writeInt(DataStoreServerRemote.SET_OBJECT);
	    out.writeLong(tid);
	    out.writeLong(oid);
	    writeBytes(data);
	    checkResult();
	}
	public void setObjects(long tid, long[] oids, byte[][] dataArray)
	    throws IOException
	{
	    out.writeInt(DataStoreServerRemote.SET_OBJECTS);
	    out.writeLong(tid);
	    writeLongs(oids);
	    out.writeInt(dataArray.length);
	    for (int i = 0; i < dataArray.length; i++) {
		writeBytes(dataArray[i]);
	    }
	    checkResult();
	}
	public void removeObject(long tid, long oid) throws IOException {
	    out.writeInt(DataStoreServerRemote.REMOVE_OBJECT);
	    out.writeLong(tid);
	    out.writeLong(oid);
	    checkResult();
	}
	public long getBinding(long tid, String name) throws IOException {
	    out.writeInt(DataStoreServerRemote.GET_BINDING);
	    out.writeLong(tid);
	    writeString(name);
	    checkResult();
	    return in.readLong();
	}
	public void setBinding(long tid, String name, long oid)
	    throws IOException
	{
	    out.writeInt(DataStoreServerRemote.SET_BINDING);
	    out.writeLong(tid);
	    writeString(name);
	    out.writeLong(oid);
	    checkResult();
	}
	public void removeBinding(long tid, String name) throws IOException {
	    out.writeInt(DataStoreServerRemote.REMOVE_BINDING);
	    out.writeLong(tid);
	    writeString(name);
	    checkResult();
	}
	public String nextBoundName(long tid, String name) throws IOException {
	    out.writeInt(DataStoreServerRemote.NEXT_BOUND_NAME);
	    out.writeLong(tid);
	    writeString(name);
	    checkResult();
	    return readString();
	}
	public long createTransaction() throws IOException {
	    out.writeInt(DataStoreServerRemote.CREATE_TRANSACTION);
	    checkResult();
	    return in.readLong();
	}
	public boolean prepare(long tid) throws IOException {
	    out.writeInt(DataStoreServerRemote.PREPARE);
	    out.writeLong(tid);
	    checkResult();
	    return in.readBoolean();
	}
	public void commit(long tid) throws IOException {
	    out.writeInt(DataStoreServerRemote.COMMIT);
	    out.writeLong(tid);
	    checkResult();
	}
	public void prepareAndCommit(long tid) throws IOException {
	    out.writeInt(DataStoreServerRemote.PREPARE_AND_COMMIT);
	    out.writeLong(tid);
	    checkResult();
	}
	public void abort(long tid) throws IOException {
	    out.writeInt(DataStoreServerRemote.ABORT);
	    out.writeLong(tid);
	    checkResult();
	}
    }
}
