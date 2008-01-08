/*
 * Copyright 2008 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.service.DataService;
import java.io.Serializable;
import java.util.logging.Level;

/**
 * Static utility methods used by both the server and service for
 * data service name bindings.
 */
final class NodeMapUtil {
    private static final String PREFIX = "nodemap";
    
    /** The major and minor version numbers for the layout of
     *  keys and data persisted by the node mapping service.
     */
    private static final int major_version = 1;
    private static final int minor_version = 0;
    
    /** The version key. */
    private static final String VERSION_KEY = PREFIX + ".version";
    
    /** The prefix of a identity key which maps to its assigned node. */
    private static final String IDENTITY_KEY_PREFIX = PREFIX + ".identity.";
    
    /** The prefix of a node key which maps to its mapped Identities. */
    private static final String NODE_KEY_PREFIX = PREFIX + ".node."; 
    
    /** The prefix of the status markers. */
    private static final String STATUS_KEY_PREFIX = PREFIX + ".status.";
    
    /** Never to be instantiated */
    private NodeMapUtil() {
        throw new AssertionError("Should not instantiate");
    }
       
    /**
     * Checks if the current stored version is the same as the current
     * version, and, if they are not the same, converts the current 
     * persisted data to the current version.  Stores the current version
     * in the data store.
     * <p>
     * This method must be called within a transaction.  It should be
     * called at configuration time:  migrating versions could take time.
     * <p>
     *
     * 
     * @param dataService the data service for accessing persisted data
     * @param logger the {@code Logger} for logging information 
     *
     * @return {@code true} if the versions were the same, {@code false}
     *         otherwise
     * @throws TransactionException if the operation failed because of a problem
     *        with the current transaction
     */
    static boolean handleDataVersion(DataService dataService, 
                                     LoggerWrapper logger) 
    {
        boolean ok = true;
        VersionMO currentVersion = new VersionMO(major_version, minor_version);
        try {
            VersionMO oldVersion = 
                    dataService.getServiceBinding(VERSION_KEY, VersionMO.class);
            logger.log(Level.CONFIG, "Found version " + oldVersion);
            ok = currentVersion.equals(oldVersion);
            if (!ok) {
                // Need to implement something if we bump the version number!
                logger.log(Level.CONFIG, 
                        "Version conversion code not implemented!");
                throw 
                    new RuntimeException("Version conversion code not present");
                // dataService.removeObject(oldVersion);
                
                // Convert the old data to the new, as required.
                // Generally, conversion should only be required for major
                // version number changes.   Typically, will want to add
                // private methods to convert from the previous version
                // to the current, and code here should make multiple method
                // calls, causing version-by-version upgrades, until the
                // data service is at the current version.
                
                // Store the new version.
//                dataService.setServiceBinding(VERSION_KEY, currentVersion);
//                logger.log(Level.CONFIG, "Storing version " + currentVersion);
            }
        } catch (NameNotBoundException ex) {
            // All is ok.  This is the first time a version has been put
            // in this data service.
            dataService.setServiceBinding(VERSION_KEY, currentVersion);
            logger.log(Level.CONFIG, "Storing version " + currentVersion);
        }

        return ok;
    }
    
    /* -- Various keys used to persist data -- */
    
    /* -- The identity key, for identity->node mapping -- */
    
    /**
     * Returns a identity key for the given {@code id}. 
     */
    static String getIdentityKey(Identity id) {    
        return IDENTITY_KEY_PREFIX + id.getName();
    }
    

    /* -- The node key, for node->identity mapping -- */
    
    /**
     * Returns a node key for the given {@code nodeId} and {@code id}.
     */
    static String getNodeKey(long nodeId, Identity id) {
        StringBuilder sb = buildNodeKey(nodeId);
        sb.append(id.getName());
        return sb.toString();
    }
    
    /**
     * Returns a node key for the given {@code nodeId};  used for 
     * iterating through a node for all identities assigned to it.
     */
    static String getPartialNodeKey(long nodeId) {
	return buildNodeKey(nodeId).toString();
    }   
    
    /**
     * Private helper method; returns a {@code StringBuilder} for
     * a partial node key.
     */
    private static StringBuilder buildNodeKey(long nodeId) {
        StringBuilder sb = new StringBuilder(NODE_KEY_PREFIX);
        sb.append(nodeId);
        sb.append(".");
        return sb;
    }
  
    /* -- The status key, for tracking which identities are in use -- */
    
    /**
     * Returns a status key for the given {@code id}, {@code nodeId},
     * and {@code serviceName}.  The status mappings are held per 
     * identity per node.
     */
    static String getStatusKey(Identity id, long nodeId, String serviceName) 
    {
        StringBuilder sb = buildStatusKey(id, nodeId);
        sb.append(serviceName);
        return sb.toString();	
    }
    
    /** 
     * Returns a status key for the given {@code id} and {@code nodeId}.
     * Used for finding all status entries for an identity on a particular
     * node.
     */
    static String getPartialStatusKey(Identity id, long nodeId) {
        return buildStatusKey(id, nodeId).toString();
    }
    
    /**
     * Returns a status key for a given {@code id}. Used for finding all
     * status entries for an identity (on all nodes).
     */
    static String getPartialStatusKey(Identity id) {
        return buildStatusKey(id).toString();
    }

    /**
     * Private helper method; returns a {@code StringBuilder} for a 
     * partial status key.
     */
    private static StringBuilder buildStatusKey(Identity id, long nodeId) {
        StringBuilder sb = buildStatusKey(id);
        sb.append(nodeId);
        sb.append(".");
        return sb;
    }

    /**
     * Private helper method; returns a {@code StringBuilder} for a 
     * partial status key.
     */
    private static StringBuilder buildStatusKey(Identity id) {
        StringBuilder sb = new StringBuilder(STATUS_KEY_PREFIX);
        sb.append(id.getName());
        sb.append(".");
        return sb;
    }

    
    /**
     * An immutable class to hold the current version of the keys
     * and data persisted by the node mapping service.
     */   
    static class VersionMO implements ManagedObject, Serializable {
        /** Serialization version. */
        private static final long serialVersionUID = 1L;
        
        private int major_version;
        private int minor_version;
        
        VersionMO(int major, int minor) {
            major_version = major;
            minor_version = minor;
        }
        
        /**
         * Returns the major version number.
         * @return the major version number
         */
        public int getMajorVersion() {
            return major_version;
        }
        
        /**
         * Returns the minor version number.
         * @return the minor version number
         */
        public int getMinorVersion() {
            return minor_version;
        }
        
        /** {@inheritDoc} */
        @Override
        public String toString() {
            return "VersionMO[major:" + major_version + 
                    ", minor:" + minor_version + "]";
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (obj == null) {
                return false;
            } else if (obj.getClass() == this.getClass()) {
                VersionMO other = (VersionMO) obj;
                return major_version == other.major_version && 
                       minor_version == other.minor_version;

            }
            return false;
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            int result = 17;
            result = 37*result + major_version;
            result = 37*result + minor_version;
            return result;              
        }
    }
}
