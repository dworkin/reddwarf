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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.transport.ConnectionHandler;
import com.sun.sgs.transport.Transport;
import com.sun.sgs.transport.TransportFactory;
import java.lang.reflect.Constructor;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of a transport manager.
 */
public class TransportManagerImpl implements TransportFactory {

    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(TransportManagerImpl.class.getName()));
    
    /**
     * @param appProperties
     */
    public TransportManagerImpl(Properties appProperties) {
        logger.log(Level.CONFIG, "Creating an IO manager");
    }
    
    /** {@inheritDoc} */
    @Override
    public Transport startTransport(String transportClassName,
                                    Properties properties,
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
            throw new IllegalArgumentException(transportClassName +
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