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

package com.sun.sgs.test.transport;

import com.sun.sgs.test.util.NameRunner;
import com.sun.sgs.transport.ConnectionHandler;
import com.sun.sgs.transport.Transport;
import com.sun.sgs.transport.TransportDescriptor;
import com.sun.sgs.transport.TransportFactory;
import java.lang.reflect.InvocationTargetException;
import java.util.Properties;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the transport factory.
 */
@RunWith(NameRunner.class)
public class TestTransportFactory {
    
    private static final String FAIL_CONSTRUCTOR_PROPERTY = "FailConstructor";
    
    @Test(expected=NullPointerException.class)
    public void testNullClassName() throws Exception {
        TransportFactory.newTransport(null, new Properties());
    }
    
    @Test(expected=NullPointerException.class)
    public void testNullProperties() throws Exception {
        TransportFactory.newTransport(ValidTransport.class.getName(), null);
    }
    
    @Test(expected=ClassNotFoundException.class)
    public void testTransportNotFound() throws Exception {
        TransportFactory.newTransport("noTransportByThisName",
                                       new Properties());
    }
    @Test(expected=IllegalArgumentException.class)
    public void testBogusTransport() throws Exception {
        TransportFactory.newTransport(BogusTransport.class.getName(),
                                      new Properties());
    }
    
    @Test(expected=NoSuchMethodException.class)
    public void testNoConstructorTransport() throws Exception {
        TransportFactory.newTransport(NoConstructorTransport.class.getName(),
                                      new Properties());
    }
    
    @Test
    public void testValidTransport() throws Exception {
        TransportFactory.newTransport(ValidTransport.class.getName(),
                                      new Properties());
    }
    
    @Test(expected=InvocationTargetException.class)
    public void testFailedConstructor() throws Exception {
        Properties props = new Properties();
        props.setProperty(FAIL_CONSTRUCTOR_PROPERTY, "true");
        TransportFactory.newTransport(ValidTransport.class.getName(), props);
    }
    
    public static class BogusTransport {}
    
    public static class NoConstructorTransport implements Transport {

        @Override
        public TransportDescriptor getDescriptor() {
            return null;
        }

        @Override
        public void accept(ConnectionHandler handler) {}

        @Override
        public void shutdown() {}
    }
    
    public static class ValidTransport implements Transport {

        public ValidTransport(Properties props) throws Exception {
            if (props == null)
                throw new NullPointerException("props is null");
            
            if (props.getProperty(FAIL_CONSTRUCTOR_PROPERTY) != null)
                throw new ConstructorFailedException();
        }
        
        @Override
        public TransportDescriptor getDescriptor() {
            return null;
        }

        @Override
        public void accept(ConnectionHandler handler) {}

        @Override
        public void shutdown() {}
    }
    
    private static class ConstructorFailedException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
