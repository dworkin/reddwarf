/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.protocol;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;

/**
 * Protocol additions for suspending and resuming messages to a client
 * session and relocating a client session to another node.
 */
public interface SessionRelocationProtocol extends SessionProtocol {

    /**
     * Notifies the associated client to suspend sending messages to the
     * server until {@link #resume resume} is invoked.  This method must
     * notify the {@code completionHandler} when messages have been
     * suspended.  Messages received by the {@link SessionProtocolHandler}
     * will be received and processed until the {@code completionHandler}'s
     * {@link RequestCompletionHandler#completed completed} method is
     * invoked.  If messages are not suspended in a timely fashion (i.e,
     * the {@code completionHandler} is not notified), then the server may
     * disconnect this session.<p>
     *
     * Only session messages that have their completion handlers notified
     * before the specified {@code completionHandler} is notified are
     * guaranteed to be processed by the server. <p>
     *
     * Once this method is invoked, an invocation on a method that sends a
     * message to the client should throw {@link IllegalStateException}
     * until messages are resumed.
     *
     * @param	completionHandler a completion handler
     * @throws	IOException if an I/O error occurs
     */
    void suspend(RequestCompletionHandler<Void> completionHandler)
	throws IOException;

    /**
     * Notifies the associated client to resume sending messages to the
     * server.  If messages were not previously suspended, the method is
     * not required to take action.
     *
     * @throws	IOException if an I/O error occurs
     */
    void resume() throws IOException;
    
    /**
     * Notifies the associated client to relocate its session to the node
     * specified by the {@code descriptors} using the given {@code
     * relocationKey}.<p>
     *
     * The associated client session can be reestablished on the new node
     * by notifying the {@link ProtocolListener} of this protocol's
     * corresponding {@link ProtocolAcceptor} on the new node.  The {@link
     * ProtocolListener#relocatedSession ProtocolListener.relocatedSession}
     * method can be invoked on the new node with the given relocation key
     * to reestablish the client session without having to log in again.<p>
     *
     * Once this method is invoked, an invocation on a method that sends a
     * message to the client should throw {@link IllegalStateException}.
     * Additionally, the client should close any underlying local
     * connection(s) in a timely fashion.
     *
     * @param	descriptors protocol descriptors for {@code newNode}
     * @param	relocationKey the key to be supplied to the new node
     * @param	completionHandler a completion handler
     * @throws	IllegalStateException if the associated session is not
     *		suspended or is already relocating 
     * @throws	IOException if an I/O error occurs
     */
    void relocate(Set<ProtocolDescriptor> descriptors,
		  ByteBuffer relocationKey,
		  RequestCompletionHandler<Void> completionHandler)
	throws IOException;
}
