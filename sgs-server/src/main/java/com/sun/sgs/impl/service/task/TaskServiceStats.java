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
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import com.sun.sgs.profile.ProfileOperation;
import com.sun.sgs.service.ProfileService;

/**
 *
 * The Statistics MBean object for the task service.
 */
public class TaskServiceStats implements TaskServiceMXBean {

    // the profiled operations
    final ProfileOperation scheduleNDTaskOp;
    final ProfileOperation scheduleNDTaskDelayedOp;
    final ProfileOperation scheduleTaskOp;
    final ProfileOperation scheduleTaskDelayedOp;
    final ProfileOperation scheduleTaskPeriodicOp;
    
    TaskServiceStats(ProfileService profileService) {
        ProfileConsumer consumer =
            profileService.getProfileCollector().
                registerProfileProducer(TaskServiceImpl.NAME);

        ProfileLevel level = ProfileLevel.MAX;
        scheduleNDTaskOp =
            consumer.registerOperation("scheduleNonDurableTask", true, level);
        scheduleNDTaskDelayedOp =
            consumer.registerOperation("scheduleNonDurableTaskDelayed", 
                                       true, level);
        scheduleTaskOp = 
            consumer.registerOperation("scheduleTask", true, level);
        scheduleTaskDelayedOp =
            consumer.registerOperation("scheduleDelayedTask", true, level);
        scheduleTaskPeriodicOp =
            consumer.registerOperation("schedulePeriodicTask", true, level);
    }
    
    /** {@inheritDoc} */
    public long getScheduleNonDurableTaskCount() {
        return scheduleNDTaskOp.getCount();
    }

    /** {@inheritDoc} */
    public long getScheduleNonDurableTaskDelayedCount() {
        return scheduleNDTaskDelayedOp.getCount();
    }

    /** {@inheritDoc} */
    public long getSchedulePeriodicTaskCount() {
        return scheduleTaskOp.getCount();
    }

    /** {@inheritDoc} */
    public long getScheduleTaskCount() {
        return scheduleTaskDelayedOp.getCount();
    }

    /** {@inheritDoc} */
    public long getScheuldeDelayedTaskCount() {
        return scheduleTaskPeriodicOp.getCount();
    }

}
