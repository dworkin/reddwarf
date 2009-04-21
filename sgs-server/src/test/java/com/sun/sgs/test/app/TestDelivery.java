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

package com.sun.sgs.test.app;

import com.sun.sgs.app.Delivery;
import com.sun.sgs.tools.test.FilteredNameRunner;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the {@link Delivery} enum.
 */
@RunWith(FilteredNameRunner.class)
public class TestDelivery extends Assert {

    @Test
    public void testSupportsDeliveryNullDelivery() {
	for (Delivery delivery : Delivery.values()) {
	    try {
		delivery.supportsDelivery(null);
		fail("expected NullPointerException: " + delivery);
	    } catch (NullPointerException e) {
		System.err.println(e);
	    }
	}
    }
    
    @Test
    public void testSupportsDeliveryAllCombinations() {
	for (Delivery delivery : Delivery.values()) {
	    assertTrue(Delivery.RELIABLE.supportsDelivery(delivery));
	    assertTrue(delivery.supportsDelivery(delivery));
	    assertTrue(delivery.supportsDelivery(Delivery.UNRELIABLE));
	    if (delivery != Delivery.UNRELIABLE) {
		assertFalse(Delivery.UNRELIABLE.supportsDelivery(delivery));
	    }
	}
    }
}
