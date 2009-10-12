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

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test booting the {@code Kernel} with various configurations of custom
 * services, managers, and node types.
 */
@RunWith(FilteredNameRunner.class)
public class TestKernelCustomServices {

    private static Set<String> runningServices = new HashSet<String>();

    @Before
    public void setup() {
        runningServices.clear();
    }

    @Test
    public void testNoServices() {

    }


    public static abstract class TestAbstractService implements Service {
        public String getName() {
            return this.getClass().getName();
        }
        public void ready() throws Exception {
            runningServices.add(this.getClass().getName());
        }
        public void shutdown() {
            runningServices.remove(this.getClass().getName());
        }
    }

    public static class Service1 extends TestAbstractService {
        public Service1(Properties p,
                        ComponentRegistry c,
                        TransactionProxy t) {

        }
    }

    public static class Manager1 {
        public Manager1(Service1 s) {}
    }

    public static class Service2 extends TestAbstractService {
        public Service2(Properties p,
                        ComponentRegistry c,
                        TransactionProxy t) {

        }
    }

    public static class Manager2 {
        public Manager2(Service2 s) {}
    }

    public static class Service3 extends TestAbstractService {
        public Service3(Properties p,
                        ComponentRegistry c,
                        TransactionProxy t) {

        }
    }

    public static class Manager3 {
        public Manager3(Service3 s) {}
    }

    public static class InvalidService extends TestAbstractService {
        public InvalidService() {}
    }

    public static class InvalidManager {
        public InvalidManager() {}
    }

}
