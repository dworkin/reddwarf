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

package com.sun.sgs.test.app;

import com.sun.sgs.app.Delivery;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test the {@link Delivery} enum.
 */
public class TestDelivery extends Assert {

    @Test
    public void testSupportsDelivery() {
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
