/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.impl.profile.util;

import java.math.BigInteger;


/**
 * A utility that wraps a transaction identifier (an array of bytes) by
 * assuming that the identifier represents a non-negative long value. This
 * is particularly useful for {@code ProfileListener}s that want to key off
 * of transaction identifiers. Each transaction's identifier is available
 * from its associated {@code ProfileReport}.
 */
public final class TransactionId {

    // the non-negative numeric value of the id
    private final long txnId;

    /**
     * Creates an instance of {@code TransactionId}.
     *
     * @param txnId the transaction identifier
     */
    public TransactionId(byte [] txnId) {
        // assert that this really fits into a long
        assert txnId.length <= 8;
        this.txnId = (new BigInteger(1, txnId)).longValue();
    }

    /** {@inheritDoc} */
    public String toString() {
        return String.valueOf(txnId);
    }

    /** {@inheritDoc} */
    public boolean equals(Object o) {
        if ((o == null) || (!(o instanceof TransactionId))) {
            return false;
        }
        return txnId == ((TransactionId) o).txnId;
    }

    /** {@inheritDoc} */
    public int hashCode() {
        // the hash code specified in the javadoc for java.lang.Long
        return (int) (txnId ^ (txnId >>> 32));
    }

}
