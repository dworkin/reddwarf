/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.client;

import com.sun.sgs.client.ClientChannel;
import com.sun.sgs.client.ClientChannelListener;
import com.sun.sgs.client.SessionId;

public class BenchmarkClientChannelListener
        implements ClientChannelListener
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;

    /** The {@code BenchmarkClient} that is the parent of this frame. */
    private final BenchmarkClient myBenchmarkClient;

    /** The {@code ClientChannel} associated with this frame. */
    private final ClientChannel myChannel;

    /**
     * Constructs a new {@code BenchmarkClientChannelListener} as a wrapper around the given
     * channel.
     *
     * @param client the parent {@code BenchmarkClient} of this frame.
     * @param channel the channel that this class will manage.
     */
    public BenchmarkClientChannelListener(BenchmarkClient client, ClientChannel channel) {
        myBenchmarkClient = client;
        myChannel = channel;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Handles messages received from other clients on this channel,
     * as well as server notifications about other clients joining and
     * leaving this channel.
     */
    public void receivedMessage(ClientChannel channel, SessionId sender,
            byte[] message)
    {
        try {
            String messageString = BenchmarkClient.fromMessageBytes(message);
            System.err.format("Recv on %s from %s: %s\n",
                    channel.getName(), sender, messageString);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * {@inheritDoc}
     */
    public void leftChannel(ClientChannel channel) {
    }
}
