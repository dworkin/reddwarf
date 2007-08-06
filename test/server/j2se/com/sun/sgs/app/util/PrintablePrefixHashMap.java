package com.sun.sgs.app.util;

import java.util.Map;

/**
 * A wrapper class around {@code PrefixHashMap} that exposes the
 * printing methods for examining the internal layout of the tree.
 *
 * @see TestPrefixHashMap
 */
public class PrintablePrefixHashMap<K,V> extends PrefixHashMap<K,V> {

    public PrintablePrefixHashMap() {
	super();
    }

    public PrintablePrefixHashMap(int minConcurrency) {
	super(minConcurrency);
    }

    public PrintablePrefixHashMap(Map<? extends K, ? extends V> m) {
	super(m);
    }

    /**
     * {@inheritDoc}
     */
    public String treeString() {
	return super.treeString();
    }

    /**
     * {@inheritDoc}
     */
    public String treeDiag() {
	return super.treeDiag();
    }

    /**
     * {@inheritDoc}
     */
    public String treeLeaves() {
	return super.treeLeaves();
    }

}