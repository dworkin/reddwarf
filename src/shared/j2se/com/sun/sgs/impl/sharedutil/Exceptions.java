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

package com.sun.sgs.impl.sharedutil;

/**
 * Utility methods for working with {@link Exception}s.
 */
public class Exceptions {

    /** Prevents instantiation of this class. */
    private Exceptions() { }
    
    /**
     * Returns the given exception with its cause initialized.  The
     * original exception is returned in a typesafe way so that it
     * can be thrown easily.
     * 
     * @param <T> the type of the parent exception
     * @param exception the exception to initialize 
     * @param cause the cause
     * @return the exception with its cause initialized
     * 
     * @throws IllegalArgumentException if an attempt is made to set
     *         an exception as its own cause
     * @throws IllegalStateException if the exception has already had
     *         its cause initialized
     * @see Throwable#initCause(Throwable)
     */
    public static <T extends Throwable> T
    initCause(T exception, Throwable cause) {
        exception.initCause(cause);
        return exception;
    }

}
