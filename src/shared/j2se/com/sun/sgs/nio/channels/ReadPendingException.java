package com.sun.sgs.nio.channels;

/**
 * Unchecked exception thrown when an attempt is made to initiate a read
 * operation on a channel and a previous read operation has not completed.
 */
public class ReadPendingException
    extends IllegalStateException
{
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an instance of this class.
     */
    public ReadPendingException() {
        super();
    }
}
