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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation for the {@code run} method of a {@link Task} which causes that
 * task to run with the unbounded timeout duration.  The task duration is still
 * bounded by the value of the {@value
 * TransactionCoordinator#TXN_UNBOUNDED_TIMEOUT_PROPERTY} property.  If the this
 * annotation is supplied with the {@link Timeout} annoation, the task will
 * ignore the timeout specified and run unbounded.
 *
 * <p>
 *
 * <b>This annotation should be used sparingly and only when necessary</b>.
 * This annotation is only appropriate for tasks that will not conflict with any
 * other running tasks.
 *
 * @see TaskManager
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Unbounded {

}