/*
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.kernel.KernelRunnable;
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
                dataService.removeObject(oldVersion);
                
                // Convert the old data to the new, as required.
                // Generally, conversion should only be required for major
                // version number changes.   Typically, will want to add
                // private methods to convert from the previous version
                // to the current, and code here should make multiple method
                // calls, causing version-by-version upgrades, until the
                // data service is at the current version.
            }
        } catch (NameNotBoundException ex) {
            // All is ok.  This is the first time a version has been put
            // in this data service.
        } finally {
            // Store the new version.
            dataService.setServiceBinding(VERSION_KEY, currentVersion);
            logger.log(Level.CONFIG, "Storing version " + currentVersion);
        }

        return ok;
    }
    
    /**
     * Returns a identity key for the given {@code identity}. 
     */
    static String getIdentityKey(Identity id) {    
        StringBuilder sb = new StringBuilder(IDENTITY_KEY_PREFIX);
        sb.append(id.getName());
        return sb.toString();
    }
    
    /**
     * Returns a node key for the given {@code node}.  
     */
    private static StringBuilder buildNodeKey(long nodeId) {
        StringBuilder sb = new StringBuilder(NODE_KEY_PREFIX);
        sb.append(nodeId);
        sb.append(".");
        return sb;
    }
    static String getPartialNodeKey(long nodeId) {
	return buildNodeKey(nodeId).toString();
    }   
    static String getNodeKey(long nodeId, Identity id) {
        StringBuilder sb = buildNodeKey(nodeId);
        sb.append(id.getName());
        return sb.toString();
    }
    
    /**
     * Returns a status key for the given {@code identity}.  The status
     * mappings are held per identity per node.
     */
    private static StringBuilder buildStatusKey(Identity id) {
        StringBuilder sb = new StringBuilder(STATUS_KEY_PREFIX);
        sb.append(id.getName());
        sb.append(".");
        return sb;
    }
    
    private static StringBuilder buildStatusKey(Identity id, long nodeId) {
        StringBuilder sb = buildStatusKey(id);
        sb.append(nodeId);
        sb.append(".");
        return sb;
    }

    static String getPartialStatusKey(Identity id) {
        return buildStatusKey(id).toString();
    }
    
    static String getPartialStatusKey(Identity id, long nodeId) {
        return buildStatusKey(id, nodeId).toString();
    }
    
    static String getStatusKey(Identity id, long nodeId, String serviceName) 
    {
        StringBuilder sb = buildStatusKey(id, nodeId);
        sb.append(serviceName);
        return sb.toString();	
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
            return "VersionMO[major : " + major_version + 
                    ", minor : " + minor_version + "]";
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
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
    
    /**
     * Task which gets an IdentityMO from a data service.  This is
     * a separate task so we can retrieve the result.  An exception
     * will be thrown if the IdentityMO is not found or the name
     * binding doesn't exist.
     */
    static class GetIdTask implements KernelRunnable {
        private IdentityMO idmo = null;
        private final DataService dataService;
        private final String idkey;
        
        /**
         * Create a new instance.
         *
         * @param dataService the data service to retrieve from
         * @param idkey Identitifier key
         */
        GetIdTask(DataService dataService, String idkey) {
            this.dataService = dataService;
            this.idkey = idkey;
        }
        
        /**
         * {@inheritDoc}
         * Get the IdentityMO. 
         * @throws NameNotBoundException if no object is bound to the id
         * @throws ObjectNotFoundException if the object has been removed
         */
        public void run() {
            idmo = dataService.getServiceBinding(idkey, IdentityMO.class);
        }
        
        /** {@inheritDoc} */
        public String getBaseTaskType() {
            return this.getClass().getName();
        }
        
        /**
         *  The identity MO retrieved from the data store, or null if
         *  the task has not yet executed or there was an error while
         *  executing.
         * @return the IdentityMO
         */
        public IdentityMO getId() {
            return idmo;
        }
    }
}
