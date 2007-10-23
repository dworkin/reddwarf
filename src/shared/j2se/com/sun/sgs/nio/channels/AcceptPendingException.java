package com.sun.sgs.nio.channels;

/**
 * Unchecked exception thrown when an attempt is made to initiate an accept
 * operation on a channel and a previous accept operation has not completed.
 */
public class AcceptPendingException
    extends IllegalStateException
{
    /** The version of the serialized representation of this class. */
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an instance of this class.
     */
    public AcceptPendingException() {
        super();
    }
}
