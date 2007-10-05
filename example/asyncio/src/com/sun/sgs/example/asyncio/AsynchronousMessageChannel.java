/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.example.asyncio;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

import com.sun.sgs.impl.nio.AttachedFuture;
import com.sun.sgs.nio.channels.AsynchronousByteChannel;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ReadPendingException;
import com.sun.sgs.nio.channels.WritePendingException;

/**
 * An attempt at making a wrapper channel.
 */
public class AsynchronousMessageChannel implements Channel {

    /**
     * The underlying channel (possibly another layer of abstraction,
     * e.g. compression, retransmission...)
     */
    final AsynchronousByteChannel channel;

    final AtomicBoolean readPending = new AtomicBoolean();
    final AtomicBoolean writePending = new AtomicBoolean();

    private final CompleteMessageDetector detector;

    /**
     * Creates a new instance of this class with the given channel.
     * 
     * @param channel a channel
     * @param detector the CompleteMessageDetector
     */
    public AsynchronousMessageChannel(AsynchronousByteChannel channel,
                                      CompleteMessageDetector detector)
    {
        this.channel = channel;
        this.detector = detector;
    }

    int completeMessageLength(ByteBuffer buf) throws IOException {
        return detector.completeMessageLength(buf);
    }

    /**
     * TODO
     */
    public interface CompleteMessageDetector {
        /**
         * 
         * @param buf the buffer
         * @return the length, or -1
         * 
         * @throws IOException if a problem is found while attempting to
         *                     determine the message length.
         */
        int completeMessageLength(ByteBuffer buf) throws IOException;
    }

    /**
     * TODO
     */
    public static class PartialMessageDetector
        implements CompleteMessageDetector
    {
        /** {@inheritDoc} */
        public int completeMessageLength(ByteBuffer buf) {
            int pos = buf.position();
            return pos > 0 ? pos : -1;
        }
    }

    /**
     * TODO
     */
    public static class PrefixMessageLengthDetector
        implements CompleteMessageDetector
    {
        private final int prefixLength;

        /**
         * @param n the prefix length, in bytes
         */
        public PrefixMessageLengthDetector(int n) {
            if (! (n == 1 || n == 2 || n == 4))
                throw new IllegalArgumentException("bad prefixLength");

            this.prefixLength = n;
        }

        /** {@inheritDoc} */
        public int completeMessageLength(ByteBuffer buf) {
            if (buf.position() >= prefixLength) {
                return peekPrefixLength(buf) + prefixLength;
            }

            // Check that there is room for the prefix in the buffer
            if (buf.limit() < prefixLength)
                throw new BufferOverflowException();

            return -1;
        }

        private int peekPrefixLength(ByteBuffer buf) {
            switch (prefixLength) {
            case 1:
                return buf.get(0) & 0xFF;
            case 2:
                return buf.getShort(0) & 0xFFFF;
            case 4:
                return buf.getInt(0);
            default:
                // Shouldn't be possible
                throw new IllegalStateException("bad prefixLength");
            }
        }
    }

    /**
     * Result is a read-only view into the "dst" buffer containing
     * a complete message as determiend by the MessageDispatcher.
     * @param dst 
     * @param attachment 
     * @param handler 
     * @param <A> 
     * @return an IoFuture
     * @throws ReadPendingException if a read is in progress
     */
    public <A> IoFuture<ByteBuffer, A>
    read(ByteBuffer dst,
         A attachment,
         CompletionHandler<ByteBuffer, ? super A> handler)
    {
        if (! readPending.compareAndSet(false, true))
            throw new ReadPendingException();

        return AttachedFuture.wrap(
            new Reader<A>(dst, attachment, handler), attachment);
    }

    /**
     * @param <A>
     * @param dst
     * @param handler
     * @return an IoFuture
     * @throws ReadPendingException if a read is in progress
     */
    public final <A> IoFuture<ByteBuffer, A>
    read(ByteBuffer dst, CompletionHandler<ByteBuffer, ? super A> handler) {
        return read(dst, null, handler);
    }

    /**
     * @param <A>
     * @param src
     * @param attachment
     * @param handler
     * @return an IoFuture
     * @throws WritePendingException if a write is in progress
     */
    public <A> IoFuture<Void, A>
    write(ByteBuffer src, A attachment,
          CompletionHandler<Void, ? super A> handler)
    {
        if (! writePending.compareAndSet(false, true))
            throw new WritePendingException();

        return AttachedFuture.wrap(
            new Writer<A>(src, attachment, handler), attachment);
    }

    /**
     * @param <A>
     * @param src
     * @param handler
     * @return an IoFuture
     * @throws WritePendingException if a write is in progress
     */
    public final <A> IoFuture<Void, A>
    write(ByteBuffer src, CompletionHandler<Void, ? super A> handler) {
        return write(src, null, handler);
    }

    /** {@inheritDoc} */
    public void close() throws IOException {
        channel.close();
    }

    /** {@inheritDoc} */
    public boolean isOpen() {
        return channel.isOpen();
    }

    // Implementation details

    static final Runnable emptyRunner =
        new Runnable() { public void run() {} };

    static <R, A> Completer<R, A>
    getCompleter(CompletionHandler<R, A> handler, A attachment) {
        if (handler == null)
            return null;
        return new Completer<R, A>(handler, attachment);
    }

    static class Completer<R, A> {
        private CompletionHandler<R, A> handler;
        private A attachment;

        Completer(CompletionHandler<R, A> handler, A attachment) {
            this.handler = handler;
            this.attachment = attachment;
        }

        void run(Future<R> future) {
            final CompletionHandler<R, A> h = handler;
            final A att = attachment;
            handler = null;
            attachment = null;
            h.completed(AttachedFuture.wrap(future, att));
        }
    }

    static abstract class Wrapper<V, R, A>
        extends FutureTask<V>
        implements CompletionHandler<R, A>
    {
        private final Object lock = new Object();
        private final Completer<V, ?> completionRunner;

        // @GuardedBy("lock")
        private IoFuture<R, A> currentFuture;

        <B> Wrapper(A x,
                    B attachment,
                    CompletionHandler<V, ? super B> handler)
        {
            super(emptyRunner, null);

            completionRunner = getCompleter(handler, attachment);

            synchronized (lock) {
                currentFuture = implStart(x);
            }
        }

        /**
         * @param attachment
         * @return the new future, or {@code} null if finished
         */
        abstract protected IoFuture<R, A>
        implStart(A attachment);

        /**
         * @param result
         * @return the new future, or {@code} null if finished
         * @throws Exception
         */
        abstract protected IoFuture<R, A>
        implCompleted(IoFuture<R, A> result) throws Exception;

        /** {@inheritDoc} */
        public void completed(IoFuture<R, A> result) {
            synchronized (lock) {
                try {
                    IoFuture<R, A> nextFuture = implCompleted(result);
                    if (nextFuture != null)
                        currentFuture = nextFuture;
                } catch (ExecutionException e) {
                    setException(e.getCause());
                } catch (Throwable t) {
                    setException(t);
                }
            }
        }

        /** {@inheritDoc} */
        @Override
        protected void done() {
            if (completionRunner != null)
                completionRunner.run(this);
        }

        /** {@inheritDoc} */
        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            synchronized (lock) {
                return currentFuture.cancel(mayInterruptIfRunning) &&
                       super.cancel(false); // always succeeds
            }
        }
    }

    final class Reader<A>
        extends Wrapper<ByteBuffer, Integer, ByteBuffer>
    {
        private int messageLen = -1;

        Reader(ByteBuffer dst,
               A attachment,
               CompletionHandler<ByteBuffer, ? super A> handler)
        {
            super(dst, attachment, handler);
        }

        /** {@inheritDoc} */
        @Override
        protected void done() {
            readPending.set(false);
            super.done();
        }

        /** {@inheritDoc} */
        @Override
        protected IoFuture<Integer, ByteBuffer>
        implStart(ByteBuffer dst) {
            return channel.read(dst, dst, this);
        }

        /** {@inheritDoc} */
        @Override
        protected IoFuture<Integer, ByteBuffer>
        implCompleted(IoFuture<Integer, ByteBuffer> result)
            throws ExecutionException, IOException
        {
            ByteBuffer dst = result.attach(null);
            int bytesRead = result.getNow();

            if (bytesRead < 0) {
                set(null);
                return null;
            }
            
            ByteBuffer readBuf = dst.asReadOnlyBuffer();

            if (messageLen < 0) {
                messageLen = completeMessageLength(readBuf);

                if (messageLen >= 0) {
                    // Ensure that the buffer will hold the complete message
                    if (dst.limit() < messageLen) {
                        throw new BufferOverflowException();
                    }
                } else {
                    // Or at least ensure that the buffer isn't full
                    if (! dst.hasRemaining())
                        throw new BufferOverflowException();
                }
            }

            if (dst.position() >= messageLen) {
                readBuf.rewind().limit(messageLen);
                dst.position(messageLen + 1);
                set(readBuf);
                return null;
            }

            return channel.read(dst, dst, this);
        }
    }

    final class Writer<A>
        extends Wrapper<Void, Integer, ByteBuffer>
    {
        Writer(ByteBuffer src,
               A attachment,
               CompletionHandler<Void, ? super A> handler)
        {
            super(src, attachment, handler);
        }

        /** {@inheritDoc} */
        @Override
        protected void done() {
            try {
                assert writePending.get();
                writePending.set(false);
            } finally {
                super.done();
            }
        }

        /** {@inheritDoc} */
        @Override
        protected IoFuture<Integer, ByteBuffer>
        implStart(ByteBuffer src) {
            return channel.write(src, src, this);
        }

        /** {@inheritDoc} */
        @Override
        protected IoFuture<Integer, ByteBuffer>
        implCompleted(IoFuture<Integer, ByteBuffer> result)
            throws ExecutionException, IOException
        {
            ByteBuffer src = result.attach(null);
            result.getNow();
            if (src.hasRemaining()) {
                // Write some more
                return channel.read(src, src, this);
            } else {
                // Finished
                set(null);
                return null;
            }
        }
    }
}
