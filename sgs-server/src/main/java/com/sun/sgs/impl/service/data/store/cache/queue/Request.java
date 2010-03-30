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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * A request to add to a {@link RequestQueueClient} or to be serviced by a
 * {@link RequestQueueServer}.
 */
public interface Request {

    /**
     * Writes the request to the data output stream.  Used by the client to
     * send the request to the server.
     *
     * @param	out the data output stream
     * @throws	IOException if an I/O error occurs
     */
    void writeRequest(DataOutput out) throws IOException;

    /**
     * Notes that a request has been completed.  Used to inform the client that
     * the server has completed the request.
     */
    void completed();

    /**
     * A object for reading and performing requests.  Used by the server to
     * handle requests sent by the client.
     *
     * @param	<R> the type of request
     */
    interface RequestHandler<R extends Request> {

	/**
	 * Reads a request from the data input stream.
	 *
	 * @param	in the data input stream
	 * @return	the request
	 * @throws	IOException if an I/O error occurs
	 */
	R readRequest(DataInput in) throws IOException;

	/**
	 * Performs a request.
	 *
	 * @param	request the request
	 * @throws	Exception if the request fails
	 */
	void performRequest(R request) throws Exception;
    }
}
