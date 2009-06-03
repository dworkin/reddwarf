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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 */

package com.sun.sgs.nio.channels;

import java.io.IOException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.Channel;
import java.util.concurrent.ExecutionException;

import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

/**
 * A channel that supports asynchronous operations.
 * <p>
 * Asynchronous channels are non-blocking and define methods to initiate
 * asynchronous operations of the form:
 * <pre>
 *  IoFuture&lt;R,A&gt; operation(... CompletionHandler&lt;R,A&gt; handler)
 * </pre>
 * The returned IoFuture represents the pending result of the asynchronous
 * operation on the channel, and R is the result type returned by the
 * IoFuture's get method.
 * <p>
 * The IoFuture's isDone method can be used to poll the operation to test if
 * it has completed. The IoFuture's get() method can be used wait
 * indefinitely for the result of the operation, and the get(long,TimeUnit)
 * method can be used to wait for at most a given time. If the operation
 * does not complete normally then the get method throws an
 * ExecutionException with the appropriate cause.
 * <p>
 * When an operation on an asynchronous channel completes (successfully or
 * due to error) then a completion handler is invoked to consume the result.
 * The handler is specified when the asynchronous operation is initiated. If
 * a completion handler is not required then it can be specified as null.
 * <p>
 * An asynchronous channel is bound to an asynchronous channel group. The
 * group can be specified when the channel is constructed. If a group is not
 * specified then the channel is bound to a default group. Once a channel is
 * created it remains bound to the group until the channel is closed. This
 * class does not define a method to obtain the group to which the channel
 * is bound. Each group has an associated ExecutorService to which tasks are
 * submitted to handle I/O events and dispatch the results to completion
 * handlers. The completion handler is guaranteed to be invoked by either
 * the thread that initiated the operation or from a task executed by the
 * associated executor service.
 * 
 * <h3>Closing, Cancellation, Timeouts, and Concurrency</h3>
 * <p>
 * Invoking the close method on an asynchronous channel arranges for all
 * outstanding operations on the channel to complete by throwing
 * ExecutionException with cause AsynchronousCloseException.
 * <p>
 * An asynchronous operation is cancelled by invoking the cancel method on
 * the IoFuture representing the result of the operation. It is
 * implementation, channel, and operation specific whether an asynchronous
 * operation can be cancelled. If an operation can be cancelled then the
 * completion handler is invoked and the IoFuture's get method throws
 * CancellationException. If the cancel method is invoked with the
 * mayInterruptIfRunning parameter set to the value true then an
 * implementation may close the channel as if by invoking the close method.
 * In that case then all outstanding operations on the channel complete by
 * throwing ExecutionException with cause AsynchronousCloseException.
 * <p>
 * Some channel implementations may allow a timeout to be specified when
 * initiating an asynchronous operation. If the timeout elapses before the
 * operation completes then the operation completes by throwing
 * ExecutionException with cause AbortedByTimeoutException. Depending on the
 * channel type, a timeout may put the channel into an error state that
 * prevents further operations on the channel.
 * <p>
 * Asynchronous channels are safe for use by multiple concurrent threads.
 * Some channel implementations may support concurrent reading and writing,
 * but may not allow more than one read or write to be outstanding at any
 * given time.
 * <p>
 * Read and write operations on asynchronous channels will typically involve
 * reading into or writing from a ByteBuffer, or a sequence of buffers.
 * Buffers are not safe for use by multiple concurrent threads so care
 * should be taken to not access the buffer until the operation completes.
 */
public abstract class AsynchronousChannel implements Channel {

    /** The provider that created this asynchronous channel. */
    private final AsynchronousChannelProvider provider;

    /**
     * Initializes a new instance of this class.
     *
     * @param provider the provider that created this channel
     */
    protected AsynchronousChannel(AsynchronousChannelProvider provider) {
        if (provider == null) {
            throw new NullPointerException("null provider");
        }
        this.provider = provider;
    }

    /**
     * Returns the provider that created this channel.
     *
     * @return the provider that created this channel
     */
    public final AsynchronousChannelProvider provider() {
        return provider;
    }

    /**
     * Closes this channel.
     * <p>
     * Any outstanding asynchronous operations upon this channel will
     * complete by throwing {@link ExecutionException} with cause
     * {@link AsynchronousCloseException}.
     * <p>
     * This method otherwise behaves exactly as specified by the
     * {@link Channel} interface.
     *
     * @throws IOException if an I/O error occurs
     */
    public abstract void close() throws IOException;

}
