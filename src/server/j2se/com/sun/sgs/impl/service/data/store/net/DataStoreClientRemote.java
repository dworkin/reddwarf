/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store.net;

import java.io.IOException;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The client side of an experimental network protocol for implementing
 * DataStoreServer using sockets instead of RMI.
 */
/*
 * XXX: Limit connections and/or close unused connections?
 * XXX: Reap the handler queue?
 * XXX: Close and not return sockets on IOException?
 * FIXME: Send or check version information on initial connection
 */
class DataStoreClientRemote implements DataStoreServer {

    /** The server host name. */
    private final String host;

    /** The server network port. */
    private final int port;

    /** A concurrent collection of objects for handling outgoing calls. */
    private final Queue<DataStoreProtocol> handlers =
	new ConcurrentLinkedQueue<DataStoreProtocol>();

    /** Creates an instance for the specified host and port */
    DataStoreClientRemote(String host, int port) {
	this.host = host;
	this.port = port;
    }

    /** Gets a free handler to use for making an outgoing call. */
    private DataStoreProtocol getHandler() throws IOException {
	DataStoreProtocol h = handlers.poll();
	if (h != null) {
	    return h;
	}
	Socket socket = new Socket(host, port);
	try {
	    socket.setTcpNoDelay(true);
	} catch (Exception e) {
	}
	try {
	    socket.setKeepAlive(true);
	} catch (Exception e) {
	}
	return new DataStoreProtocol(
	    socket.getInputStream(), socket.getOutputStream());
    }

    /** Returns a handler that is no longer in use. */
    private void returnHandler(DataStoreProtocol h) {
	handlers.offer(h);
    }

    /* -- Implement DataStoreServer -- */

    public long allocateObjects(int count) throws IOException {
	DataStoreProtocol h = getHandler();
	try {
	    return h.allocateObjects(count);
	} finally {
	    returnHandler(h);
	}
    }

    public void markForUpdate(long tid, long oid) throws IOException {
	DataStoreProtocol h = getHandler();
	try {
	    h.markForUpdate(tid, oid);
	} finally {
	    returnHandler(h);
	}
    }

    public byte[] getObject(long tid, long oid, boolean forUpdate)
	throws IOException
    {
	DataStoreProtocol h = getHandler();
	try {
	    return h.getObject(tid, oid, forUpdate);
	} finally {
	    returnHandler(h);
	}
    }

    public void setObject(long tid, long oid, byte[] data) throws IOException {
	DataStoreProtocol h = getHandler();
	try {
	    h.setObject(tid, oid, data);
	} finally {
	    returnHandler(h);
	}
    }

    public void setObjects(long tid, long[] oids, byte[][] dataArray)
	throws IOException
    {
	DataStoreProtocol h = getHandler();
	try {
	    h.setObjects(tid, oids, dataArray);
	} finally {
	    returnHandler(h);
	}
    }

    public void removeObject(long tid, long oid) throws IOException {
	DataStoreProtocol h = getHandler();
	try {
	    h.removeObject(tid, oid);
	} finally {
	    returnHandler(h);
	}
    }

    public long getBinding(long tid, String name) throws IOException {
	DataStoreProtocol h = getHandler();
	try {
	    return h.getBinding(tid, name);
	} finally {
	    returnHandler(h);
	}
    }

    public void setBinding(long tid, String name, long oid)
	throws IOException
    {
	DataStoreProtocol h = getHandler();
	try {
	    h.setBinding(tid, name, oid);
	} finally {
	    returnHandler(h);
	}
    }

    public void removeBinding(long tid, String name) throws IOException {
	DataStoreProtocol h = getHandler();
	try {
	    h.removeBinding(tid, name);
	} finally {
	    returnHandler(h);
	}
    }

    public String nextBoundName(long tid, String name) throws IOException {
	DataStoreProtocol h = getHandler();
	try {
	    return h.nextBoundName(tid, name);
	} finally {
	    returnHandler(h);
	}
    }

    public long createTransaction() throws IOException {
	DataStoreProtocol h = getHandler();
	try {
	    return h.createTransaction();
	} finally {
	    returnHandler(h);
	}
    }

    public boolean prepare(long tid) throws IOException {
	DataStoreProtocol h = getHandler();
	try {
	    return h.prepare(tid);
	} finally {
	    returnHandler(h);
	}
    }

    public void commit(long tid) throws IOException {
	DataStoreProtocol h = getHandler();
	try {
	    h.commit(tid);
	} finally {
	    returnHandler(h);
	}
    }

    public void prepareAndCommit(long tid) throws IOException {
	DataStoreProtocol h = getHandler();
	try {
	    h.prepareAndCommit(tid);
	} finally {
	    returnHandler(h);
	}
    }

    public void abort(long tid) throws IOException {
	DataStoreProtocol h = getHandler();
	try {
	    h.abort(tid);
	} finally {
	    returnHandler(h);
	}
    }
}
