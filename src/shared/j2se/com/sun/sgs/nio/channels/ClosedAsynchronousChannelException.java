package com.sun.sgs.nio.channels;

/**
 * Unchecked exception thrown when an attempt is made to initiate an
 * asynchronous operation on an asynchronous channel that is closed.
 */
public class ClosedAsynchronousChannelException
    extends IllegalStateException
{
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an instance of this class.
     */
    public ClosedAsynchronousChannelException() {
        super();
    }
}
