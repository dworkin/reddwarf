package com.sun.sgs.impl.io;

import org.apache.mina.common.ByteBuffer;

public class FilterTestHarness {
    private final CompleteMessageFilter filter;
    private final Callback callback;

    /**
     * Constructs a new {@code FilterTestHarness} with the given
     * callback.
     *
     * @param callback the callback for this harness
     */
    public FilterTestHarness(Callback callback) {
        filter = new CompleteMessageFilter();
        this.callback = callback;
    }

    /**
     * Add the data in {@code buf} to the message filter and process the
     * bytes received so far, dispatching any complete message to the
     * {@linkplain FilterListener#filteredMessageReceived
     * filteredMessageReceived} method of the callback for this harness.
     *
     * @param buf the data to add and process
     */
    public void recv(ByteBuffer buf) {
        filter.filterReceive(callback, buf);
    }

    /**
     * Prepend a 4-byte length field to the given bytes, and pass
     * the result to the {@linkplain FilterListener#sendUnfiltered
     * sendUnfiltered} method of the callback for this harness.
     *
     * @param bytes the data to process
     */
    public void send(byte[] bytes) {
        filter.filterSend(callback, bytes);
    }

    /**
     * Exposes the methods of {@link FilterListener} as public.
     */
    public interface Callback extends FilterListener {
        // empty; simply exposes FilterListener's methods
    }
}
