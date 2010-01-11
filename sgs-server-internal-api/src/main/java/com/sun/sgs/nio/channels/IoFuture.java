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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
 */

package com.sun.sgs.nio.channels;

import java.nio.channels.AsynchronousCloseException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * A {@code Future} representing the result of an asynchronous I/O
 * operation.
 * <p>
 * In addition to the methods defined by the {@link Future} interface, an
 * {@code IoFuture} allows for the attachment of a single arbitrary object.
 * Where the same {@link CompletionHandler} instance is used to consume the
 * result of several operations then the attachment object can be used to
 * associate application-specific data or context that is required when
 * consuming the result. The attachment object, if any, is specified when
 * initiating the operation. The attachment method is used to retrieve it.
 * The {@link IoFuture#attachment attachment} may later be discarded by
 * {@link IoFuture#attach attaching} {@code null}.
 * 
 * @param <R> the result type
 * @param <A> the attachment type
 */
public interface IoFuture<R, A> extends Future<R> {

    /**
     * Retrieves the result of a completed operation.
     * <p>
     * The method is intended to be invoked from a {@link CompletionHandler}
     * to retrieve the result of a completed operation. It is equivalent to
     * invoking the {@link Future#get() get} method to retrieve the result
     * except that the method does not wait for the result.
     * 
     * @return the completed result
     * @throws ExecutionException if the operation threw an exception
     * @throws CancellationException if the operation was
     *         {@link #cancel(boolean) cancelled}
     * @throws IllegalStateException if the operation has not completed
     */
    R getNow() throws ExecutionException;

    /**
     * Attempts to cancel execution of the operation.
     * <p>
     * If the value of {@code mayInterruptIfRunning} is {@code true} then
     * this method may cancel the operation <i>forcefully</i> by
     * {@link AsynchronousChannel#close() closing} the channel. Whether it
     * does close the channel is implementation and operation specific. If
     * the channel is closed then all outstanding operations on the channel
     * complete by throwing {@link ExecutionException} with cause
     * {@link AsynchronousCloseException}. Where an implementation does not
     * close the channel then the channel may be put into an error state
     * that prevents further operations on the channel. For example, if a
     * read operation is cancelled and the implementation cannot guarantee
     * that no bytes have been read from the channel then it puts the
     * channel into an implementation specific error state. Any subsequent
     * attempt to initiate a read operation on the channel throws an
     * unspecified runtime exception.
     * <p>
     * This method otherwise behaves exactly as specified by the
     * {@link Future} interface.
     * 
     * @param mayInterruptIfRunning {@code true} if the operation can be
     *        cancelled forcefully (possibily by closing the channel),
     *        {@code false} otherwise
     * @return  {@code false} if the operation could not be cancelled,
     *         {@code true} if the operation has been cancelled
     */
    boolean cancel(boolean mayInterruptIfRunning);

    /**
     * Retrieves the current attachment.
     * 
     * @return the object currently attached to this channel future or
     *         {@code null} if there is no attachment
     */
    A attachment();

    /**
     * Attaches the given object to this channel future.
     * <p>
     * An attached object may later be retrieved via the
     * {@link #attachment() attachment} method. Only one object may be
     * attached at a time; invoking this method causes any previous
     * attachment to be discarded. The current attachment may be discarded
     * by attaching {@code null}.
     * 
     * @param ob the object to be attached; may be {@code null}
     * @return the previously-attached object, if any, otherwise
     *         {@code null}
     */
    A attach(A ob);
}
