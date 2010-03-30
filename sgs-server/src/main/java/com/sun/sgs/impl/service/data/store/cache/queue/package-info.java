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

/**
 * Provides facilities to allow the {@link
 * com.sun.sgs.impl.service.data.store.cache.CachingDataStore} to send updates
 * to the {@link
 * com.sun.sgs.impl.service.data.store.cache.server.CachingDataStoreServerImpl},
 * preserving the order of the updates in the face of network errors. <p>
 *
 * Two layers of facilities are provided. <p>
 *
 * The lower layer supports sending an ordered stream of arbitrary requests,
 * insuring that each request is processed exactly once on the server.  The
 * {@link com.sun.sgs.impl.service.data.store.cache.queue.RequestQueueClient}
 * class represents the sending side of the communications mechanism, an
 * instance of which is created on each application node.  The central server
 * creates a {@link
 * com.sun.sgs.impl.service.data.store.cache.queue.RequestQueueListener}, which
 * listens for client connection requests and creates instances of {@link
 * com.sun.sgs.impl.service.data.store.cache.queue.RequestQueueServer} to
 * service them.  The requests themselves need to implement the {@link
 * com.sun.sgs.impl.service.data.store.cache.queue.Request} interface. <p>
 *
 * The upper layer consists of facilities to transmit the specific messages
 * required by the caching data store.  The {@link
 * com.sun.sgs.impl.service.data.store.cache.queue.UpdateQueue} represents the
 * sending side at this level, also created on each node.  This class maintains
 * its own queue of the requests associated with particular transactions as
 * part of the mechanism to insure that transactions are committed in order and
 * locks are not released until the transactions in which they were used have
 * committed.  The {@link
 * com.sun.sgs.impl.service.data.store.cache.queue.UpdateQueueServer}
 * represents the server-side implementation of this level, and the {@link
 * com.sun.sgs.impl.service.data.store.cache.queue.UpdateQueueRequest} class
 * represents the requests themselves.
 */
package com.sun.sgs.impl.service.data.store.cache.queue;
