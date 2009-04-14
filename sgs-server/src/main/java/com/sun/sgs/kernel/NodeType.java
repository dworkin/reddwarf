package com.sun.sgs.kernel;

/**
 * The valid node types.
 */
public enum NodeType {
    /** A single node configuration. */
    singleNode,
    /** The core server for multi-node configurations. */
    coreServerNode,
    /** An application node for multi-node configurations. */
    appNode,
}
