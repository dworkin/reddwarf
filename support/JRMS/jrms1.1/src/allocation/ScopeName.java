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
 * ScopeName.java
 */

package com.sun.multicast.allocation;

/**
 * A scope name with an RFC 1766 language tag.
 *
 * <P>Objects of this class and all values returned by their methods are
 * immutable. That is, their values cannot change after they are constructed.
 */
public class ScopeName implements Cloneable, java.io.Serializable {
    /**
     * Creates a <code>ScopeName</code> with the specified parameters.
     *
     * @param name          the scope name
     * @param language      the language tag that goes along with the name
     * @return a <code>ScopeName</code> with the specified parameters
     */
    public ScopeName(String name, String language) {
        this.name = name;
        lang = language;
    }

    /**
     * Gets the scope name.
     * @return the scope name
     */
    public String getName() {
        return (name);
    }

    /**
     * Gets the language tag.
     * @return the language tag
     */
    public String getLanguage() {
        return (lang);
    }

    /**
     * Compares this <code>ScopeName</code> with the specified
     * object for order. Returns a negative integer, zero, or
     * a positive integer as this object is less than, equal
     * to, or greater than the specified object.
     *
     * <P>If the other object is not a <code>ScopeName</code>,
     * a <code>ClassCastException</code> is thrown.
     *
     * <P>This method imposes a total ordering on <code>ScopeNames</code>.
     * <code>ScopeNames</code> are ordered first by language code and then
     * by name.
     *
     * @param o the <code>Object</code> to compare against
     * @return an integer reflecting the outcome of the comparison
     * @exception ClassCastException if the objects cannot be compared
     */
    public int compareTo(Object o) throws ClassCastException {
        ScopeName otherName = (ScopeName) o;
        int langResult = lang.compareTo(otherName.getLanguage());
        if (langResult != 0)
            return (langResult);
        else
            return (name.compareTo(otherName.getName()));
    }

    /**
     * Indicates whether some other object is "equal to" this one. Two
     * <code>ScopeNames</code> are equal if and only if their scope
     * name and language tag strings are equal.
     *
     * @param obj the object with which to compare
     * @return <code>true</code> if this object is the same as the
     *         reference object, <code>false</code> otherwise.
     */
    public boolean equals(Object obj) {
        if (obj instanceof ScopeName) {
            ScopeName otherName = (ScopeName) obj;
            return (otherName.getName().equals(name) &&
            otherName.getLanguage().equals(lang));
        } else
            return (false);
    }

    /**
     * Returns a hash code value for this object.
     * The hash code values for two <code>ScopeNames</code> are equal
     * if they are equal. However, it may be possible for two unequal
     * <code>ScopeNames</code> to have the same hash code.
     *
     * @return a hash code value for this <code>ScopeName</code>
     */
    public int hashCode() {
        return (name.hashCode() + lang.hashCode());
    }

    /**
     * Returns a string representation of this <code>ScopeName</code>.
     *
     * @return a string representation of this <code>ScopeName</code>
     */
    public String toString() {
        return ("ScopeName with name \"" + name +
	    "\", language tag \"" + lang + "\".\n");
    }
    
    /**
     * @serial the scope name
     */
    private String name;

    /**
     * @serial the language tag
     */
    private String lang;

}
