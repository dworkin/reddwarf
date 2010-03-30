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
 * Provides an implementation of {@link com.sun.sgs.service.store.DataStore}
 * that caches data on each application node. <p>
 *
 * Each application node runs an instance of the {@link
 * com.sun.sgs.impl.service.data.store.cache.CachingDataStore} class, which is
 * responsible for obtaining and caching information about objects and name
 * bindings.  The caches on the various nodes communicate with a central {@link
 * com.sun.sgs.impl.service.data.store.cache.server.CachingDataStoreServer},
 * implemented by {@link
 * com.sun.sgs.impl.service.data.store.cache.server.CachingDataStoreServerImpl},
 * which responds to requests from the caches, supplying the requested object
 * and name binding data.  The {@code CachingDataStore} also provides a {@link
 * com.sun.sgs.impl.service.data.store.cache.CallbackServer}, which the central
 * server contacts to request that nodes return lock ownership of cached
 * entries that have been requested by other nodes. <p>
 *
 * Both the caches and the central server use classes in the {@link
 * com.sun.sgs.impl.service.data.store.cache.queue queue} subpackage to permit
 * the caches to send updates to the central server, preserving the order of
 * the updates in the face of network errors. <p>
 *
 * Both the cache and central server implementations use a technique called
 * <em>next-key locking</em> to insure the consistency of name bindings.  This
 * technique is used to guarantee that the results of requests related to
 * unbound names and for the next bound name following a particular name will
 * not change because of concurrent modifications by other transactions. <p>
 * 
 * The issue that next-key locking addresses is how to apply locking to an
 * unbound name, or to the next name after a given name, both of which cannot
 * be represented by an existing, bound name.  The next-key locking approach is
 * that all parties wishing to query or modify an unbound name, or the next
 * bound name appearing after a given name, need to lock whatever name is the
 * next bound name found after that name.  A special value is chosen to
 * represent the name that follows all possible legal names. <p>
 *
 * For example, to lock a given name for read, it is sufficient to read lock
 * the name if it is bound, but otherwise the next name needs to be read
 * locked.  Locking that next name will insure that no other transaction can
 * bind the given name, since it, too, will need to lock the next name, in this
 * case for write. <p>
 *
 * Here is a chart of the various operations and the locks they need to obtain:
 *
 * <table border="1">
 * <tr><th>Operation		<th>Name	<th>Next name
 * <tr><td>get (bound)		<td>lock read	<td>
 * <tr><td>get (not bound)	<td>		<td>lock read
 * <tr><td>set (bound)		<td>lock write	<td>
 * <tr><td>set (not bound)	<td>		<td>lock write
 * <tr><td>remove (bound)	<td>lock write	<td>lock write
 * <tr><td>remove (not bound)	<td>		<td>lock read
 * <tr><td>commit (newly bound)	<td>lock write	<td>
 * <tr><td>commit (remove bound)<td>unlock	<td>
 * <tr><td>evict (bound)	<td>unlock	<td>
 * <tr><td>evict (not bound)	<td>		<td>
 * <tr><td>downgrade (bound)	<td>downgrade	<td>
 * <tr><td>downgrade (not bound)<td>		<td>
 * </table> <p>
 *
 * For more information on next-key locking, see Chapter 7.8.5, "Dynamic
 * Key-Range Locks: Previous-Key and Next-Key Locking" in Jim Gray and Andreas
 * Reuter, <i>Transaction Processing: Concepts and Techniques</i> (San Mateo:
 * Morgan Kaufman, 1993), 412-414. <p>
 *
 * The design of the caching data store is summarized in the white paper
 * <a href="http://projectdarkstar.com/wiki/Data_Service_Write_Caching">
 * Data Service Write Caching</a>, available on the Project Darkstar website.
 */
package com.sun.sgs.impl.service.data.store.cache;
