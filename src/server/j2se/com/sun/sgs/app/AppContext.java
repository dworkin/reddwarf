
package com.sun.sgs.app;

import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.TimerManager;


/**
 * This interface provides access to the managers used within a specific
 * application context.
 * <p>
 * FIXME: we should figure out if this requires more detail (e.g., some
 * unique identitifer, the application's name, etc.) and whether access to
 * these managers should actually be protected. In the latter case, this
 * would presumably be done through a sub-interface, and the former detail
 * should still be exposed here.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public interface AppContext {

    /**
     * Returns the <code>ChannelManager</code> used by this Application.
     *
     * @return the available <code>ChannelManager</code>
     */
    public ChannelManager getChannelManager();

    /**
     * Returns the <code>DataManager</code> used by this Application.
     *
     * @return the available <code>DataManager</code>.
     */
    public DataManager getDataManager();

    /**
     * Returns the <code>TaskManager</code> used by this Application.
     *
     * @return the available <code>TaskManager</code>.
     */
    public TaskManager getTaskManager();

    /**
     * Returns the <code>TimerManager</code> used by this Application.
     *
     * @return the available <code>TimerManager</code>.
     */
    public TimerManager getTimerManager();

}
