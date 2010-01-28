/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.kernel;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.lang.reflect.Field;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@code ScheduledTaskImpl}, mainly state transition tests
 */
@RunWith(FilteredNameRunner.class)
public class TestScheduledTaskImpl {

    private ScheduledTaskImpl task;

    // empty Identity implementation used for the tests
    private Identity defaultIdentity = new Identity() {
        public String getName() { return ""; }
        public void notifyLoggedIn() {}
        public void notifyLoggedOut() {}
    };
    // empty KernelRunnable implementation used for tests
    private KernelRunnable runnable = new KernelRunnable() {
        public String getBaseTaskType() { return ""; }
        public void run() throws Exception { }
    };

    @Before
    public void setup() {
        ScheduledTaskImpl.Builder.setDefaultTimeout(100);
        task = new ScheduledTaskImpl.Builder(
                runnable, defaultIdentity, Priority.getDefaultPriority()).build();
    }

    @After
    public void tearDown() {
        task = null;
    }

    // using reflection, set the internal state of this test's task
    private void setTaskState(String state) throws Exception {
        Field stateField = ScheduledTaskImpl.class.getDeclaredField("state");
        stateField.setAccessible(true);
        Object[] states = stateField.getType().getEnumConstants();
        for (Object s : states) {
            if (s.toString().equals(state)) {
                stateField.set(task, s);
                return;
            }
        }
        throw new IllegalArgumentException(state + " not a valid State");
    }

    // using reflection, get the internal state of this test's task and
    // return it as a String
    private String getTaskState() throws Exception {
        Field stateField = ScheduledTaskImpl.class.getDeclaredField("state");
        stateField.setAccessible(true);
        return stateField.get(task).toString();
    }

    @Test
    public void setRunningTrueWhenRunnable() throws Exception {
        setTaskState("RUNNABLE");
        Assert.assertTrue(task.setRunning(true));
        Assert.assertEquals("RUNNING", getTaskState());
    }

    @Test
    public void setRunningTrueWhenRunning() throws Exception {
        setTaskState("RUNNING");
        Assert.assertFalse(task.setRunning(true));
        Assert.assertEquals("RUNNING", getTaskState());
    }

    @Test
    public void setRunningTrueWhenInterrupted() throws Exception {
        setTaskState("INTERRUPTED");
        Assert.assertTrue(task.setRunning(true));
        Assert.assertEquals("RUNNING", getTaskState());
    }

    @Test
    public void setRunningTrueWhenCompleted() throws Exception {
        setTaskState("COMPLETED");
        Assert.assertFalse(task.setRunning(true));
        Assert.assertEquals("COMPLETED", getTaskState());
    }

    @Test
    public void setRunningTrueWhenCancelled() throws Exception {
        setTaskState("CANCELLED");
        Assert.assertFalse(task.setRunning(true));
        Assert.assertEquals("CANCELLED", getTaskState());
    }

    @Test
    public void setRunningFalseWhenRunnable() throws Exception {
        setTaskState("RUNNABLE");
        Assert.assertFalse(task.setRunning(false));
        Assert.assertEquals("RUNNABLE", getTaskState());
    }

    @Test
    public void setRunningFalseWhenRunning() throws Exception {
        setTaskState("RUNNING");
        Assert.assertTrue(task.setRunning(false));
        Assert.assertEquals("RUNNABLE", getTaskState());
    }

    @Test
    public void setRunningFalseWhenInterrupted() throws Exception {
        setTaskState("INTERRUPTED");
        Assert.assertFalse(task.setRunning(false));
        Assert.assertEquals("INTERRUPTED", getTaskState());
    }

    @Test
    public void setRunningFalseWhenCompleted() throws Exception {
        setTaskState("COMPLETED");
        Assert.assertFalse(task.setRunning(false));
        Assert.assertEquals("COMPLETED", getTaskState());
    }

    @Test
    public void setRunningFalseWhenCancelled() throws Exception {
        setTaskState("CANCELLED");
        Assert.assertFalse(task.setRunning(false));
        Assert.assertEquals("CANCELLED", getTaskState());
    }

    @Test
    public void setInterruptedWhenRunnable() throws Exception {
        setTaskState("RUNNABLE");
        Assert.assertFalse(task.setInterrupted());
        Assert.assertEquals("RUNNABLE", getTaskState());
    }

    @Test
    public void setInterruptedWhenRunning() throws Exception {
        setTaskState("RUNNING");
        Assert.assertTrue(task.setInterrupted());
        Assert.assertEquals("INTERRUPTED", getTaskState());
    }

    @Test
    public void setInterruptedWhenInterrupted() throws Exception {
        setTaskState("INTERRUPTED");
        Assert.assertFalse(task.setInterrupted());
        Assert.assertEquals("INTERRUPTED", getTaskState());
    }

    @Test
    public void setInterruptedWhenCompleted() throws Exception {
        setTaskState("COMPLETED");
        Assert.assertFalse(task.setInterrupted());
        Assert.assertEquals("COMPLETED", getTaskState());
    }

    @Test
    public void setInterruptedWhenCancelled() throws Exception {
        setTaskState("CANCELLED");
        Assert.assertFalse(task.setInterrupted());
        Assert.assertEquals("CANCELLED", getTaskState());
    }

    @Test
    public void setDoneWhenRunnable() throws Exception {
        setTaskState("RUNNABLE");
        task.setDone(null);
        Assert.assertEquals("RUNNABLE", getTaskState());
    }

    @Test
    public void setDoneWhenRunning() throws Exception {
        setTaskState("RUNNING");
        task.setDone(null);
        Assert.assertEquals("COMPLETED", getTaskState());
    }

    @Test
    public void setDoneWhenInterrupted() throws Exception {
        setTaskState("INTERRUPTED");
        task.setDone(null);
        Assert.assertEquals("COMPLETED", getTaskState());
    }

    @Test
    public void setDoneWhenCompleted() throws Exception {
        setTaskState("COMPLETED");
        task.setDone(null);
        Assert.assertEquals("COMPLETED", getTaskState());
    }

    @Test
    public void setDoneWhenCancelled() throws Exception {
        setTaskState("CANCELLED");
        task.setDone(null);
        Assert.assertEquals("CANCELLED", getTaskState());
    }

    @Test
    public void cancelAndDontBlockWhenRunnable() throws Exception {
        setTaskState("RUNNABLE");
        Assert.assertTrue(task.cancel(false));
        Assert.assertEquals("CANCELLED", getTaskState());
    }

    @Test
    public void cancelAndDontBlockWhenRunning() throws Exception {
        setTaskState("RUNNING");
        Assert.assertFalse(task.cancel(false));
        Assert.assertEquals("RUNNING", getTaskState());
    }

    @Test
    public void cancelAndDontBlockWhenInterrupted() throws Exception {
        setTaskState("INTERRUPTED");
        Assert.assertTrue(task.cancel(false));
        Assert.assertEquals("CANCELLED", getTaskState());
    }

    @Test
    public void cancelAndDontBlockWhenCompleted() throws Exception {
        setTaskState("COMPLETED");
        Assert.assertFalse(task.cancel(false));
        Assert.assertEquals("COMPLETED", getTaskState());
    }

    @Test
    public void cancelAndDontBlockWhenCancelled() throws Exception {
        setTaskState("CANCELLED");
        Assert.assertFalse(task.cancel(false));
        Assert.assertEquals("CANCELLED", getTaskState());
    }

    @Test
    public void cancelAndBlockWhenRunnable() throws Exception {
        setTaskState("RUNNABLE");
        Assert.assertTrue(task.cancel(true));
        Assert.assertEquals("CANCELLED", getTaskState());
    }

    @Test(timeout=500)
    public void cancelAndBlockWhenRunningThenComplete() throws Exception {
        setTaskState("RUNNING");
        new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(200);
                } catch (Exception e) {}

                task.setDone(null);
            }
        }).start();
        Assert.assertFalse(task.cancel(true));
        Assert.assertEquals("COMPLETED", getTaskState());
    }

    @Test(timeout=500)
    public void cancelAndBlockWhenRunningThenRunnable() throws Exception {
        setTaskState("RUNNING");
        new Thread(new Runnable() {
            public void run() {
                try {
                    Thread.sleep(200);
                } catch (Exception e) {}

                task.setRunning(false);
            }
        }).start();
        Assert.assertTrue(task.cancel(true));
        Assert.assertEquals("CANCELLED", getTaskState());
    }

    @Test
    public void cancelAndBlockWhenInterrupted() throws Exception {
        setTaskState("INTERRUPTED");
        Assert.assertTrue(task.cancel(true));
        Assert.assertEquals("CANCELLED", getTaskState());
    }

    @Test
    public void cancelAndBlockWhenCompleted() throws Exception {
        setTaskState("COMPLETED");
        Assert.assertFalse(task.cancel(true));
        Assert.assertEquals("COMPLETED", getTaskState());
    }

    @Test
    public void cancelAndBlockWhenCancelled() throws Exception {
        setTaskState("CANCELLED");
        Assert.assertFalse(task.cancel(true));
        Assert.assertEquals("CANCELLED", getTaskState());
    }
}
