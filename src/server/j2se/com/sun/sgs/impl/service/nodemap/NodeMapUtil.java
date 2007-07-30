/*
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.service.DataService;

/**
 * Static utility methods used by both the server and service.
 */
public final class NodeMapUtil {
    /** Package name of this class */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.nodemap";
    
    /** The prefix of a node key which maps to its mapped Identities. */
    private static final String NODE_KEY_PREFIX = PKG_NAME + ".node.";
    
    /** The prefix of a identity key which maps to its assigned node. */
    private static final String IDENTITY_KEY_PREFIX = PKG_NAME + ".identity.";
    
    /** The prefix of the status markers. */
    private static final String STATUS_KEY_PREFIX = PKG_NAME + ".status.";
    
    /** Never to be instantiated */
    private NodeMapUtil() {
        throw new AssertionError("Should not instantiate");
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
