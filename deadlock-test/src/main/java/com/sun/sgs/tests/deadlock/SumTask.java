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

package com.sun.sgs.tests.deadlock;

import java.io.Serializable;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ManagedReference;

/**
 *
 */
public class SumTask implements Task, Serializable {
    
    private ManagedReference<ManagedInteger>[] integers;
    private ManagedReference<ManagedInteger> update;
    
    public SumTask(ManagedReference<ManagedInteger>[] integers,
                   ManagedReference<ManagedInteger> update) {
        this.integers = integers;
        this.update = update;
    }

    @Override
    public void run() throws Exception {
        int sum = 0;
        for(ManagedReference<ManagedInteger> i : integers) {
            sum += i.get().getValue();
        }
        update.getForUpdate().setSum(sum);
        update.getForUpdate().setValue();
        
        AppContext.getTaskManager().scheduleTask(this);
    }

}
