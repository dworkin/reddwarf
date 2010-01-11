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

package com.sun.sgs.impl.nio;

import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * An abstract base class for defining a {@code CompletionHandler} to use when
 * implementing methods that return an {@code IoFuture}, have a {@code
 * CompletionHandler} parameter, and are implemented by making calls to similar
 * methods.  This class is intended to support the interaction between the
 * internal (involving to the delegating function) and external (involving the
 * caller) futures and completion handlers, allowing subclasses supply the
 * desired behavior by customizing the three protected methods: {@link
 * #implStart}, {@link #implCompleted}, and {@link #done}. <p>
 *
 * For example, suppose you wanted to implement a method like {@code
 * AsynchronousByteChannel.read} that delegated to an existing channel, but
 * printed a message before and after reading.  A simple implementation might
 * look like: 
 * <pre>
 * public class PrintReader&lt;A&gt;
 *     extends DelegatingCompletionHandler&lt;Integer, A, Integer, A&gt;
 * {
 *     private final AsynchronousByteChannel channel;
 *     private final ByteBuffer dst;
 * 
 *     public static &lt;A&gt; IoFuture&lt;Integer, A&gt; read(
 *         AsynchronousByteChannel channel, ByteBuffer dst,
 *         A attachment, CompletionHandler&lt;Integer, A&gt; handler)
 *     {
 *         return new PrintReader&lt;A&gt;(channel, dst, 
 *                                         attachment, handler).start();
 *     }
 * 
 *     private PrintReader(AsynchronousByteChannel channel, ByteBuffer dst,
 *                  A attachment, CompletionHandler&lt;Integer, A&gt; handler)
 *     {
 *         super(attachment, handler);
 *         this.channel = channel;
 *         this.dst = dst;
 *     }
 * 
 *     protected IoFuture&lt;Integer, A&gt; implStart() {
 *         System.err.println("Begin reading");
 *         return channel.read(dst, null);
 *     }
 * 
 *     protected IoFuture&lt;Integer, A&gt; implCompleted(
 *                                  IoFuture&lt;Integer, A&gt; result) {
 *         System.err.println("Done reading");
 *         return null;
 *     }
 * }
 * </pre>
 *
 * @param <OR> the result type for the outer handler
 * @param <OA> the attachment type for the outer handler
 * @param <IR> the result type for this handler
 * @param <IA> the attachment type for this handler
 */
public abstract class DelegatingCompletionHandler<OR, OA, IR, IA>
    extends IoFutureTask<OR, OA>
    implements CompletionHandler<IR, IA>
{
    /** The associated outer handler. */
    private final CompletionHandler<OR, OA> outerHandler;

    /** The lock to synchronize on when accessing innerFuture. */
    private final Object lock = new Object();

    /**
     * The inner future associated with the current computation, or {@code
     * null} if the inner computation is not underway.
     */
    private IoFuture<IR, IA> innerFuture = null;

    /**
     * Creates an instance for the specified attachment and handler.
     *
     * @param	outerAttachment the attachment for the outer future; may be
     *		{@code null}
     * @param	outerHandler the handler to notify or {@code null}
     */
    public DelegatingCompletionHandler(
	OA outerAttachment, CompletionHandler<OR, OA> outerHandler)
    {
	/*
	 * We won't be calling {@code run} on this object anyway, so the
	 * callable should not be called.
	 */
	super(new FailingCallable<OR>(), outerAttachment);
	this.outerHandler = outerHandler;
    }

    /* -- Implement CompletionHandler -- */

    /**
     * Invoked when an inner computation has completed.  This method calls
     * {@link #implCompleted}, and calls {@link #setException} on the future if
     * that method throws an exception.
     *
     * @param	innerResult the result of the inner computation
     */
    public final void completed(IoFuture<IR, IA> innerResult) {
	synchronized (lock) {
	    if (!isDone()) {
		try {
		    innerFuture = implCompleted(innerResult);
		    if (innerFuture == null) {
			set(null);
		    }
		} catch (ExecutionException e) {
		    setException(e.getCause());
		} catch (Throwable t) {
		    setException(t);
		}
	    }
	}
    }

    /* -- Other public methods -- */

    /**
     * This method should not be called.
     *
     * @see	#start
     */
    @Override
    public final void run() {
	throw new UnsupportedOperationException(
	    "The run method is not supported");
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation cancels the current future, if any.
     */
    @Override
    public final boolean cancel(boolean mayInterruptIfRunning) {
	synchronized (lock) {
	    if (isDone()) {
		return false;
	    }
	    boolean success = (innerFuture == null)
		? true : innerFuture.cancel(mayInterruptIfRunning);
	    if (success) {
		success = super.cancel(false);
		assert success;
	    }
	    return success;
	}
    }

    /**
     * Starts the computation and returns a future representing the result of
     * the computation.
     *
     * @return	a future representing the result of the computation
     */
    public final IoFuture<OR, OA> start() {
	synchronized (lock) {
	    if (!isDone()) {
		try {
		    innerFuture = implStart();
		    if (innerFuture == null) {
			set(null);
		    }
		} catch (Throwable t) {
		    setException(t);
		}
	    }
	    return this;
	}
    }

    /* -- Protected methods -- */

    /**
     * Starts the computation, returning a future for managing the inner
     * computation or {@code null} to indicate that the computation is
     * completed.  Any exception thrown will terminate the computation.
     *
     * @return	the future or {@code null}
     */
    protected abstract IoFuture<IR, IA> implStart();

    /**
     * Called when the delegated computation completes.  The implementation
     * should return a new future if there is more computation to perform, or
     * else {@code null} to indicate that the operation has completed.
     * Any exception thrown will terminate the computation.  If an {@link
     * ExecutionException} is thrown, then its cause will be used.
     *
     * @param	innerResult the result of the delegated computation
     * @return	a future for managing continued compuation, or {@code null} to
     *		specify that the computation is done
     * @throws	Exception if the computation failed
     */
    protected abstract IoFuture<IR, IA> implCompleted(
	IoFuture<IR, IA> innerResult)
	throws Exception;

    /**
     * Called when the computation is completed, which occurs when {@link
     * #implCompleted} returns {@code null} or throws an exception, or when the
     * outer future is cancelled. <p>
     *
     * This implementation runs the outer completion handler.  Subclasses that
     * override this method should make sure to call this method by calling
     * {@code super.done()}.
     */
    @Override
    protected void done() {
	synchronized (lock) {
	    innerFuture = null;
	    if (outerHandler != null) {
		outerHandler.completed(this);
	    }
	}
    }

    /* -- Private methods and classes -- */

    /** Implements a {@code Callable} that fails if called. */
    private static final class FailingCallable<V> implements Callable<V> {
	FailingCallable() { }
	public V call() {
	    throw new AssertionError();
	}
    }
}
