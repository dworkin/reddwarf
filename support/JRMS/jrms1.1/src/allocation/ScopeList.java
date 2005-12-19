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
 * ScopeList.java
 */

package com.sun.multicast.allocation;

import java.util.Enumeration;
import java.util.Vector;

/**
 * A multicast scope list.
 *
 * <P>Objects of this class and all values returned by their methods are
 * immutable. That is, their values cannot change after they are constructed.
 */
// Note that because the default implementation of Clone is used, two
// ScopeLists may share scopes Vector. This should be OK, so long as
// this field cannot be changed after object construction.
// @@@ Might want to have a method to fetch a list of only scopes
// the support addresses of a given type. Code could look something
// like this (but not quite):
//    Vector v = new Vector();
//    Enumeration e = scopes.elements();
//    while (e.hasMoreElements()) {
//      Scope scope = e.nextElement();
//      if (scope.getAddresses().getAddressType().equals(addressType))
//        v.addElement(scope);
//    }
//    return (v);
//
public class ScopeList implements Cloneable, java.io.Serializable {
    /**
     * Creates an empty <code>ScopeList</code>.
     *
     * @return a <code>ScopeList</code> containing no <code>Scopes</code>
     */
    public ScopeList() {
        this.scopes = new Vector();
    }

    /**
     * Creates a <code>ScopeList</code> with the specified parameters.
     *
     * @param scopes         an <code>Enumeration</code> of <code>Scopes</code>
     *                       to be included
     * @return a <code>ScopeList</code> with the specified parameters
     */
    public ScopeList(Enumeration scopes) {
        this.scopes = new Vector();
        while (scopes.hasMoreElements())
            add((Scope) scopes.nextElement());
    }

    /**
     * Gets an <code>Enumeration</code> of the scopes in this list.
     *
     * @return an <code>Enumeration</code> of <code>Scopes</code>
     */
    public Enumeration getScopes() {
        return (scopes.elements());
    }

    /**
     * Merges this <code>ScopeList</code> and another one.
     *
     * @param  otherList the other <code>ScopeList</code>
     * @return a new <code>ScopeList</code> representing the
     *         merger of this <code>ScopeList</code> and
     *         <code>otherList</code>.
     */
    ScopeList merge(ScopeList otherList) {
        // Handle the easy cases first.
        if (equals(otherList))
            return (this);
        if (scopes.isEmpty())
            return (otherList);
	Enumeration e = otherList.getScopes();
	if (!e.hasMoreElements())
	    return (this);

	ScopeList sl;
	try {
            sl = (ScopeList) this.clone();
        } catch (CloneNotSupportedException ex) {
            throw new NullPointerException(); // Should never happen
        }
        while (e.hasMoreElements())
            sl.add((Scope) e.nextElement());
        return (sl);
    }

    /**
     * Compares this <code>ScopeList</code> with the specified
     * object for order. Returns a negative integer, zero, or
     * a positive integer as this object is less than, equal
     * to, or greater than the specified object.
     *
     * <P>If the other object is not a <code>ScopeList</code>,
     * a <code>ClassCastException</code> is thrown.
     *
     * <P>This method imposes a total ordering on <code>ScopeLists</code>.
     * <code>ScopeLists</code> are ordered according to the order
     * of the <code>Scopes</code> contained within them.
     *
     * @param o the <code>Object</code> to compare against
     * @return an integer reflecting the outcome of the comparison
     * @exception ClassCastException if the objects cannot be compared
     */
    public int compareTo(Object o) throws ClassCastException {
        ScopeList otherScopeList = (ScopeList) o;
        return (compareScopes(getScopes(), otherScopeList.getScopes()));
    }

    /**
     * Compares two sorted <code>Enumerations</code> of <code>Scopes</code>
     * for order. Returns a negative integer, zero, or
     * a positive integer as the first object is less than, equal
     * to, or greater than the second object.
     *
     * <P>This method is private because it should only be used by the
     * compareTo method.
     *
     * @param enum1 the first <code>Enumeration</code> of
     *              <code>Scopes</code> to compare
     * @param enum2 the second <code>Enumeration</code> of
     *              <code>Scopes</code> to compare
     * @return an integer reflecting the outcome of the comparison
     */
    private static int compareScopes(Enumeration enum1, Enumeration enum2) {
        while (enum1.hasMoreElements()) {
            if (!enum2.hasMoreElements())
                return (1);
            Scope scope1 = (Scope) enum1.nextElement();
            Scope scope2 = (Scope) enum2.nextElement();
            int result = scope1.compareTo(scope2);
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
     * <code>ScopeLists</code> are equal if and only the <code>Scopes</code>
     * in the <code>ScopeLists</code> are all equal.
     *
     * @param obj the object with which to compare
     * @return <code>true</code> if this object is the same as the
     *         reference object, <code>false</code> otherwise.
     */
    public boolean equals(Object obj) {
        if (obj instanceof ScopeList)
            return ((compareTo(obj) == 0));
        else
            return (false);
    }

    /**
     * Returns a hash code value for this object.
     * The hash code values for two <code>ScopeLists</code> are equal
     * if they are equal. However, it may be possible for two unequal
     * <code>ScopeLists</code> to have the same hash code.
     *
     * @return a hash code value for this <code>ScopeList</code>
     */
    public int hashCode() {
        if (scopes.size() == 0)
            return (0);
        else
            return (scopes.firstElement().hashCode());
    }

    /**
     * Adds a <code>Scope</code> to this <code>ScopeList</code>.
     * This method is private because it should only be called
     * from methods of this class. Since ScopeLists are immutable,
     * it's important to only call this before anybody gets a
     * reference to the ScopeList being added to (i.e. during
     * construction or when creating a new ScopeList during a merge).
     *
     * @param scope the <code>Scope</code> to add
     */
    private void add(Scope scope) {
        // Add, removing duplicates
        int index = find(scope);
        if (index < scopes.size()) {
            Scope next = (Scope) scopes.elementAt(index);
            if (scope.equals(next)) {
                return;
            }
        }
        scopes.insertElementAt(scope, index);
    }

    /**
     * Finds the index where a <code>Scope</code> belongs
     * in our sorted Vector of <code>Scopes</code>. All
     * <code>Scopes</code> preceding the returned index
     * will be less than the specified <code>Scope</code>.
     * All <code>Scopes</code> after the returned index
     * will be greater than or equal to the specified
     * <code>Scope</code>.
     *
     * <P>This method is private because it should only be called
     * from the add method.
     *
     * @param scope the <code>Scope</code> whose proper
     *              location we are going to find
     * @return the index where the <code>Scope</code> belongs
     */
    private int find(Scope scope) {
        // @@@ Should use binary search.
        boolean done = false;
        int i;
        for (i = scopes.size() - 1; (!done) && (i >= 0); i--) {
            if (scope.compareTo(scopes.elementAt(i)) >= 0)
            done = true;
        }
        return (i + 1);
    }

    /**
     * Finds a Scope suitable for use with the specified ttl value.
     *
     * @return a Scope suitable for use with the specified ttl value
     *         (null if none available)
     */
    public Scope findScopeForTTL(int ttl) {
        Scope bestYet = null;
        Enumeration enum = getScopes();
        while (enum.hasMoreElements()) {
            Scope scope = (Scope) enum.nextElement();
            if (scope.getTTL() >= ttl) {
	        if ((bestYet == null) || (bestYet.getTTL() > scope.getTTL()))
    	            bestYet = scope;
            }
        }
        return (bestYet);
    }

    /**
     * Returns a string representation of this <code>ScopeList</code>.
     *
     * @return a string representation of this <code>ScopeList</code>
     */
    public String toString() {
        String output = "ScopeList with " + scopes.size() + " scopes:\n";
        Enumeration enum = getScopes();
        while (enum.hasMoreElements()) {
            Scope scope = (Scope) enum.nextElement();
            output = output + scope;
        }
        return (output);
    }

    /**
     * @serial the Scopes included in this ScopeList
     */
    private Vector scopes;

}
