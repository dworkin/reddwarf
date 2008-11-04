/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.impl.sharedutil.Objects;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;

/**
 * An implementation of {@code ManagedReference} that delegates to a {@link
 * ManagedReferenceImpl} but allows the implementation to be replaced.
 */
final class ManagedReferenceWrapper<T>
    implements ManagedReference<T>, Serializable
{
    /** The logger for this class. */
    private static final LoggerWrapper logger =
	new LoggerWrapper(
	    Logger.getLogger(ManagedReferenceWrapper.class.getName()));

    /** The version of the serialized form. */
    private static final long serialVersionUID = 1;

    /** The object ID. */
    final transient long oid;

    /** The object used to obtain the reference implementation. */
    private transient ContextWrapper contextWrapper;

    /** The reference implementation or {@code null}. */
    private transient ManagedReferenceImpl impl;

    /**
     * The generation number of the context wrapper for which the reference
     * implementation is valid.
     */
    private transient int generation;

    /** The value returned by getId, or null. */
    private transient BigInteger id;

    /**
     * Creates an instance for the associated implementation.
     *
     * @param	impl the implementation
     */
    ManagedReferenceWrapper(ManagedReferenceImpl impl) {
	this(impl, new ContextWrapper(impl.context));
    }

    ManagedReferenceWrapper(ManagedReferenceImpl impl,
			    ContextWrapper contextWrapper)
    {
	this.oid = impl.oid;
	this.contextWrapper = contextWrapper;
	this.impl = impl;
	generation = contextWrapper.getGeneration();	
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation delegates to the underlying reference
     * implementation.
     */
    public T get() {
	if (logger.isLoggable(Level.FINEST)) {
	    logger.log(Level.FINEST, "{0}.get {1}", this, contextWrapper);
	}
	return getImpl().get();
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation delegates to the underlying reference
     * implementation.
     */
    public T getForUpdate() {
	return getImpl().getForUpdate();
    }

    /** {@inheritDoc} */
    public BigInteger getId() {
	if (id == null) {
	    id = BigInteger.valueOf(oid);
	}
	return id;
    }

    /**
     * Returns {@code true} if the argument is either a {@link
     * ManagedReferenceWrapper} or a {@link ManagedReferenceImpl} with the same
     * object ID.
     *
     * @param	object the object to compare
     * @return	whether the argument is equal to this object
     */
    public boolean equals(Object object) {
	if (object == this) {
	    return true;
	} else if (object instanceof ManagedReferenceWrapper) {
	    return oid == ((ManagedReferenceWrapper) object).oid;
	} else if (object instanceof ManagedReferenceImpl) {
	    return oid == ((ManagedReferenceImpl) object).oid;
	} else {
	    return false;
	}
    }

    /**
     * Returns a hash code for this object.
     *
     * @return	the has code for this object.
     */
    public int hashCode() {
	return ManagedReferenceImpl.hashCode(oid);
    }
	
    /**
     * Returns a string representing this object.
     *
     * @return	a string representing this object.
     */
    public String toString() {
	return "ManagedReferenceWrapper@" +
	    Integer.toHexString(System.identityHashCode(this)) +
	    "[oid:" + oid + "]";
    }

    /**
     * Replaces this object with the underlying implementation object during
     * serialization.
     */
    private Object writeReplace() throws ObjectStreamException {
	if (impl != null) {
	    return impl;
	} else {
	    return new ManagedReferenceImpl<Object>(
		contextWrapper.getContext(), oid);
	}
    }

    /**
     * Sets the context wrapper.
     *
     * @param	contextWrapper the contextWrapper
     */
    private void setContextWrapper(ContextWrapper contextWrapper) {
	if (contextWrapper == null) {
	    throw new NullPointerException(
		"The contextWrapper must not be null");
	}
	this.contextWrapper = contextWrapper;
	impl = null;
    }

    /**
     * Returns the underlying implementation, obtaining it from the context as
     * needed.
     *
     * @return	the implementation
     */
    private ManagedReferenceImpl<T> getImpl() {
	int contextGeneration = contextWrapper.getGeneration();
	if (impl == null || generation != contextGeneration) {
	    impl = contextWrapper.getContext().getReference(oid);
	    generation = contextGeneration;
	}
	return Objects.uncheckedCast(impl);
    }
}
