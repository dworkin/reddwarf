package com.sun.sgs.nio.channels;

import java.io.IOException;

public class UnsupportedSocketOptionException
    extends IOException
{
    private static final long serialVersionUID = 1L;

    public UnsupportedSocketOptionException() {
        super();
    }

    public UnsupportedSocketOptionException(String message) {
        super(message);
    }

    public UnsupportedSocketOptionException(Throwable cause) {
        super(cause == null ? null : cause.toString());
        initCause(cause);
    }

    public UnsupportedSocketOptionException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
