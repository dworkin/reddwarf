package com.sun.sgs.impl.util;

import com.sun.sgs.service.DataService;
import java.util.HashSet;
import java.util.Set;

/**
 * A utility class for obtaining a set of bound names (matching a
 * prefix) in a data service.
 */
public class BoundNamesUtil {

    /** Prevents instantiation. */
    private BoundNamesUtil() {}

    /**
     * Returns a set containing the service bound names that match the
     * specified {@code prefix} in the specified {@code dataService}.
     *
     * @param	dataService a data service
     * @param	the prefix of service bound names
     * @return	a set containing the matching service bound names
     */
    public static Set<String> getServiceBoundNames(
	DataService dataService, String prefix)
    {
	if (dataService == null || prefix == null) {
	    throw new NullPointerException("null argument");
	}
	
	String key = prefix;
	Set<String> keys = new HashSet<String>();

	for (;;) {
	    key = dataService.nextServiceBoundName(key);

	    if (key == null || !hasPrefix(key, prefix)) {
		break;
	    }

	    keys.add(key);
	}
	return keys;
    }

    /**
     * Returns {@code true} if the given {@code key} has the specified
     * {@code prefix}, and returns {@code false} otherwise.
     */
    private static boolean hasPrefix(String key, String prefix) {
	return key.regionMatches(0, prefix, 0, prefix.length());
    }
}
