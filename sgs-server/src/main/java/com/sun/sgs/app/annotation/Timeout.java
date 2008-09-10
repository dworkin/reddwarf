/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.app.annotation;

import com.sun.sgs.app.Task;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation for the {@code run} method of a {@link Task} which sets the
 * timeout duration in milliseconds.  Using this annotation will override the
 * {@value TransactionCoordinator#TXN_TIMEOUT_PROPERTY} property only for the
 * specific task.  If the this annotation is supplied with the {@link Unbounded}
 * annoation, the task will ignore the timeout specified by this annotation.
 *
 * <p>
 *
 * <b>This annotation should be used sparingly and only when necessary</b>.
 * Overriding the default timeout can have a negative impact on the latency and
 * throughput of the system.  This annotation is only appropriate in a limited
 * number of situation when task logic cannot be broken up into separate tasks
 * and the resulting combined logic is not designed to finish within the default
 * timeout.
 *
 * <p>
 *
 * Developers may also use this to shorten the task timeout.
 *
 * @see TaskManager
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Timeout {
    
    /**
     * Specifies the length of the timeout for the {@link Task} to which this
     * annotation has been applied.  This value must be positive for the task to
     * be scheduled.
     *
     * @return the length of the timeout in milliseconds
     */
    long duration();
}