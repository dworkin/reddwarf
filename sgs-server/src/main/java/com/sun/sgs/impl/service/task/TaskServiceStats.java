/*
 * Copyright 2008 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.task;

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
    
    TaskServiceStats(ProfileCollector collector, String name) {
        ProfileConsumer consumer = collector.getConsumer(name);
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
    public long getScheduleNonDurableTaskCount() {
        return ((AggregateProfileOperation) scheduleNDTaskOp).getCount();
    }

    /** {@inheritDoc} */
    public long getScheduleNonDurableTaskDelayedCount() {
        return ((AggregateProfileOperation) scheduleNDTaskDelayedOp).getCount();
    }

    /** {@inheritDoc} */
    public long getSchedulePeriodicTaskCount() {
        return ((AggregateProfileOperation) scheduleTaskOp).getCount();
    }

    /** {@inheritDoc} */
    public long getScheduleTaskCount() {
        return ((AggregateProfileOperation) scheduleTaskDelayedOp).getCount();
    }

    /** {@inheritDoc} */
    public long getScheuldeDelayedTaskCount() {
        return ((AggregateProfileOperation) scheduleTaskPeriodicOp).getCount();
    }

}
