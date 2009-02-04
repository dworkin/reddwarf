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

package com.sun.sgs.tests.deadlock;

import java.util.Random;
import java.io.Serializable;
import com.sun.sgs.app.ManagedObject;

/**
 * A toy {@code ManagedObject} that keeps track of a single integer value.
 */
public class ManagedInteger implements Serializable, ManagedObject {
    
    /** 
     * The version of the serialized form.
     */
    private static final long serialVersionUID = 1;
    
    private static int MAX = 100;

    private Random random;
    private int value;
    private int sum;
    
    private int updates;

    public ManagedInteger() {
        random = new Random();
        value = random.nextInt(MAX);
        sum = 0;
        updates = 0;
    }
    
    /**
     * Returns the value of this object.
     * @return
     */
    public int getValue() {
        return value;
    }
    
    /**
     * Sets the internal value of this object to a random integer between
     * {@code 0} and {@value MAX}.
     */
    public void setValue() {
        value = random.nextInt(MAX);
        updates++;
    }
    
    /**
     * Set the sum 
     * 
     * @param sum
     */
    public void setSum(int sum) {
        this.sum = sum;
    }
    
    /**
     * Returns the number of times this object's value has been updated
     * since it was created.
     * 
     * @return the number of updates since creation
     */
    public int getUpdates() {
        return updates;
    }
    
}
