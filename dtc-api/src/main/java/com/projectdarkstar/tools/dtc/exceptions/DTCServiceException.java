/*
 * Copyright 2008 Sun Microsystems, Inc.
 *
 * This file is part of the Darkstar Test Cluster
 *
 * Darkstar Test Cluster is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Darkstar Test Cluster is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.projectdarkstar.tools.dtc.exceptions;

/**
 * A generic exception type that should be thrown whenever there is a
 * business logic error or data validation error related to processing
 * data sent to a DTC service
 */
public class DTCServiceException extends Exception
{
    public DTCServiceException() {
        super();
    }
    
    public DTCServiceException(String message) {
        super(message);
    }
    
    public DTCServiceException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public DTCServiceException(Throwable cause) {
        super(cause);
    }

}
