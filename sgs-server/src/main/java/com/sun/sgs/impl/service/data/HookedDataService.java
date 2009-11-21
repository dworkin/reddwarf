/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.impl.hook.HookLocator;
import com.sun.sgs.service.DataService;
import java.math.BigInteger;

public class HookedDataService implements DataService {

    private DataService delegate;

    public HookedDataService(DataService delegate) {
        this.delegate = delegate;
    }

    private static <T> T applyHook(T object) {
        return HookLocator.getManagedObjectReplacementHook().replaceManagedObject(object);
    }

    // hooked methods

    public void setBinding(String name, Object object) {
        delegate.setBinding(name, applyHook(object));
    }

    public void removeObject(Object object) {
        delegate.removeObject(applyHook(object));
    }

    public void markForUpdate(Object object) {
        delegate.markForUpdate(applyHook(object));
    }

    public <T> ManagedReference<T> createReference(T object) {
        return delegate.createReference(applyHook(object));
    }

    public BigInteger getObjectId(Object object) {
        return delegate.getObjectId(applyHook(object));
    }

    // unaffected methods

    public long getLocalNodeId() {
        return delegate.getLocalNodeId();
    }

    public ManagedObject getServiceBinding(String name) {
        return delegate.getServiceBinding(name);
    }

    public ManagedObject getServiceBindingForUpdate(String name) {
        return delegate.getServiceBindingForUpdate(name);
    }

    public void setServiceBinding(String name, Object object) {
        delegate.setServiceBinding(name, object);
    }

    public void removeServiceBinding(String name) {
        delegate.removeServiceBinding(name);
    }

    public String nextServiceBoundName(String name) {
        return delegate.nextServiceBoundName(name);
    }

    public ManagedReference<?> createReferenceForId(BigInteger id) {
        return delegate.createReferenceForId(id);
    }

    public BigInteger nextObjectId(BigInteger objectId) {
        return delegate.nextObjectId(objectId);
    }

    public ManagedObject getBinding(String name) {
        return delegate.getBinding(name);
    }

    public ManagedObject getBindingForUpdate(String name) {
        return delegate.getBindingForUpdate(name);
    }

    public void removeBinding(String name) {
        delegate.removeBinding(name);
    }

    public String nextBoundName(String name) {
        return delegate.nextBoundName(name);
    }

    public String getName() {
        return delegate.getName();
    }

    public void ready() throws Exception {
        delegate.ready();
    }

    public void shutdown() {
        delegate.shutdown();
    }
}
