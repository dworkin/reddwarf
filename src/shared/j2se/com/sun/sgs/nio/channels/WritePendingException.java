package com.sun.sgs.nio.channels;

/**
 * Unchecked exception thrown when an attempt is made to initiate a write
 * operation on a channel and a previous write operation has not completed.
 */
public class WritePendingException
    extends IllegalStateException
{
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an instance of this class.
     */
    public WritePendingException() {
        super();
    }
}
