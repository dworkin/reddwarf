/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
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

package com.sun.sgs.impl.app.profile;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;

import com.sun.sgs.kernel.ProfileConsumer;
import com.sun.sgs.kernel.ProfileOperation;
import com.sun.sgs.kernel.ProfileProducer;
import com.sun.sgs.kernel.ProfileRegistrar;


/**
 * This is an implementation of <code>DataManager</code> used to suport
 * profiling. It simply calls its backing manager for each manager method. If
 * no <code>ProfileRegistrar</code> is provided via
 * <code>setProfileRegistrar</code> then this manager does no reporting, and
 * only calls through to the backing manager. If the backing manager is also
 * an instance of <code>ProfileProducer</code> then it too will be supplied
 * with the <code>ProfileRegistrar</code> as described in
 * <code>setProfileRegistrar</code>.
 */
public class ProfileDataManager implements DataManager, ProfileProducer {

    // the data manager that this manager calls through to
    private final DataManager backingManager;

    // the operations being profiled
    private ProfileOperation createReferenceOp = null;

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
     * <p>
     * Note that if the backing manager supplied to the constructor is also
     * an instance of <code>ProfileProducer</code> then its
     * <code>setProfileRegistrar</code> will be invoked when this method
     * is called.
     */
    public void setProfileRegistrar(ProfileRegistrar profileRegistrar) {
        ProfileConsumer consumer =
            profileRegistrar.registerProfileProducer(this);

	if (consumer != null) {
	    createReferenceOp = consumer.registerOperation("createReference");
	}

        // call on the backing manager, if it's also profiling
        if (backingManager instanceof ProfileProducer)
            ((ProfileProducer)backingManager).
                setProfileRegistrar(profileRegistrar);
    }

    /**
     * {@inheritDoc}
     */
    public <T> T getBinding(String name, Class<T> type) {
        return backingManager.getBinding(name, type);
    }

    /**
     * {@inheritDoc}
     */
    public void setBinding(String name, ManagedObject object) {
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
    public void removeObject(ManagedObject object) {
        backingManager.removeObject(object);
    }

    /**
     * {@inheritDoc}
     */
    public void markForUpdate(ManagedObject object) {
        backingManager.markForUpdate(object);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Note that this method is the only one that is directly reported by
     * this manager, if profiling is enabled.
     */
    public ManagedReference createReference(ManagedObject object) {
        if (createReferenceOp != null)
            createReferenceOp.report();
        return backingManager.createReference(object);
    }

}
