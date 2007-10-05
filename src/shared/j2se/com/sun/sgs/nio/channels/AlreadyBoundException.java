package com.sun.sgs.nio.channels;

/**
 * Unchecked exception thrown when an attempt is made to bind the socket of
 * a network oriented channel that is already bound.
 */
public class AlreadyBoundException
    extends IllegalStateException
{
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an instance of this class.
     */
    public AlreadyBoundException() {
        super();
    }
}
