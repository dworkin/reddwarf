/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.Channel;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.AcceptPendingException;
import com.sun.sgs.nio.channels.AsynchronousChannel;
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ReadPendingException;
import com.sun.sgs.nio.channels.WritePendingException;

/**
 * A token handed out by an {@link AsyncGroupImpl} to support asynchronous
 * IO operations on an underlying {@link SelectableChannel}.
 * <p>
 * It is similar in function to the {@link SelectionKey} in non-blocking
 * reactive IO.
 */
interface AsyncKey
    extends Closeable, Executor
{
    /**
     * Returns the underlying {@link SelectableChannel} for this key.
     * 
     * @return the underlying {@code SelectableChannel} for this key
     */
    SelectableChannel channel();
    
    /**
     * Returns whether the given operation is pending.  An operation
     * is marked as finished (i.e., no longer pending) just before its
     * completion handler is invoked.
     * 
     * @param op a {@link SelectionKey} opcode
     * @return {@code true} if the operation is pending
     */
    boolean isOpPending(int op);
    
    /**
     * Notifies this key that the given IO operations are ready on the
     * underlying channel.  The caller must ensure that interest in these
     * ops has been cleared before invoking this method.
     * 
     * @param readyOps the {@link SelectionKey} ops that are ready
     */
    void selected(int readyOps);

    /**
     * Executes the given command according to the execution policy
     * determined by this key and channel group.  This method supports
     * asynchronous channel operations that cannot be awaited using
     * {@code select()}.
     * 
     * @param command the command to run, possibly in another thread
     */
    void execute(Runnable command);

    /**
     * Invokes the given completion handler, if it is not {@code null};
     * otherwise does nothing.
     * Passes the handler an {@code IoFuture} constructed from the given
     * future and attachment object.
     * 
     * @param <R> the result type
     * @param <A> the attachment type
     * @param handler the completion handler
     * @param attachment the attachment, or {@code null}
     * @param future the result
     */
    <R, A> void
    runCompletion(CompletionHandler<R, A> handler,
                  A attachment,
                  Future<R> future);

    /**
     * Initiates the given operation asynchronously on the underlying
     * channel, and returns a future representing the result.  An appropriate
     * exception is thrown if the channel is closed or the requested operation
     * is already pending.  If a non-{@code null} completion handler is
     * provided, it will be invoked when the operation completes.
     * 
     * @param <R> the result type
     * @param <A> the attachment type
     * @param op a {@link SelectionKey} opcode
     * @param attachment the attachment for the {@code IoFuture}; may be
     *        {@code null}
     * @param handler the completion handler; may be {@code null}
     * @param timeout the timeout for this operation, or {@code 0} to wait
     *        indefinitely
     * @param unit the unit of the timeout
     * @param callable performs an IO operation on the underlying channel
     *        when invoked
     * @return a future representing the result of this operation
     * 
     * @throws ClosedAsynchronousChannelException if the channel is closed
     * @throws ReadPendingException if {@code OP_READ} is requested but
     *         already pending
     * @throws WritePendingException if {@code OP_WRITE} is requested but
     *         already pending
     * @throws ConnectionPendingException {@code OP_CONNECT} is requested
     *         but already pending
     * @throws AcceptPendingException if {@code OP_ACCEPT} is requested but
     *         already pending
     * 
     * @see CompletionHandler
     * @see AsynchronousChannel
     */
    <R, A> IoFuture<R, A>
    execute(int op,
            A attachment,
            CompletionHandler<R, ? super A> handler,
            long timeout,
            TimeUnit unit,
            Callable<R> callable);

    /**
     * Closes this key's underlying channel.  The channel <em>must</em>
     * be closed via this key, not directly.
     * <p>
     * [Description copied from {@link AsynchronousChannel#close()}]
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
    void close() throws IOException;

}
