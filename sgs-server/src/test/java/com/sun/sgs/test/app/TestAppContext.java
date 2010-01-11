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

package com.sun.sgs.test.app;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.ManagerNotFoundException;
import com.sun.sgs.internal.InternalContext;
import com.sun.sgs.internal.ManagerLocator;
import com.sun.sgs.tools.test.FilteredNameRunner;

import org.junit.Test;
import org.junit.Assert;
import org.junit.runner.RunWith;

import org.easymock.EasyMock;

/**
 * Test the {@link AppContext} class
 */
@RunWith(FilteredNameRunner.class)
public class TestAppContext {
    
    //dummy AppContext
    private ManagerLocator managerLocator;
    private DataManager dataManager;
    private ChannelManager channelManager;
    private TaskManager taskManager;
    private Object arbitraryManager;
    
    /**
     * setup the dummy AppContext
     */
    private void initStableAppContext() {
        managerLocator = EasyMock.createMock(ManagerLocator.class);
        dataManager = EasyMock.createNiceMock(DataManager.class);
        channelManager = EasyMock.createNiceMock(ChannelManager.class);
        taskManager = EasyMock.createNiceMock(TaskManager.class);
        arbitraryManager = new Object();
        
        EasyMock.expect(managerLocator.getDataManager()).
                andReturn(dataManager);
        EasyMock.expect(managerLocator.getChannelManager()).
                andReturn(channelManager);
        EasyMock.expect(managerLocator.getTaskManager()).
                andReturn(taskManager);
        EasyMock.expect(managerLocator.getManager(Object.class)).
                andReturn(arbitraryManager);
        EasyMock.replay(managerLocator);
    }
    
    /**
     * setup the dummy AppContext with a ManagerLocator that is unstable
     * with its return values
     */
    private void initUnstableAppContext() {
        initStableAppContext();
        
        EasyMock.reset(managerLocator);
        
        EasyMock.expect(managerLocator.getDataManager()).
                andReturn(dataManager).andReturn(null);
        EasyMock.expect(managerLocator.getChannelManager()).
                andReturn(channelManager).andReturn(null);
        EasyMock.expect(managerLocator.getTaskManager()).
                andReturn(taskManager).andReturn(null);
        EasyMock.expect(managerLocator.getManager(Object.class)).
                andReturn(arbitraryManager).andReturn(null);
        EasyMock.replay(managerLocator);
    }
    
    private void initEmptyAppContext() {
        managerLocator = EasyMock.createMock(ManagerLocator.class);
        
        ManagerNotFoundException m = new ManagerNotFoundException("not found");
        
        EasyMock.expect(managerLocator.getDataManager()).
                andThrow(m);
        EasyMock.expect(managerLocator.getChannelManager()).
                andThrow(m);
        EasyMock.expect(managerLocator.getTaskManager()).
                andThrow(m);
        EasyMock.expect(managerLocator.getManager(Object.class)).
                andThrow(m);
        EasyMock.replay(managerLocator);
    }
    
    @Test(expected=IllegalStateException.class)
    public void testGetManagerLocatorBeforeInit() {
        InternalContext.getManagerLocator();
    }

    @Test(expected=ManagerNotFoundException.class)
    public void testGetDataManagerBeforeInit() {
        AppContext.getDataManager();
    }
    
    @Test(expected=ManagerNotFoundException.class)
    public void testGetTaskManagerBeforeInit() {
        AppContext.getTaskManager();
    }
    
    @Test(expected=ManagerNotFoundException.class)
    public void testGetChannelManagerBeforeInit() {
        AppContext.getChannelManager();
    }
    
    @Test(expected=ManagerNotFoundException.class)
    public void testGetArbitraryManagerBeforeInit() {
        AppContext.getManager(Object.class);
    }
    
    @Test
    public void testGetDataManagerAfterInit() {
        initStableAppContext();
        InternalContext.setManagerLocator(managerLocator);
        DataManager d = AppContext.getDataManager();
        
        Assert.assertSame(d, dataManager);
    }
    
    @Test
    public void testGetTaskManagerAfterInit() {
        initStableAppContext();
        InternalContext.setManagerLocator(managerLocator);
        TaskManager t = AppContext.getTaskManager();
        
        Assert.assertSame(t, taskManager);
    }
    
    @Test
    public void testGetChannelManagerAfterInit() {
        initStableAppContext();
        InternalContext.setManagerLocator(managerLocator);
        ChannelManager c = AppContext.getChannelManager();
        
        Assert.assertSame(c, channelManager);
    }
    
    @Test
    public void testGetArbitraryManagerAfterInit() {
        initStableAppContext();
        InternalContext.setManagerLocator(managerLocator);
        Object o = AppContext.getManager(Object.class);
        
        Assert.assertSame(o, arbitraryManager);
    }
    
    @Test
    public void testGetDataManagerWithUnstableManagerLocator() {
        initUnstableAppContext();
        InternalContext.setManagerLocator(managerLocator);
        
        DataManager d1 = AppContext.getDataManager();
        DataManager d2 = AppContext.getDataManager();
        
        Assert.assertSame(d1, dataManager);
        Assert.assertNull(d2);
    }
    
    @Test
    public void testGetChannelManagerWithUnstableManagerLocator() {
        initUnstableAppContext();
        InternalContext.setManagerLocator(managerLocator);
        
        ChannelManager c1 = AppContext.getChannelManager();
        ChannelManager c2 = AppContext.getChannelManager();
        
        Assert.assertSame(c1, channelManager);
        Assert.assertNull(c2);
    }
    
    @Test
    public void testGetTaskManagerWithUnstableManagerLocator() {
        initUnstableAppContext();
        InternalContext.setManagerLocator(managerLocator);
        
        TaskManager t1 = AppContext.getTaskManager();
        TaskManager t2 = AppContext.getTaskManager();
        
        Assert.assertSame(t1, taskManager);
        Assert.assertNull(t2);
    }
    
    @Test
    public void testGetArbitraryManagerWithUnstableManagerLocator() {
        initUnstableAppContext();
        InternalContext.setManagerLocator(managerLocator);
        
        Object o1 = AppContext.getManager(Object.class);
        Object o2 = AppContext.getManager(Object.class);
        
        Assert.assertSame(o1, arbitraryManager);
        Assert.assertNull(o2);
    }
    
    @Test(expected=ManagerNotFoundException.class)
    public void testGetDataManagerWithEmptyManagerLocator() {
        initEmptyAppContext();
        InternalContext.setManagerLocator(managerLocator);
        
        AppContext.getDataManager();
    }
    
    @Test(expected=ManagerNotFoundException.class)
    public void testGetTaskManagerWithEmptyManagerLocator() {
        initEmptyAppContext();
        InternalContext.setManagerLocator(managerLocator);
        
        AppContext.getTaskManager();
    }
    
    @Test(expected=ManagerNotFoundException.class)
    public void testGetChannelManagerWithEmptyManagerLocator() {
        initEmptyAppContext();
        InternalContext.setManagerLocator(managerLocator);
        
        AppContext.getChannelManager();
    }
    
    @Test(expected=IllegalStateException.class)
    public void testSetNullManagerLocator() {
        InternalContext.setManagerLocator(null);
        
        InternalContext.getManagerLocator();
    }
    
}
