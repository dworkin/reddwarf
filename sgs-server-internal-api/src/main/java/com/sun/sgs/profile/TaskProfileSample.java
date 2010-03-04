/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.profile;

/**
 * A profile sample which provides information to {@link ProfileReport}s.
 * <p>
 * If data is added to the sample during a given task, the {@code ProfileReport}
 * for that task will include the changes made, and exclude changes made while
 * running other tasks.
 */
public interface TaskProfileSample extends ProfileSample {

    /** 
     * {@inheritDoc}
     * 
     * @throws IllegalStateException if this is called outside the scope
     *                               of a task run through the scheduler
     */
    void addSample(long value);
}
