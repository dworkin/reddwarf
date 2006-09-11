
package com.sun.sgs.kernel.impl;

import com.sun.sgs.kernel.AppContext;

import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.TimerManager;


/**
 * This is a simple implementation of <code>AppContext</code>.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class SimpleAppContext implements AppContext {

    // the actual manager instances
    private ChannelManager channelManager;
    private DataManager dataManager;
    private TaskManager taskManager;
    private TimerManager timerManager;

    /**
     * Creates a new instance of <code>SimpleAppContext</code>.
     *
     * @param channelManager the channel manager used in this app context
     * @param dataManager the data manager used in this app context
     * @param taskManager the task manager used in this app context
     * @param timerManager the timer manager used in this app context
     */
    public SimpleAppContext(ChannelManager channelManager,
                            DataManager dataManager, TaskManager taskManager,
                            TimerManager timerManager) {
        this.channelManager = channelManager;
        this.dataManager = dataManager;
        this.taskManager = taskManager;
        this.timerManager = timerManager;
    }

    /**
     * Returns the <code>ChannelManager</code> used by this Application.
     *
     * @return the available <code>ChannelManager</code>
     */
    public ChannelManager getChannelManager() {
        return channelManager;
    }

    /**
     * Returns the <code>DataManager</code> used by this Application.
     *
     * @return the available <code>DataManager</code>.
     */
    public DataManager getDataManager() {
        return dataManager;
    }

    /**
     * Returns the <code>TaskManager</code> used by this Application.
     *
     * @return the available <code>TaskManager</code>.
     */
    public TaskManager getTaskManager() {
        return taskManager;
    }

    /**
     * Returns the <code>TimerManager</code> used by this Application.
     *
     * @return the available <code>TimerManager</code>.
     */
    public TimerManager getTimerManager() {
        return timerManager;
    }

}
