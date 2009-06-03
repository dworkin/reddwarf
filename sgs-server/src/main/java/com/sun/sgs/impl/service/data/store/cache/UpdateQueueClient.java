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

import java.io.IOException;
import java.util.Properties;

class UpdateQueueClient implements UpdateQueue {
    private final RequestQueueClient queue;

    UpdateQueueClient(long nodeId, String host, int port) throws IOException {
	queue = new RequestQueueClient(
	    nodeId,
	    new RequestQueueClient.BasicSocketFactory(host, port),
	    new Runnable() {
		public void run() { 
		    /* FIXME: Handle connection failure */
		}
	    },
	    new Properties());
	queue.start();
    }

    /* -- Implement UpdateQueue -- */

    public void commit(
	long[] oids, byte[][] oidValues, String[] names, long[] nameValues)
    {
	queue.addRequest(
	    new UpdateQueueRequest.Commit(oids, oidValues, names, nameValues));
    }

    public void evictObject(long oid, CompletionHandler<Long> handler) {
	queue.addRequest(new UpdateQueueRequest.EvictObject(oid, handler));
    }

    public void downgradeObject(long oid, CompletionHandler<Long> handler) {
	queue.addRequest(new UpdateQueueRequest.DowngradeObject(oid, handler));
    }

    public void evictBinding(String name, CompletionHandler<String> handler) {
	queue.addRequest(new UpdateQueueRequest.EvictBinding(name, handler));
    }

    public void downgradeBinding(
	String name, CompletionHandler<String> handler)
    {
	queue.addRequest(
	    new UpdateQueueRequest.DowngradeBinding(name, handler));
    }

    /* -- Other methods -- */

    void shutdown() {
	queue.shutdown();
    }
}
