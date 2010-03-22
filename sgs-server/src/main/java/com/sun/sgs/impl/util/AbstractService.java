/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.util;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.TransactionException;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TransactionProxy;
import java.io.Serializable;
import java.util.Properties;

/**
 * An abstract implementation of a service, with support for checking service
 * versions in addition to the facilities provided by {@link
 * AbstractBasicService}. <p>
 *
 * An {@link #AbstractService} supports the following properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.util.io.task.max.retries
 *	</b></code><br>
 *	<i>Default:</i> 5 retries <br>
 *
 * <dd style="padding-top: .5em">
 *	Specifies how many times an {@link IoRunnable IoRunnable} task should 
 *      be retried before performing failure procedures. The value
 *	must be greater than or equal to {@code 0}.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	com.sun.sgs.impl.util.io.task.wait.time
 *	</b></code><br>
 *	<i>Default:</i> 100 milliseconds <br>
 *
 * <dd style="padding-top: .5em">
 *      Specifies the wait time between {@link IoRunnable IoRunnable} task
 *      retries. The value must be greater than or equal to {@code 0}.
 *
 * </dl>
 */
public abstract class AbstractService extends AbstractBasicService {

    /** The data service. */
    protected final DataService dataService;

    /**
     * Constructs an instance with the specified {@code properties}, {@code
     * systemRegistry}, {@code txnProxy}, and {@code logger}.  It sets this
     * service's state to {@code INITIALIZED}.
     *
     * @param	properties service properties
     * @param	systemRegistry system registry
     * @param	txnProxy transaction proxy
     * @param	logger the service's logger
     */
    protected AbstractService(Properties properties,
			      ComponentRegistry systemRegistry,
			      TransactionProxy txnProxy,
			      LoggerWrapper logger)
    {
	super(properties, systemRegistry, txnProxy, logger);
	this.dataService = txnProxy.getService(DataService.class);
    }

    /**
     * Checks the service version.  If a version is not associated with the
     * given {@code versionKey}, then a new {@link Version} object
     * (constructed with the specified {@code majorVersion} and {@code
     * minorVersion}) is bound in the data service with the specified key.
     *
     * <p>If an old version is bound to the specified key and that old
     * version is not equal to the current version (as specified by {@code
     * majorVersion}/{@code minorVersion}), then the {@link
     * #handleServiceVersionMismatch handleServiceVersionMismatch} method is
     * invoked to convert the old version to the new version.  If the {@code
     * handleVersionMismatch} method returns normally, the old version is
     * removed and the current version is bound to the specified key.
     *
     * <p>This method must be called within a transaction.
     *
     * @param	versionKey a key for the version
     * @param	majorVersion a major version
     * @param	minorVersion a minor version
     * @throws 	TransactionException if there is a problem with the
     *		current transaction
     * @throws	IllegalStateException if {@code handleVersionMismatch} is
     *		invoked and throws a {@code RuntimeException}
     */
    protected final void checkServiceVersion(
	String versionKey, int majorVersion, int minorVersion)
    {
	if (versionKey ==  null) {
	    throw new NullPointerException("null versionKey");
	}
	Version currentVersion = new Version(majorVersion, minorVersion);
	try {
	    Version oldVersion = (Version)
		dataService.getServiceBinding(versionKey);
	    
	    if (!currentVersion.equals(oldVersion)) {
		try {
		    handleServiceVersionMismatch(oldVersion, currentVersion);
		    dataService.removeObject(oldVersion);
		    dataService.setServiceBinding(versionKey, currentVersion);
		} catch (IllegalStateException e) {
		    throw e;
		} catch (RuntimeException e) {
		    throw new IllegalStateException(
		        "exception occurred while upgrading from version: " +
		        oldVersion + ", to: " + currentVersion, e);
		}
	    }

	} catch (NameNotBoundException e) {
	    // No version exists yet; store first version in data service.
	    dataService.setServiceBinding(versionKey, currentVersion);
	}
    }
    
    /**
     * Handles conversion from the {@code oldVersion} to the {@code
     * currentVersion}.  This method is invoked by {@link #checkServiceVersion
     * checkServiceVersion} if a version mismatch is detected and is invoked
     * from within a transaction.
     *
     * @param	oldVersion the old version
     * @param	currentVersion the current version
     * @throws	IllegalStateException if the old version cannot be upgraded
     *		to the current version
     */
    protected abstract void handleServiceVersionMismatch(
	Version oldVersion, Version currentVersion);
    
    /**
     * Returns the data service.
     *
     * @return the data service
     */
    public DataService getDataService() {
	return dataService;
    }

    /**
     * An immutable class to hold the current version of the keys
     * and data persisted by a service.
     */   
    public static class Version implements ManagedObject, Serializable {
        /** Serialization version. */
        private static final long serialVersionUID = 1L;
        
        private final int majorVersion;
        private final int minorVersion;

	/**
	 * Constructs an instance with the specified {@code major} and
	 * {@code minor} version numbers.
	 *
	 * @param major a major version number
	 * @param minor a minor version number
	 */
        public Version(int major, int minor) {
            majorVersion = major;
            minorVersion = minor;
        }
        
        /**
         * Returns the major version number.
         * @return the major version number
         */
        public int getMajorVersion() {
            return majorVersion;
        }
        
        /**
         * Returns the minor version number.
         * @return the minor version number
         */
        public int getMinorVersion() {
            return minorVersion;
        }
        
        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "Version[major:" + majorVersion + 
                    ", minor:" + minorVersion + "]";
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null) {
                return false;
            } else if (obj.getClass() == this.getClass()) {
                Version other = (Version) obj;
                return majorVersion == other.majorVersion && 
                       minorVersion == other.minorVersion;

            }
            return false;
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            int result = 17;
            result = 37 * result + majorVersion;
            result = 37 * result + minorVersion;
            return result;              
        }
    }
}
