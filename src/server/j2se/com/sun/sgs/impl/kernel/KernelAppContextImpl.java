
package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagerNotFoundException;
import com.sun.sgs.app.TaskManager;

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelAppContext;

import com.sun.sgs.service.Service;

import java.util.MissingResourceException;


/**
 * This is a simple implementation of <code>KernelAppContext</code> used by
 * the kernel for managing application context state.
 *
 * @since 1.0
 * @author Seth Proctor
 */
class KernelAppContextImpl implements KernelAppContext {

    // the application's name and cached hash code
    private final String applicationName;
    private final int applicationCode;

    // the managers available in this context
    private final ComponentRegistry managerComponents;

    // the services used in this context
    private ComponentRegistry serviceComponents = null;

    // the three standard managers, which are cached since they are used
    // extremely frequently
    private final ChannelManager channelManager;
    private final DataManager dataManager;
    private final TaskManager taskManager;

    /**
     * Creates an instance of <code>KernelAppContextImpl</code>.
     *
     * @param applicationName the name of the application represented by
     *                        this context
     * @param managerComponents the managers available in this context
     *
     * @throws MissingResourceException if the <code>ChannelManager</code>,
     *                                  <code>DataManager</code>, or
     *                                  <code>TaskManager</code> is missing
     *                                  from the provided components
     */
    KernelAppContextImpl(String applicationName,
                         ComponentRegistry managerComponents) {
        this.applicationName = applicationName;
        this.managerComponents = managerComponents;

        // the hash code is the hash of the application name, which never
        // changes, so the hash code gets pre-cached here
        applicationCode = applicationName.hashCode();

        // pre-fetch the three standard managers
        // FIXME: add this back in when we have support for channels
        /*channelManager = managerComponents.
          getComponent(ChannelManager.class);*/
        channelManager = null;
        dataManager = managerComponents.getComponent(DataManager.class);
        taskManager = managerComponents.getComponent(TaskManager.class);
    }

    /**
     * Returns the <code>ChannelManager</code> used in this context.
     *
     * @return the context's <code>ChannelManager</code>
     */
    ChannelManager getChannelManager() {
        return channelManager;
    }

    /**
     * Returns the <code>DataManager</code> used in this context.
     *
     * @return the context's <code>DataManager</code>
     */
    DataManager getDataManager() {
        return dataManager;
    }

    /**
     * Returns the <code>TaskManager</code> used in this context.
     *
     * @return the context's <code>TaskManager</code>
     */
    TaskManager getTaskManager() {
        return taskManager;
    }

    /**
     * Returns a manager based on the given type. If the manager type is
     * unknown, or if there is more than one manager of the given type,
     * <code>ManagerNotFoundException</code> is thrown. This may be used
     * to find any available manager, including the three standard
     * managers.
     *
     * @param type the <code>Class</code> of the requested manager
     *
     * @return the requested manager
     *
     * @throws ManagerNotFoundException if there wasn't exactly one match to
     *                                  the requested type
     */
    <T> T getManager(Class<T> type) {
        try {
            return managerComponents.getComponent(type);
        } catch (MissingResourceException mre) {
            throw new ManagerNotFoundException("couldn't find manager: " +
                                               type.getName());
        }
    }

    /**
     * Sets the <code>Service</code>s available in this context. This is done
     * as part of application startup. This method may only be called once
     * for the lifetime of a given context.
     *
     * @param serviceComponents the <code>Service</code>s used in this context
     *
     * @throws IllegalStateException if the <code>Service</code>s have already
     *                               been set
     */
    void setServices(ComponentRegistry serviceComponents) {
        if (this.serviceComponents != null)
            throw new IllegalStateException("Services have already been set");
        this.serviceComponents = serviceComponents;
    }

    /**
     * Returns a <code>Service</code> based on the given type. If the type is
     * unknown, or if there is more than one <code>Service</code> of the
     * given type, <code>MissingResourceException</code> is thrown. This is
     * the only way to resolve service components directly, and should be
     * used with care, as <code>Service</code>s should not be resolved and
     * invoked directly outside of a transactional context.
     *
     * @param type the <code>Class</code> of the requested <code>Service</code>
     *
     * @return the requested <code>Service</code>
     *
     * @throws MissingResourceException if there wasn't exactly one match to
     *                                  the requested type
     */
    <T extends Service> T getService(Class<T> type) {
        return serviceComponents.getComponent(type);
    }

    /**
     * Returns a unique representation of this context, in this case the
     * name of the application.
     *
     * @return a <code>String</code> representation of the context
     */
    public String toString() {
        return applicationName;
    }

    /**
     * Returns <code>true</code> if the provided object is an instance of
     * <code>KernelAppContextImpl</code> that represents the same application
     * context.
     *
     * @param o an instance of <code>KernelAppContextImpl</code>
     *
     * @return <code>true</code> if the provided object represents the same
     *         context as this object, <code>false</code> otherwise
     */
    public boolean equals(Object o) {
        if (! (o instanceof KernelAppContextImpl))
            return false;

        KernelAppContextImpl other = (KernelAppContextImpl)o;

        return (other.applicationCode == applicationCode);
    }

    /**
     * @{inheritDoc}
     */
    public int hashCode() {
        return applicationCode;
    }

}
