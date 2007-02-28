/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.app;

import java.io.Serializable;

/**
 * A marker interface implemented by shared, persistent objects managed by
 * {@link DataManager}.  Classes that implement <code>ManagedObject</code> must
 * also implement {@link Serializable}, as should any non-managed objects they
 * refer to.  Any instances of <code>ManagedObject</code> that a managed object
 * refers to directly, or indirectly through non-managed objects, need to be
 * referred to through instances of {@link ManagedReference}.
 *
 * @see		DataManager
 * @see		ManagedReference
 * @see		Serializable
 */
public interface ManagedObject { }
