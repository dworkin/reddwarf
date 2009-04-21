package com.sun.ds;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.sgs.app.ManagedObject;

/**
 * ManagedObject wrapper for an AtomicInteger.
 */
public class ScalableInteger extends AtomicInteger
        implements Serializable, ManagedObject
{

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

}