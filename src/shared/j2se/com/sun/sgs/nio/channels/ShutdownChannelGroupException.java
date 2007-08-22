package com.sun.sgs.nio.channels;

/**
 * Unchecked exception thrown when an attempt is made to construct a channel
 * in a group that is shutdown.
 */
public class ShutdownChannelGroupException
    extends IllegalStateException
{
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an instance of this class.
     */
    public ShutdownChannelGroupException() {
        super();
    }
}
