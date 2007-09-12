/*
 * Copyright (c) 2007, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.sun.sgs.client;

/**
 * Identifies a session between client and server.
 * <p>
 * Session identifiers are constant; their values cannot be changed
 * after they are created.
 */
public abstract class SessionId {

    /**
     * Enables construction of a session identifier (for subclasses).
     */
    protected SessionId() {
    }

    /**
     * Returns a session identifier whose representation is contained in the
     * specified byte array.
     *
     * @param id a byte array containing a session identifier
     * @return a session identifier
     * @throws IllegalArgumentException if the specified byte array does not
     *         contain a valid representation of a {@code SessionId}
     */
    public static SessionId fromBytes(byte[] id) {
        return new com.sun.sgs.impl.client.simple.SimpleSessionId(id);
    }

    /**
     * Returns a byte array containing the representation of this session
     * identifier.
     * <p>
     * The returned byte array must not be modified; if the byte array
     * is modified, the client framework may behave unpredictably.
     *
     * @return a byte array containing the representation of this
     *         session identifier
     */
    public abstract byte[] toBytes();

    /**
     * Compares this session identifier to the specified object.
     * The result is {@code true} if and only if the argument is not
     * {@code null} and is a {@code SessionId} object that represents
     * the same session identifier as this object.
     *
     * @param obj the object to compare this {@code SessionId} against
     *
     * @return {@code true} if the given object represents a 
     *         {@code SessionId} equivalent to this session identifier,
     *         {@code false} otherwise
     */
    @Override
    public abstract boolean equals(Object obj);

    /**
     * Returns a hash code for this session identifier.
     *
     * @return a hash code value for this object
     * 
     * @see Object#hashCode()
     */
    @Override
    public abstract int hashCode();

}
