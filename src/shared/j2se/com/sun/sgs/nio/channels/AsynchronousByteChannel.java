package com.sun.sgs.nio.channels;

import java.nio.ByteBuffer;
import java.nio.channels.Channel;

public interface AsynchronousByteChannel extends Channel {

    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     */
    <A> IoFuture<Integer, A> read(ByteBuffer dst, A attachment,
        CompletionHandler<Integer, ? super A> handler);

    /**
     * Reads a sequence of bytes from this channel into the given buffer.
     */
    <A> IoFuture<Integer, A> read(ByteBuffer dst,
        CompletionHandler<Integer, ? super A> handler);

    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     */
    <A> IoFuture<Integer, A> write(ByteBuffer src, A attachment,
        CompletionHandler<Integer, ? super A> handler);

    /**
     * Writes a sequence of bytes to this channel from the given buffer.
     */
    <A> IoFuture<Integer, A> write(ByteBuffer src,
        CompletionHandler<Integer, ? super A> handler);
}
