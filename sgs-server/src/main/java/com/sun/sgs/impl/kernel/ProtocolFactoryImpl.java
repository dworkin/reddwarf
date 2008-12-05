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
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.protocol.session.Protocol;
import com.sun.sgs.protocol.session.ProtocolConnectionListener;
import com.sun.sgs.protocol.ProtocolFactory;
import com.sun.sgs.service.TransactionProxy;
import java.lang.reflect.Constructor;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of a transport manager.
 */
public class ProtocolFactoryImpl implements ProtocolFactory {

    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(ProtocolFactoryImpl.class.getName()));
    
    private final ComponentRegistry systemRegistry;
    private final TransactionProxy txnProxy;

    /**
     * @param appProperties
     */
    ProtocolFactoryImpl(Properties appProperties,
                        ComponentRegistry systemRegistry,
                        TransactionProxy proxy)
    {
        logger.log(Level.CONFIG, "Creating a protocol factory");
        this.systemRegistry = systemRegistry;
        this.txnProxy = proxy;
    }
    
    /** {@inheritDoc} */
    @Override
    public Protocol newProtocol(String protocolClassName,
                                Properties properties,
                                ProtocolConnectionListener handler)
        throws Exception
    {
        if (properties == null)
            throw new IllegalArgumentException("properties can not be null");

        if (protocolClassName == null)
            throw new IllegalArgumentException("protocolClassName can not be null");
        
        if (handler == null)
            throw new IllegalArgumentException("handler can not be null");
        
        logger.log(Level.FINE, "new protocol: {0}", protocolClassName);
        
        Class<?> protocolClass = Class.forName(protocolClassName);
    
        if (!Protocol.class.isAssignableFrom(protocolClass))
            throw new IllegalArgumentException(protocolClassName +
                                " class does not implement Protocol interface");
        
        Constructor<?> [] constructors = protocolClass.getConstructors();
        Constructor<?> protocolConstructor = null;
        for (int i = 0; i < constructors.length; i++) {
            Class<?> [] types = constructors[i].getParameterTypes();
            if (types.length == 4) {
                if (types[0].isAssignableFrom(Properties.class) &&
                    types[1].isAssignableFrom(ComponentRegistry.class) &&
                    types[2].isAssignableFrom(TransactionProxy.class) &&
                    types[3].isAssignableFrom(ProtocolConnectionListener.class)) {
                    protocolConstructor = constructors[i];
                    break;
                }
            }
        }

        if (protocolConstructor == null)
            throw new NoSuchMethodException("Could not find a constructor for " +
                                            protocolClass);
        
        return (Protocol)protocolConstructor.newInstance(properties,
                                                         systemRegistry,
                                                         txnProxy,
                                                         handler);
    }
}