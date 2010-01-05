/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.test.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.NullSerializationHook;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.data.ManagedReferenceFactory;
import com.sun.sgs.service.data.SerializationHook;
import com.sun.sgs.service.data.SerializationHookFactory;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(FilteredNameRunner.class)
public class TestCreatingManagedReferencesDuringSerialization extends Assert {

    private SgsTestNode serverNode;
    private TransactionScheduler txnScheduler;
    private DataServiceImpl dataService;
    private Identity taskOwner;
    private ManagedReferenceFactory referenceFactory;

    @Before
    public void setUp() throws Exception {
        serverNode = new SgsTestNode("TestCreatingManagedReferencesDuringSerialization", null, null);
        txnScheduler = serverNode.getSystemRegistry().getComponent(TransactionScheduler.class);
        dataService = (DataServiceImpl) serverNode.getDataService();
        taskOwner = serverNode.getProxy().getCurrentOwner();
    }

    @After
    public void tearDown() throws Exception {
        serverNode.shutdown(true);
    }

    private void useSerializationHook(final SerializationHook hook) {
        dataService.setSerializationHookFactory(new SerializationHookFactory() {
            @Override
            public SerializationHook create(ManagedReferenceFactory factory) {
                referenceFactory = factory;
                return hook;
            }
        });
    }

    @Test
    public void managed_references_to_known_objects_can_be_created_during_serialization() throws Exception {
        final AtomicBoolean referenceWasCreated = new AtomicBoolean(false);

        useSerializationHook(new NullSerializationHook() {
            public Object replaceObject(Object topLevelObject, Object object) {
                if (isMyObjectIdentifiedBy(object, "known")) {
                    ManagedReference<?> ref = referenceFactory.createReference(object);
                    referenceWasCreated.set(ref != null);
                }
                return object;
            }
        });

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                dataService.createReference(new MyObject("known"));
            }
        }, taskOwner);

        assertTrue(referenceWasCreated.get());
    }

    @Test
    public void managed_references_to_new_objects_can_be_created_during_serialization() throws Exception {
        final AtomicBoolean referenceWasCreated = new AtomicBoolean(false);

        useSerializationHook(new NullSerializationHook() {
            public Object replaceObject(Object topLevelObject, Object object) {
                if (isMyObjectIdentifiedBy(object, "known")) {
                    MyObject newObject = new MyObject("new");
                    ManagedReference<?> ref = referenceFactory.createReference(newObject);
                    referenceWasCreated.set(ref != null);
                }
                return object;
            }
        });

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                dataService.createReference(new MyObject("known"));
            }
        }, taskOwner);

        assertTrue(referenceWasCreated.get());
    }

    @Test
    public void cyclic_managed_object_graphs_can_be_created_during_serialization_without_causing_infinite_recursion() throws Exception {
        final AtomicInteger numberOfReferencesCreated = new AtomicInteger(0);

        useSerializationHook(new NullSerializationHook() {
            public Object replaceObject(Object topLevelObject, Object object) {
                if (object instanceof MyObject) {
                    wrapOtherToManagedReference((MyObject) object);
                }
                return object;
            }

            private void wrapOtherToManagedReference(MyObject object) {
                ManagedReference<?> otherRef = referenceFactory.createReference(object.getOther());
                object.setOther(otherRef);
                numberOfReferencesCreated.incrementAndGet();
            }
        });

        txnScheduler.runTask(new TestAbstractKernelRunnable() {
            public void run() throws Exception {
                MyObject a = new MyObject("a");
                MyObject b = new MyObject("b");
                a.setOther(b);
                b.setOther(a);
                dataService.createReference(a);
            }
        }, taskOwner);

        assertEquals(2, numberOfReferencesCreated.get());
    }


    private static boolean isMyObjectIdentifiedBy(Object object, String name) {
        return object instanceof MyObject
                && ((MyObject) object).getName().equals(name);
    }

    private static class MyObject implements ManagedObject, Serializable {

        private final String name;
        private Object other;

        public MyObject(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public Object getOther() {
            return other;
        }

        public void setOther(Object other) {
            this.other = other;
        }
    }
}
