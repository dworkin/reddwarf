/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.service.task;

import com.sun.sgs.impl.profile.ProfileCollectorImpl;
import com.sun.sgs.management.TaskServiceMXBean;
import com.sun.sgs.profile.AggregateProfileOperation;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileConsumer.ProfileDataType;
import com.sun.sgs.profile.ProfileOperation;

/**
 *
 * The Statistics MBean object for the task service.
 */
class TaskServiceStats implements TaskServiceMXBean {

    // the profiled operations
    final ProfileOperation scheduleNDTaskOp;
    final ProfileOperation scheduleNDTaskDelayedOp;
    final ProfileOperation scheduleTaskOp;
    final ProfileOperation scheduleTaskDelayedOp;
    final ProfileOperation scheduleTaskPeriodicOp;
    
    TaskServiceStats(ProfileCollector collector) {
        ProfileConsumer consumer = 
            collector.getConsumer(ProfileCollectorImpl.CORE_CONSUMER_PREFIX + 
                                  "TaskService");
        ProfileLevel level = ProfileLevel.MAX;
        ProfileDataType type = ProfileDataType.TASK_AND_AGGREGATE;
        
        scheduleNDTaskOp =
            consumer.createOperation("scheduleNonDurableTask", type, level);
        scheduleNDTaskDelayedOp =
            consumer.createOperation("scheduleNonDurableTaskDelayed", 
                                       type, level);
        scheduleTaskOp = 
            consumer.createOperation("scheduleTask", type, level);
        scheduleTaskDelayedOp =
            consumer.createOperation("scheduleDelayedTask", type, level);
        scheduleTaskPeriodicOp =
            consumer.createOperation("schedulePeriodicTask", type, level);
    }
    
    /** {@inheritDoc} */
    public long getScheduleNonDurableTaskCalls() {
        return ((AggregateProfileOperation) scheduleNDTaskOp).getCount();
    }

    /** {@inheritDoc} */
    public long getScheduleNonDurableTaskDelayedCalls() {
        return ((AggregateProfileOperation) scheduleNDTaskDelayedOp).getCount();
    }

    /** {@inheritDoc} */
    public long getSchedulePeriodicTaskCalls() {
        return ((AggregateProfileOperation) scheduleTaskOp).getCount();
    }

    /** {@inheritDoc} */
    public long getScheduleTaskCalls() {
        return ((AggregateProfileOperation) scheduleTaskDelayedOp).getCount();
    }

    /** {@inheritDoc} */
    public long getScheduleDelayedTaskCalls() {
        return ((AggregateProfileOperation) scheduleTaskPeriodicOp).getCount();
    }

}
