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

package com.sun.sgs.test.impl.service.data.store.net;

import com.sun.sgs.test.impl.service.data.BasicDataServiceMultiTest;
import com.sun.sgs.test.util.SgsTestNode;
import java.util.Properties;

/**
 * Perform multi-node tests on the {@code DataService} using the network data
 * store.
 */
public class TestDataServiceClientMulti extends BasicDataServiceMultiTest {

    @Override
    protected Properties getServerProperties() throws Exception {
	return SgsTestNode.getDefaultProperties(appName, null, null);
    }
}
