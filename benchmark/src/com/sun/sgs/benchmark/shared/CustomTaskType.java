/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.shared;

/**
 * This class defines the different "types" of custom tasks that can be created
 * in the benchmarking application.  Tasks are created on the server with a set
 * of commands to execute; task type differ solely in the order in which the
 * commands are executed.
 *
 * parallel - a seperate task is spawned for each individual command and all are
 *            queued with the TaskManager at the same time
 *
 * serial   - a seperate task is spawned for each individual command, but
 *            the tasks are ordered and each is not queued with the TaskManager
 *            until the previous task in line has completed
 *
 * singular - all tasks are executed sequentially in a single task
 *
 */
public enum CustomTaskType {
    /** Enum types */
    PARALLEL,
    SERIAL,
    SINGULAR;
}
