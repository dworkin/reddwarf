
package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagerNotFoundException;
import com.sun.sgs.app.TaskManager;

/**
 * This class is used to resolve the state associated with the current task's
 * context.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public final class ContextResolver {

    // the thread local that caches the context, so we don't need to cast
    // the task thread with each query to find the context
    private static ThreadLocal<KernelAppContextImpl> context =
        new ThreadLocal<KernelAppContextImpl>() {
            protected KernelAppContextImpl initialValue() {
                // FIXME: this should use the kernel context, when that's
                // been clearly defined
                return null;
            }
        };

    /**
     * Returns the <code>ChannelManager</code> used in this context.
     *
     * @return the context's <code>ChannelManager</code>.
     */
    public static ChannelManager getChannelManager() {
        return context.get().getChannelManager();
    }

    /**
     * Returns the <code>DataManager</code> used in this context.
     *
     * @return the context's <code>DataManager</code>.
     */
    public static DataManager getDataManager() {
        return context.get().getDataManager();
    }

    /**
     * Returns the <code>TaskManager</code> used in this context.
     *
     * @return the context's <code>TaskManager</code>.
     */
    public static TaskManager getTaskManager() {
        return context.get().getTaskManager();
    }

    /**
     * Returns the manager in this context that matches the given type.
     *
     * @return the matching manager
     *
     * @throws ManagerNotFoundException if no manager is found that matches
     *                                  the given type
     */
    public static <T> T getManager(Class<T> type) {
        return context.get().getManager(type);
    }

    /**
     * Package-private method used to set the context. This is called each
     * time the <code>TaskHandler</code> is invoked to run under a new owner.
     *
     * @param appContext the current context
     */
    static void setContext(KernelAppContextImpl appContext) {
        context.set(appContext);
    }

} 
