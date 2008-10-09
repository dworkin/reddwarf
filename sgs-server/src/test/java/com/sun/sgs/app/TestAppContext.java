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

package com.sun.sgs.app;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Assert;

import org.easymock.EasyMock;

/**
 * Test the {@link AppContext} class
 */
public class TestAppContext {
    
    //dummy AppContext
    private ManagerLocator managerLocator;
    private DataManager dataManager;
    private ChannelManager channelManager;
    private TaskManager taskManager;
    private Object arbitraryManager;
    
    //system property
    private String saved;
    
    @Before
    public void saveCurrentProperty() {
        saved = System.getProperty("com.sun.sgs.app.AppContext.resetAllowed");
    }
    
    @After
    public void resetOriginalProperty() {
        if(saved == null)
            System.clearProperty("com.sun.sgs.app.AppContext.resetAllowed");
        else
            System.setProperty("com.sun.sgs.app.AppContext.resetAllowed", saved);
    }
    
    /**
     * Resets the private static state of the AppContext to 
     * its original state using Reflection.  This is not ideal as it requires 
     * knowledge of the
     * internal state of AppContext.  Since it is static, however, we have
     * few alternatives.
     */
    @After
    public void resetAppContextState() throws Exception {
        
        Field[] allFields = AppContext.class.getDeclaredFields();
       
        for(Field f : allFields) {
            int modifiers = f.getModifiers();
            if(Modifier.isPrivate(modifiers) && Modifier.isStatic(modifiers)) {
                f.setAccessible(true);
                f.set(AppContext.class, null);
            }
        }
    }
    
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
        AppContext.setManagerLocator(managerLocator);
        DataManager d = AppContext.getDataManager();
        
        Assert.assertSame(d, dataManager);
    }
    
    @Test
    public void testGetTaskManagerAfterInit() {
        initStableAppContext();
        AppContext.setManagerLocator(managerLocator);
        TaskManager t = AppContext.getTaskManager();
        
        Assert.assertSame(t, taskManager);
    }
    
    @Test
    public void testGetChannelManagerAfterInit() {
        initStableAppContext();
        AppContext.setManagerLocator(managerLocator);
        ChannelManager c = AppContext.getChannelManager();
        
        Assert.assertSame(c, channelManager);
    }
    
    @Test
    public void testGetArbitraryManagerAfterInit() {
        initStableAppContext();
        AppContext.setManagerLocator(managerLocator);
        Object o = AppContext.getManager(Object.class);
        
        Assert.assertSame(o, arbitraryManager);
    }
    
    @Test
    public void testGetDataManagerWithUnstableManagerLocator() {
        initUnstableAppContext();
        AppContext.setManagerLocator(managerLocator);
        
        DataManager d1 = AppContext.getDataManager();
        DataManager d2 = AppContext.getDataManager();
        
        Assert.assertSame(d1, dataManager);
        Assert.assertNull(d2);
    }
    
    @Test
    public void testGetChannelManagerWithUnstableManagerLocator() {
        initUnstableAppContext();
        AppContext.setManagerLocator(managerLocator);
        
        ChannelManager c1 = AppContext.getChannelManager();
        ChannelManager c2 = AppContext.getChannelManager();
        
        Assert.assertSame(c1, channelManager);
        Assert.assertNull(c2);
    }
    
    @Test
    public void testGetTaskManagerWithUnstableManagerLocator() {
        initUnstableAppContext();
        AppContext.setManagerLocator(managerLocator);
        
        TaskManager t1 = AppContext.getTaskManager();
        TaskManager t2 = AppContext.getTaskManager();
        
        Assert.assertSame(t1, taskManager);
        Assert.assertNull(t2);
    }
    
    @Test
    public void testGetArbitraryManagerWithUnstableManagerLocator() {
        initUnstableAppContext();
        AppContext.setManagerLocator(managerLocator);
        
        Object o1 = AppContext.getManager(Object.class);
        Object o2 = AppContext.getManager(Object.class);
        
        Assert.assertSame(o1, arbitraryManager);
        Assert.assertNull(o2);
    }
    
    @Test(expected=AppContextException.class)
    public void testInvalidResetManagerLocator() {
        initStableAppContext();
        ManagerLocator m1 = managerLocator;
        
        initStableAppContext();
        ManagerLocator m2 = managerLocator;
        
        AppContext.setManagerLocator(m1);
        AppContext.setManagerLocator(m2);
    }
    
    @Test
    public void testValidResetManagerLocator() {
        System.setProperty("com.sun.sgs.app.AppContext.resetAllowed", "true");
        
        initStableAppContext();
        ManagerLocator m1 = managerLocator;
        
        initStableAppContext();
        ManagerLocator m2 = managerLocator;
        
        AppContext.setManagerLocator(m1);
        AppContext.setManagerLocator(m2);
    }
    
    @Test
    public void testEmptyManagerLocator() {
        initEmptyAppContext();
        AppContext.setManagerLocator(managerLocator);
    }
    
    @Test(expected=ManagerNotFoundException.class)
    public void testGetDataManagerWithEmptyManagerLocator() {
        initEmptyAppContext();
        AppContext.setManagerLocator(managerLocator);
        
        AppContext.getDataManager();
    }
    
    @Test(expected=ManagerNotFoundException.class)
    public void testGetTaskManagerWithEmptyManagerLocator() {
        initEmptyAppContext();
        AppContext.setManagerLocator(managerLocator);
        
        AppContext.getTaskManager();
    }
    
    @Test(expected=ManagerNotFoundException.class)
    public void testGetChannelManagerWithEmptyManagerLocator() {
        initEmptyAppContext();
        AppContext.setManagerLocator(managerLocator);
        
        AppContext.getChannelManager();
    }
    
}
