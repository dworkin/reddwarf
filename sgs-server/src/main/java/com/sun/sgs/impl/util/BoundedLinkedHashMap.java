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

package com.sun.sgs.impl.util;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

/** 
 * An implementation of {@code LinkedHashMap} that is bounded in size.
 */
public class BoundedLinkedHashMap<K,V> extends LinkedHashMap<K,V> {
    
    private static final long serialVersionUID = 1;
    
    /**
     * the maximum size of this map
     */
    private final int maxSize;
    
    /** 
     * Creates an instance of {@code BoundedLinkedHashMap} with the provided
     * size bound.
     *
     * @param maxSize the maximum size of this map
     */
    public BoundedLinkedHashMap(int maxSize) {
	this.maxSize = maxSize;
    }

    /** 
     * Returns {@code true} if adding {@code eldest} would cause the size of this
     * map to exceed its size bound.
     *
     * @param eldest the eldest entry in the map
     *
     * @return {@code true} if adding {@code eldest} would cause the size of
     *         this map to exceed its size bound.
     */
    protected boolean removeEldestEntry(Entry<K,V> eldest) {
	return size() > maxSize;
    }
}