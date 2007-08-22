/*
 * Copyright 2007 Sun Microsystems, Inc.  All rights reserved.
 */

package com.sun.sgs.test.impl.service.nodemap;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.rmi.RemoteException;
import java.util.HashSet;
import java.util.Set;

/**
 * An evil proxy object, which throws a {@link RemoteException}
 * for all {@code NodeMappingServer} methods.
 *
 */
public class EvilProxy implements InvocationHandler {
    
    private final Object obj;
    private final Set<String> interestingNames = new HashSet<String>();
    
    /** Creates a new instance of EvilProxy */
    private EvilProxy(Object obj) throws Exception {
        this.obj = obj;
        Class serverClass = 
           Class.forName("com.sun.sgs.impl.service.nodemap.NodeMappingServer");
        Method[] methods = serverClass.getDeclaredMethods();
        for (Method m : methods ) {
            interestingNames.add(m.getName());
        }
    }
    
    /**
     * Factory method.  Creates a proxy for the object.
     */
    public static synchronized Object proxyFor(Object obj) throws Exception {
        Class<?> objClass = obj.getClass();
        return Proxy.newProxyInstance(
                objClass.getClassLoader(),
                objClass.getInterfaces(),
                new EvilProxy(obj));
    }

    public Object invoke(Object proxy, Method method, Object[] args) 
        throws Throwable 
    {
        String name = method.getName();
        if (interestingNames.contains(name)) {
            throw new RemoteException(name + " failed");
        }
        try {
            return method.invoke(obj, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }   
}
