/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.shared;

public class Pair<O,P> {
    
    private O obj1 = null;
    private P obj2 = null;
    
    public Pair(O firstObj, P secondObj) {
        obj1 = firstObj;
        obj2 = secondObj;
    }
    
    public O getFirst() {
        return obj1;
    }
    
    public P getSecond() {
        return obj2;
    }
    
    public O setFirst(O obj) {
        O old = obj1;
        obj1 = obj;
        return old;
    }
    
    public P setSecond(P obj) {
        P old = obj2;
        obj2 = obj;
        return old;
    }
}
