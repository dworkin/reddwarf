package com.sun.sgs.impl.service.data.store.net;

import java.io.IOException;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The client side of an network protocol for implementing DataStoreServer
 * using sockets instead of RMI.
 */
/*
 * XXX: Limit connections and/or close unused connections?
 * FIXME: Send version information
 */
class DataStoreClientRemote implements DataStoreServer {
    private final String host;
    private final int port;
    private final Queue<Handler> handlers =
	new ConcurrentLinkedQueue<Handler>();
    DataStoreClientRemote(String host, int port) throws IOException {
	this.host = host;
	this.port = port;
    }
    private Handler getHandler() throws IOException {
	Handler h = handlers.poll();
	if (h != null) {
	    return h;
	}
	return new Handler(host, port);
    }
    private void returnHandler(Handler h) {
	handlers.offer(h);
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
    private static class Handler extends DataStoreProtocol {
	Handler(String host, int port) throws IOException {
	    this(new Socket(host, port));
	}
	private Handler(Socket socket) throws IOException {
	    super(socket.getInputStream(), socket.getOutputStream());
	}
    }
}
