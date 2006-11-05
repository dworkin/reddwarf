package com.sun.gi.logic.impl;

import java.lang.reflect.Method;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;
import com.sun.gi.transition.AppContext;
import com.sun.gi.transition.impl.TaskList;
import com.sun.gi.transition.impl.TaskManagerImpl;

public class BootstrapImpl implements GLO, SimBoot {
    private static final long serialVersionUID = 1L;

    public void boot(GLOReference thisGLO, boolean firstBoot) {
        restartTimers();
        bootUserland(firstBoot);
    }
    
    private void restartTimers() {
        SimTask simTask = SimTask.getCurrent();
        GLOReference<TaskList> taskListRef =
            simTask.findGLO(TaskManagerImpl.TASKLISTNAME);
        
        if (taskListRef == null) {
            taskListRef =
                simTask.createGLO(new TaskList(),
                        TaskManagerImpl.TASKLISTNAME);
        }
        taskListRef.get(simTask).restartTasks();
    }
    
    private void bootUserland(boolean firstBoot) {
        SimTask simTask = SimTask.getCurrent();
        GLOReference<SimBoot> bootRef = simTask.findGLO("BOOT");
        
        bootRef.get(simTask).boot(bootRef, firstBoot);
    }

}
