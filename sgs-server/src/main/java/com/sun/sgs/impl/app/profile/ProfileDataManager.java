/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.app.profile;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import java.math.BigInteger;

/**
 * This implementation of {@code DataManager} simply calls its 
 * backing manager for each manager method.
 */
public class ProfileDataManager implements DataManager {

    // the data manager that this manager calls through to
    private final DataManager backingManager;

    /**
     * Creates an instance of <code>ProfileDataManager</code>.
     *
     * @param backingManager the <code>DataManager</code> to call through to
     */
    public ProfileDataManager(DataManager backingManager) {
        this.backingManager = backingManager;
    }

    /**
     * {@inheritDoc}
     */
    public ManagedObject getBinding(String name) {
        return backingManager.getBinding(name);
    }

    /**
     * {@inheritDoc}
     */
    public ManagedObject getBindingForUpdate(String name) {
        return backingManager.getBindingForUpdate(name);
    }

    /**
     * {@inheritDoc}
     */
    public void setBinding(String name, Object object) {
        backingManager.setBinding(name, object);
    }

    /**
     * {@inheritDoc}
     */
    public void removeBinding(String name) {
        backingManager.removeBinding(name);
    }

    /**
     * {@inheritDoc}
     */
    public String nextBoundName(String name) {
        return backingManager.nextBoundName(name);
    }

    /**
     * {@inheritDoc}
     */
    public void removeObject(Object object) {
        backingManager.removeObject(object);
    }

    /**
     * {@inheritDoc}
     */
    public void markForUpdate(Object object) {
        backingManager.markForUpdate(object);
    }

    /**
     * {@inheritDoc}
     */
    public <T> ManagedReference<T> createReference(T object) {
        return backingManager.createReference(object);
    }

    /**
     * {@inheritDoc}
     */
    public BigInteger getObjectId(Object object) {
	return backingManager.getObjectId(object);
    }
}
