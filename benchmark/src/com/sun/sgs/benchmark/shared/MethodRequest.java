/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.shared;

import java.io.Serializable;

/**
 * Interface for all method requests sent between client and server in the
 * benchmark application.  Method requests must support the specification of
 * method arguments both via full {@code Object} parameters, which will be
 * serialized by the MethodRequest object, and via {@code byte} array
 * parameters, in case the caller wishes to perform their own parameter
 * serialization.
 *
 * @see com.sun.sgs.benchmark.server.TaskFactory
 * @see com.sun.sgs.benchmark.app.BehaviorModule
 */
public interface MethodRequest extends Serializable {
    /**
     * Returns the arguments with which this method request should be invoked,
     * in {@code byte} array format.
     *
     * @return the arguments with which the method request should be invoked
     *
     * @throws IllegalStateException if this MethodRequest was created with an
     *         {@code Object} array of arguments instead of a {@code byte} array
     *         of arguments.
     */
    byte[] getByteArgs();

    /**
     * Returns the name of the requested method.
     *
     * @return the name of method that should be invoked
     */
    String getMethodName();

    /**
     * Returns the {@code Object} arguments with which this method
     * request should be invoked.
     *
     * @return the arguments with which the method request should be invoked
     *
     * @throws IllegalStateException if this MethodRequest was created with a
     *         {@code byte} array of arguments instead of an {@code Object}
     *         array of arguments.
     */
    Object[] getObjectArgs();

    /**
     * Returns whether this method request was created with {@code Object}
     * arguments or {@code byte} array arguments.
     *
     * @return {@code true} if this method request was created with
     *         {@code Object} arguments instead of {@code byte} array arguments.
     */
    boolean hasObjectArgs();
}
