/* 
 * Copyright (c) 2001, Sun Microsystems Laboratories 
 * All rights reserved. 
 * 
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met: 
 * 
 *     Redistributions of source code must retain the 
 *     above copyright notice, this list of conditions 
 *     and the following disclaimer. 
 *             
 *     Redistributions in binary form must reproduce 
 *     the above copyright notice, this list of conditions 
 *     and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution. 
 *             
 *     Neither the name of Sun Microsystems, Inc. nor 
 *     the names of its contributors may be used to endorse 
 *     or promote products derived from this software without 
 *     specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND 
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE. 
 */

/*
 * Scope.java
 */

package com.sun.multicast.allocation;

import java.util.Enumeration;
import java.util.Vector;
import java.util.ResourceBundle;

/**
 * A multicast scope.
 *
 * <P>Objects of this class and all values returned by their methods are
 * immutable. That is, their values cannot change after they are constructed.
 */

// Note that because the default implementation of Clone is used, two
// Scopes may share an AddressRange and names Vector. This should be
// OK, so long as those fields cannot be changed after object construction.

// @@@ Should consider having method to get ScopeName for a given language.
public class Scope implements Cloneable, java.io.Serializable {
    /**
     * Creates a <code>Scope</code> with the specified parameters.
     *
     * @param addresses      the addresses included in the scope
     * @param ttl            the ttl value to be used when transmitting
     *                       on addresses in the scope
     * @param names          an <code>Enumeration</code> of 
     *			     <code>ScopeNames</code>
     * @param defaultName    the <code>ScopeName</code> to be used if no name
     *                       is available in the desired language 
     *			     (null if none).
     *                       This name (if not null) must be in the
     *                       <code>ScopeName</code> <code>Enumeration</code>.
     * @return a <code>Scope</code> with the specified parameters
     */
    public Scope(AddressRange addresses, int ttl, Enumeration names, 
        ScopeName defaultName) {

        addrs = addresses;
        this.ttl = ttl;
        this.names = new Vector();
        boolean foundDefault = (defaultName == null);
        // Create a sorted vector of names (with no duplicates)
        while (names.hasMoreElements()) {
            ScopeName name = (ScopeName) names.nextElement();
	    if (name.equals(defaultName))
		foundDefault = true;
	    add(name);
    	}
	ResourceBundle myResources = ResourceBundle.
	getBundle("com.sun.multicast.allocation.resources.AllocationResources");
	if (!foundDefault) {
	    throw new IllegalArgumentException(
		myResources.getString("defname"));
	}
	this.defaultName = defaultName;
    }

    /**
     * Adds a <code>ScopeName</code> to this
     * <code>Scope</code>. This method is private because
     * it should only be called from the <code>Scope</code>
     * constructor.
     *
     * @param name the <code>ScopeName</code> to add
     */
    private void add(ScopeName name) {
        // Add to sorted vector (with no duplicates)
        int index = find(name);
        if (index < names.size()) {
            ScopeName next = (ScopeName) names.elementAt(index);
            if (name.equals(next))
                return;
        }
        names.insertElementAt(name, index);
    }

    /**
     * Finds the index where a <code>ScopeName</code> belongs
     * in our sorted Vector of <code>ScopeNames</code>. All
     * <code>ScopeNames</code> preceding the returned index
     * will be less than the specified <code>ScopeName</code>.
     * All <code>ScopeNames</code> after the returned index
     * will be greater than or equal to the specified
     * <code>ScopeName</code>.
     *
     * <P>This method is private because it should only be called
     * from the add method.
     *
     * @param name the <code>ScopeName</code> whose proper
     *              location we are going to find
     * @return the index where the <code>ScopeName</code> belongs
     */
    private int find(ScopeName name) {
        // @@@ Should use binary search.
        boolean done = false;
        int i;
        for (i = names.size() - 1; (!done) && (i >= 0); i--) {
            if (name.compareTo(names.elementAt(i)) >= 0)
                done = true;
        }
        return (i + 1);
    }

    /**
     * Gets the addresses included in the scope.
     * @return an <code>AddressRange</code> representing the addresses
     *         included in the scope
     */
    public AddressRange getAddresses() {
        return (addrs);
    }

    /**
     * Gets the ttl value to be used when transmitting
     * on addresses in the scope.
     *
     * @return the ttl value to be used when transmitting
     *         on addresses in the scope
     */
    public int getTTL() {
        return (ttl);
    }

    /**
     * Gets an <code>Enumeration</code> of the names for the scope.
     *
     * @return an <code>Enumeration</code> of <code>ScopeNames</code>
     *         representing the names associated with the scope
     */
    public Enumeration getNames() {
        return (names.elements());
    }

    /**
     * Gets a default <code>ScopeName</code> for the scope.
     *
     * @return a default <code>ScopeName</code> for the scope
     */
    public ScopeName getDefaultName() {
        return (defaultName);
    }

    private int sign(int value) {
        if (value < 0) {
            return (-1);
        } else {
            if (value > 0)
                return (1);
            else
                return (0);
        }
    }

    /**
     * Compares this <code>Scope</code> with the specified
     * object for order. Returns a negative integer, zero, or
     * a positive integer as this object is less than, equal
     * to, or greater than the specified object.
     *
     * <P>If the other object is not a <code>Scope</code>,
     * a <code>ClassCastException</code> is thrown.
     *
     * <P>This method imposes a total ordering on <code>Scopes</code>.
     * <code>Scopes</code> are ordered first by address range, ttl,
     * scope name, and then default name.
     *
     * @param o the <code>Object</code> to compare against
     * @return an integer reflecting the outcome of the comparison
     * @exception ClassCastException if the objects cannot be compared
     */
    public int compareTo(Object o) throws ClassCastException {
        Scope otherScope = (Scope) o;
        int result = addrs.compareTo(otherScope.getAddresses());
        if (result != 0)
            return (result);
        result = sign(ttl - otherScope.getTTL());
        if (result != 0)
            return (result);
        result = compareNames(getNames(), otherScope.getNames());
        if (result != 0)
            return (result);
        return (defaultName.compareTo(otherScope.getDefaultName()));
    }

    /**
     * Compares two sorted <code>Enumerations</code> of <code>ScopeNames</code>
     * for order. Returns a negative integer, zero, or
     * a positive integer as the first object is less than, equal
     * to, or greater than the second object.
     *
     * <P>This method is private because it should only be used by the
     * compareTo method.
     *
     * @param enum1 the first <code>Enumerations</code> of
     *              <code>ScopeNames</code> to compare
     * @param enum2 the second <code>Enumerations</code> of
     *              <code>ScopeNames</code> to compare
     * @return an integer reflecting the outcome of the comparison
     */
    private static int compareNames(Enumeration enum1, Enumeration enum2) {
        while (enum1.hasMoreElements()) {
            if (!enum2.hasMoreElements())
                return (1);
            ScopeName name1 = (ScopeName) enum1.nextElement();
            ScopeName name2 = (ScopeName) enum2.nextElement();
            int result = name1.compareTo(name2);
            if (result != 0)
                return (result);
        }
        if (enum2.hasMoreElements())
            return (-1);
        else
	    return (0);
    }

    /**
     * Indicates whether some other object is "equal to" this one. Two
     * <code>Scopes</code> are equal if and only if their address ranges,
     * ttls, scope names, and default names are all equal.
     *
     * @param obj the object with which to compare
     * @return <code>true</code> if this object is the same as the
     *         reference object, <code>false</code> otherwise.
     */
    public boolean equals(Object obj) {
        if (obj instanceof Scope) {
            Scope otherScope = (Scope) obj;
            return (otherScope.getAddresses().equals(addrs) &&
		(otherScope.getTTL() == ttl) &&
		(compareNames(getNames(), otherScope.getNames()) == 0) &&
		(defaultName.equals(otherScope.getDefaultName())));
	} else
	    return (false);
    }

    /**
     * Returns a hash code value for this object.
     * The hash code values for two <code>Scopes</code> are equal
     * if they are equal. However, it may be possible for two unequal
     * <code>Scopes</code> to have the same hash code.
     *
     * @return a hash code value for this <code>Scope</code>
     */
    public int hashCode() {
        return (addrs.hashCode());
    }

    /**
     * Returns a string representation of this <code>Scope</code>.
     *
     * @return a string representation of this <code>Scope</code>
     */
    public String toString() {
        String output = "Scope with AddressRange:\n";
        output = output + addrs;
        output = output + " TTL " + ttl + "\n";
        output = output + " Names:\n";
        Enumeration enum = getNames();
        while (enum.hasMoreElements()) {
            ScopeName name = (ScopeName) enum.nextElement();
            output = output + name;
        }
        output = output + " Default Name:\n";
        output = output + defaultName;
        return (output);
    }

    /**
     * @serial the addresses included in the scope
     */
    private AddressRange addrs;

    /**
     * @serial the ttl value to be used when transmitting on addresses 
     * in the scope
     */
    private int ttl;

    /**
     * @serial the ScopeNames for the scope
     */
    private Vector names;

    /**
     * @serial the default ScopeName for the scope
     */
    private ScopeName defaultName;
  
    private static ResourceBundle myResources;

}
