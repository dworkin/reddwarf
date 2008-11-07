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

/**
 * An object that wraps a {@link Context} object, to permit changing which
 * context a set of references refer to without needing to update the
 * references individually.
 *
 * @see	ObjectCache
 */
final class ContextWrapper {

    /** The associated context. */
    private Context context;

    /** The generation number -- incremented when the context is changed. */
    private int generation = 0;

    /**
     * Creates an instance with the specified context.
     *
     * @param	context the context
     */
    ContextWrapper(Context context) {
	setContext(context);
    }

    /**
     * Returns the associated context.
     *
     * @return	the context
     */
    Context getContext() {
	return context;
    }

    /**
     * Sets the associated context.
     *
     * @param	context the new context
     */
    void setContext(Context context) {
	if (context == null) {
	    throw new NullPointerException("The context must not be null");
	}
	this.context = context;
	generation++;
    }

    /**
     * Returns the current generation number, which increments each time the
     * context is changed.  Always returns a number greater than zero.
     *
     * @return	the generation number
     */
    int getGeneration() {
	return generation;
    }

    /**
     * Returns a string representing this object.
     *
     * @return	a string representing this object
     */
    public String toString() {
	return "ContextWrapper@" +
	    Integer.toHexString(System.identityHashCode(this)) +
	    "[" + context + "]";
    }
}
