package com.sun.sgs.kernel;

import com.sun.sgs.transport.ConnectionHandler;
import com.sun.sgs.transport.Transport;
import java.util.Properties;

/**
 * A transport manager.
 */
public interface TransportManager {

    /**
     * Start a new transport.
     * 
     * @param properties
     * @param transportClassName
     * @param handler
     * @return the transport object
     * @throws java.lang.Exception
     */
    Transport startTransport(Properties properties,
                             String transportClassName,
                             ConnectionHandler handler) throws Exception;

}
