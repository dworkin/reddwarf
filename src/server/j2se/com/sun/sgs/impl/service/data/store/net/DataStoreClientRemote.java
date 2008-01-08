/*
 * Copyright 2008 Sun Microsystems, Inc.
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

import java.io.IOException;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The client side of an experimental network protocol, not currently used, for
 * implementing DataStoreServer using sockets instead of RMI.
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

    public long allocateObjects(long tid, int count) throws IOException {
	DataStoreProtocol h = getHandler();
	try {
	    return h.allocateObjects(tid, count);
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

    public int getClassId(long tid, byte[] classInfo) throws IOException {
	DataStoreProtocol h = getHandler();
	try {
	    return h.getClassId(tid, classInfo);
	} finally {
	    returnHandler(h);
	}
    }

    public byte[] getClassInfo(long tid, int classId) throws IOException {
	DataStoreProtocol h = getHandler();
	try {
	    return h.getClassInfo(tid, classId);
	} finally {
	    returnHandler(h);
	}
    }

    public long nextObjectId(long tid, long oid) throws IOException {
	DataStoreProtocol h = getHandler();
	try {
	    return h.nextObjectId(tid, oid);
	} finally {
	    returnHandler(h);
	}
    }

    public long createTransaction(long timeout) throws IOException {
	DataStoreProtocol h = getHandler();
	try {
	    return h.createTransaction(timeout);
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
