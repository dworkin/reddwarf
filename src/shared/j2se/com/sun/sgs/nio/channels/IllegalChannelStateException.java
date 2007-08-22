package com.sun.sgs.nio.channels;

public class IllegalChannelStateException
    extends IllegalStateException
{
    private static final long serialVersionUID = 1L;

    public IllegalChannelStateException() {
        super();
    }

    public IllegalChannelStateException(String message) {
        super(message);
    }

    public IllegalChannelStateException(Throwable cause) {
        super(cause == null ? null : cause.toString());
        initCause(cause);
    }

    public IllegalChannelStateException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
