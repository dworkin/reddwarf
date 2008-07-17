package com.sun.sgs.impl.kernel;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.transport.ConnectionHandler;
import com.sun.sgs.transport.Transport;
import com.sun.sgs.kernel.TransportManager;
import java.lang.reflect.Constructor;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of a transport manager.
 */
public class TransportManagerImpl implements TransportManager {

    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(TransportManagerImpl.class.getName()));
    
    /**
     * @param appProperties
     */
    public TransportManagerImpl(Properties appProperties) {
        logger.log(Level.CONFIG, "Creating an IO manager");
    }
    
    /** {@inheritDoc} */
    public Transport startTransport(Properties properties,
                                    String transportClassName,
                                    ConnectionHandler handler)
        throws Exception
    {
        if (properties == null)
            throw new IllegalArgumentException("properties can not be null");

        if (transportClassName == null)
            throw new IllegalArgumentException("transport can not be null");
        
        if (handler == null)
            throw new IllegalArgumentException("handler can not be null");
        
        logger.log(Level.FINE, "starting transport: {0}", transportClassName);
        
        Class<?> transportClass = Class.forName(transportClassName);
    
        if (!Transport.class.isAssignableFrom(transportClass))
            throw new Exception(transportClassName +
                                " class does not implement Transport interface");
        
        Constructor<?> [] constructors = transportClass.getConstructors();
        Constructor<?> transportConstructor = null;
        for (int i = 0; i < constructors.length; i++) {
            Class<?> [] types = constructors[i].getParameterTypes();
            if (types.length == 2) {
                if (types[0].isAssignableFrom(Properties.class) &&
                    types[1].isAssignableFrom(ConnectionHandler.class)) {
                    transportConstructor = constructors[i];
                    break;
                }
            }
        }

        if (transportConstructor == null)
            throw new NoSuchMethodException("Could not find a constructor for " +
                                            transportClass);
        
        return (Transport)transportConstructor.newInstance(properties, handler);
    }
}