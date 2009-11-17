package com.sun.sgs.impl.hook;

import com.sun.sgs.app.*;
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
