package com.sun.sgs.nio.channels;

/**
 * Unchecked exception received when a channel operation is attempted that
 * has previously completed due to a timeout.
 * <p>
 * [[Not specified by JSR-203, but referenced in the JSR-203 Javadoc]]
 */
public class IllegalChannelStateException
    extends IllegalStateException
{
    /** The version of the serialized representation of this class. */
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an instance of this class.
     */
    public IllegalChannelStateException() {
        super();
    }
}