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

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.AppListener;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.service.DataService;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.impl.sharedutil.Objects;
import com.sun.sgs.tools.test.FilteredNameRunner;
import org.junit.Assert;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import java.io.Serializable;
import java.util.Properties;

/**
 * Tests that the {@code Kernel} properly boots up both {@code AppListener}s
 * that implement {@code ManagedObject} as well as those that do not.
 */
@RunWith(FilteredNameRunner.class)
public class TestKernelAppListeners {
    
    private SgsTestNode serverNode;
    private TransactionScheduler txnScheduler;
    private Identity taskOwner;
    private DataService dataService;
    
    private void startupServer(Class<?> listener) throws Exception {
        serverNode = new SgsTestNode("TestKernelAppListeners", null,
                                     SgsTestNode.getDefaultProperties(
                                     "TestKernelAppListeners", null, listener));
        txnScheduler = serverNode.getSystemRegistry().getComponent(
                TransactionScheduler.class);
	taskOwner = serverNode.getProxy().getCurrentOwner();
	dataService = serverNode.getDataService();
    }
    
    @After
    public void shutdownServer() throws Exception {
        serverNode.shutdown(true);
    }
    
    @Test
    public void testManagedAppListener() throws Exception {
        startupServer(ManagedAppListener.class);
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {

		ManagedAppListener listener = 
                        (ManagedAppListener) dataService.getServiceBinding(
                        StandardProperties.APP_LISTENER);
                Assert.assertEquals(listener.getState(), new Integer(1));

	    }
	}, taskOwner);
    }
    
    @Test
    public void testUnmanagedAppListener() throws Exception {
        startupServer(UnmanagedAppListener.class);
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
	    public void run() throws Exception {

                ManagedSerializable<AppListener> obj =
                        Objects.uncheckedCast(dataService.getServiceBinding(
                                              StandardProperties.APP_LISTENER));
                UnmanagedAppListener listener =
                        (UnmanagedAppListener) obj.get();
                Assert.assertEquals(listener.getState(), new Integer(1));

	    }
	}, taskOwner);
    }

    public static class ManagedAppListener implements AppListener,
                                                       ManagedObject,
                                                       Serializable {
        private Integer state;
        
        public void initialize(Properties props) {
            state = new Integer(1);
        }
        
        public ClientSessionListener loggedIn(ClientSession session) {
            return null;
        }
        
        public Integer getState() {
            return state;
        }
    }
    
    public static class UnmanagedAppListener implements AppListener,
                                                       Serializable {
        private ManagedReference<ManagedSerializable<Integer>> state;
        
        public void initialize(Properties props) {
            ManagedSerializable<Integer> i = 
                    new ManagedSerializable<Integer>(1);
            state = AppContext.getDataManager().createReference(i);
        }
        
        public ClientSessionListener loggedIn(ClientSession session) {
            return null;
        }
        
        public Integer getState() {
            return state.get().get();
        }
    }
}
