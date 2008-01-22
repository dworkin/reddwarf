/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.Service;

import com.sun.sgs.test.util.DummyIdentity;

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
	ThreadState.setCurrentOwner(new TaskOwnerImpl(new DummyIdentity(), this));
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

    public <T extends Service> T getService(Class<T> type) {
	return componentRegistry.getComponent(type);
    }
}
