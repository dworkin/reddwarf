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

package com.sun.sgs.impl.service.nodemap;

import com.sun.sgs.auth.Identity;

/**
 * Static utility methods used by both the server and service for
 * data service name bindings.
 */
final class NodeMapUtil {
    private static final String PREFIX = "nodemap";
    
    /** The major and minor version numbers for the layout of
     *  keys and data persisted by the node mapping service.
     */
    static final int MAJOR_VERSION = 1;
    static final int MINOR_VERSION = 0;
    
    /** The version key. */
    static final String VERSION_KEY = PREFIX + ".version";
    
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
    static String getStatusKey(Identity id, long nodeId, String serviceName) {
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
}
