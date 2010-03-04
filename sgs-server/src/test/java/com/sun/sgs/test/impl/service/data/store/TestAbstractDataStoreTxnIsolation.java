/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.test.impl.service.data.store;

import com.sun.sgs.service.store.DataStore;
import com.sun.sgs.test.util.InMemoryDataStore;
import com.sun.sgs.tools.test.FilteredNameRunner;
import org.junit.runner.RunWith;

/**
 * Tests the isolation that {@link AbstractDataStore} enforces between
 * transactions.
 */
@RunWith(FilteredNameRunner.class)
public class TestAbstractDataStoreTxnIsolation extends BasicTxnIsolationTest {

    /** Creates an {@link InMemoryDataStore}. */
    protected DataStore createDataStore() {
	return new InMemoryDataStore(props, env.systemRegistry, txnProxy);
    }
}
