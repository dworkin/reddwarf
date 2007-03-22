package com.sun.sgs.impl.util;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.service.DataService;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * A utility class for obtaining an iterator for a set of bound names
 * (matching a prefix) in a data service.
 */
public final class BoundNamesUtil {

    /** Prevents instantiation. */
    private BoundNamesUtil() {}

    /**
     * Returns an {@code Iterable} that can be used to obtain an
     * {@code Iterator} for the set of service bound names matching
     * the specified {@code prefix} in the specified {@code
     * dataService}.  The returned {@code Iterable} is not serializable.
     *
     * <p>The {@code iterator} method of the returned {@code Iterable}
     * returns the result of invoking {@link
     * #getServiceBoundNamesIterator getServiceBoundNamesIterator}
     * with the specified {@code dataService} and {@code prefix}.
     *
     * @param 	dataService a data service
     * @param	prefix the prefix of service bound names
     * @return 	an {@code Iterable} for the set of service bound names
     * 		matching the {@code prefix}
     * @see #getServiceBoundNamesIterator
     */
    public static Iterable<String> getServiceBoundNamesIterable(
	DataService dataService, String prefix)
    {
	return new BoundNamesIterable(
	    new BoundNamesIterator(dataService, prefix));
    }

    /**
     * Returns an {@code Iterator} for the set of service bound names
     * matching the specified {@code prefix} in the specified {@code
     * dataService}.  The returned {@code Iterator} is not serializable.
     *
     * <p>The {@code remove} method of the returned iterator removes
     * the binding of the last name returned by {@code next} by
     * invoking {@link DataService#removeServiceBinding
     * removeServiceBinding} on the given {@code dataService} passing
     * the name.
     *
     * <p>Note: the {@code remove} method does not remove from the
     * data store the {@link ManagedObject} bound to the name.  If the
     * {@code ManagedObject} needs to be removed, that object should
     * be removed by invoking the {@code dataService}'s {@link
     * DataManager#removeObject removeObject} method passing the
     * {@code ManagedObject} bound to the name.
     *
     * @param 	dataService a data service
     * @param	prefix the prefix of service bound names
     * @return	an {@code Iterator} for the set of service bound names
     * 		matching the {@code prefix}
     */
    public static Iterator<String> getServiceBoundNamesIterator(
	DataService dataService, String prefix)
    {
	return new BoundNamesIterator(dataService, prefix);
    }

    /* -- other classes -- */

    /**
     * An {@code Iterable} that is a conatiner for a {@code
     * BoundNamesIterator}.
     */
    private static class BoundNamesIterable implements Iterable<String> {

	private final Iterator<String> iter;

	BoundNamesIterable(Iterator<String> iter) {
	    this.iter = iter;
	}

	/** {@inheritDoc} */
	public Iterator<String> iterator() { return iter; }
    }

    /**
     * An {@code Iterator} for the set of service bound names matching
     * the {@code prefix} of the {@code dataService} both specified
     * during construction.
     */
    private static class BoundNamesIterator implements Iterator<String> {

	/** The data service. */
	private final DataService dataService;
	/** The prefix for service bound names. */
	private final String prefix;
	/** The key used to look up next service bound name, or null. */
	private String key;
	/** The key returned by {@code next}, or null. */
	private String keyReturnedByNext;
	/** The name fetched in the {@code hasNext} method, which
	 * is only valid if {@code hasNext} returns {@code true}. */
	private String nextName;
	
	BoundNamesIterator(DataService dataService, String prefix) {
	    if (dataService == null || prefix == null) {
		throw new NullPointerException("null argument");
	    }
	    this.dataService = dataService;
	    this.prefix = prefix;
	    this.key = prefix;
	}

	/** {@inheritDoc} */
	public boolean hasNext() {
	    if (key == null) {
		return false;
	    }
	    if (nextName != null) {
		return true;
	    }
	    String name = dataService.nextServiceBoundName(key);
	    if (name != null && name.startsWith(prefix)) {
		nextName = name;
		return true;
	    } else {
		key = null;
		return false;
	    }
	}

	/** {@inheritDoc} */
	public String next() {
	    try {
		if (! hasNext()) {
		    throw new NoSuchElementException();
		}
		keyReturnedByNext = key = nextName;
		return keyReturnedByNext;
	    } finally {
		nextName = null;
	    }
	}

	/** {@inheritDoc} */
	public void remove() {
	    if (keyReturnedByNext == null) {
		throw new IllegalStateException();
	    }

	    dataService.removeServiceBinding(keyReturnedByNext);
	    keyReturnedByNext = null;
	}
    }
}
