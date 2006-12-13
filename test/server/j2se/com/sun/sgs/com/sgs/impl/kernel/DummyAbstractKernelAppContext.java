package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.Service;

/**
 * Define an implementation of AbstractKernelAppContext that obtains components
 * from a specified component registry and registers itself as the context for
 * the current thread.  This class is defined in the com.sun.sgs.impl.kernel
 * package because the AbstractKernelAppContext class is not public.
 */
public class DummyAbstractKernelAppContext extends AbstractKernelAppContext {

    /** The component registry. */
    private ComponentRegistry componentRegistry;

    /**
     * Creates an instance that obtains components from the argument and
     * registers itself as the context for the current thread.
     */
    public DummyAbstractKernelAppContext(ComponentRegistry componentRegistry) {
	super("DummyApplication");
	if (componentRegistry == null) {
	    throw new NullPointerException("The argument must not be null");
	}
	this.componentRegistry = componentRegistry;
	ContextResolver.setContext(this);
    }

    public ChannelManager getChannelManager() {
	return componentRegistry.getComponent(ChannelManager.class);
    }

    public DataManager getDataManager() {
	return componentRegistry.getComponent(DataManager.class);
    }

    public TaskManager getTaskManager() {
	return componentRegistry.getComponent(TaskManager.class);
    }

    public <T> T getManager(Class<T> type) {
	return componentRegistry.getComponent(type);
    }

    public void setServices(ComponentRegistry serviceComponents) {
	this.componentRegistry = serviceComponents;
    }

    public <T extends Service> T getService(Class<T> type) {
	return componentRegistry.getComponent(type);
    }
}
