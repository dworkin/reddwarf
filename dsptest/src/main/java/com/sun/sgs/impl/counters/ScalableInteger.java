package com.sun.sgs.impl.counters;

import java.util.concurrent.atomic.AtomicInteger;

import com.sun.sgs.app.ManagedObject;

/**
 * ManagedObject wrapper for an AtomicInteger.
 */
public class ScalableInteger extends AtomicInteger implements ManagedObject
{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

}