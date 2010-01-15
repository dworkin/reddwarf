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

package com.sun.sgs.test.impl.service.nodemap;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroup;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.test.util.UtilReflection;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the {@link RelocatingAffinityGroup} class.
 */
@RunWith(FilteredNameRunner.class)
public class TestRelocatingAffinityGroup extends Assert {

    private static Constructor<?> RAPConstructor;
    private static Method getTargetNodeMethod;
    private static Method setTargetNodeMethod;
    private static Method getStragglersMethod;
    static {
        try {
            Class<?> RAPClass = UtilReflection.getClass(
                    "com.sun.sgs.impl.service.nodemap.RelocatingAffinityGroup");
            RAPConstructor = UtilReflection.getConstructor(RAPClass,
                                                           long.class,
                                                           Map.class,
                                                           long.class);
            getTargetNodeMethod = UtilReflection.getMethod(RAPClass, "getTargetNode");
            setTargetNodeMethod = UtilReflection.getMethod(RAPClass, "setTargetNode",
                                                           long.class);
            getStragglersMethod = UtilReflection.getMethod(RAPClass, "getStragglers");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static AffinityGroup
            newRelocatingAffinityGroup(long gid,
                                       Map<Identity, Long> groups,
                                       long gen)
        throws Exception
    {
        try {
            return (AffinityGroup)RAPConstructor.newInstance(gid, groups, gen);
        } catch (InvocationTargetException ie) {
            throw (Exception)ie.getCause();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNoIdentities() throws Exception {
        Map<Identity, Long> emptyMap = Collections.emptyMap();
        newRelocatingAffinityGroup(0L, emptyMap, 0L);
    }

    @Test
    public void testSingleIdentity() throws Exception {
        Map<Identity, Long> singletonMap = new HashMap<Identity, Long>(1);
        singletonMap.put(new DummyIdentity(), 42L);
        AffinityGroup group = newRelocatingAffinityGroup(123L, singletonMap, 567L);
        assertEquals(getTargetNodeMethod.invoke(group), 42L);
        assertEquals(group.getId(), 123L);
        assertEquals(group.getGeneration(), 567L);
        assert ((Set)getStragglersMethod.invoke(group)).isEmpty();
    }

    @Test
    public void testMultiIdentity() throws Exception {
        Map<Identity, Long> map = new HashMap<Identity, Long>(4);
        map.put(new DummyIdentity("one"), 42L);
        map.put(new DummyIdentity("two"), 42L);
        map.put(new DummyIdentity("three"), 42L);
        map.put(new DummyIdentity("four"), 42L);
        AffinityGroup group = newRelocatingAffinityGroup(0L, map, 0L);
        assertEquals(getTargetNodeMethod.invoke(group), 42L);
        assert ((Set)getStragglersMethod.invoke(group)).isEmpty();
    }
    
    @Test
    public void testOneStraggler() throws Exception {
        Map<Identity, Long> map = new HashMap<Identity, Long>(4);
        map.put(new DummyIdentity("one"), 42L);
        map.put(new DummyIdentity("two"), 42L);
        map.put(new DummyIdentity("three"), 42L);
        map.put(new DummyIdentity("four"), 1L);
        AffinityGroup group = newRelocatingAffinityGroup(0L, map, 0L);
        assertEquals(getTargetNodeMethod.invoke(group), 42L);
        assertEquals(((Set)getStragglersMethod.invoke(group)).size(), 1);
    }

    @Test
    public void testMultiStraggler() throws Exception {
        Map<Identity, Long> map = new HashMap<Identity, Long>(4);
        map.put(new DummyIdentity("one"), 42L);
        map.put(new DummyIdentity("two"), 42L);
        map.put(new DummyIdentity("three"), 1L);
        map.put(new DummyIdentity("four"), 2L);
        AffinityGroup group = newRelocatingAffinityGroup(0L, map, 0L);
        assertEquals(getTargetNodeMethod.invoke(group), 42L);
        assertEquals(((Set)getStragglersMethod.invoke(group)).size(), 2);
    }

    @Test
    public void testSplitNodes() throws Exception {
        Map<Identity, Long> map = new HashMap<Identity, Long>(4);
        map.put(new DummyIdentity("one"), 42L);
        map.put(new DummyIdentity("two"), 42L);
        map.put(new DummyIdentity("three"), 1L);
        map.put(new DummyIdentity("four"), 1L);
        AffinityGroup group = newRelocatingAffinityGroup(0L, map, 0L);
        assert ((Long)getTargetNodeMethod.invoke(group) == 42L) ||
               ((Long)getTargetNodeMethod.invoke(group) == 1L);
        assertEquals(((Set)getStragglersMethod.invoke(group)).size(), 2);
    }

    @Test
    public void testOneUnknownNode() throws Exception {
        Map<Identity, Long> map = new HashMap<Identity, Long>(4);
        map.put(new DummyIdentity("one"), 42L);
        map.put(new DummyIdentity("two"), 42L);
        map.put(new DummyIdentity("three"), 42L);
        map.put(new DummyIdentity("four"), -1L);
        AffinityGroup group = newRelocatingAffinityGroup(0L, map, 0L);
        assertEquals(getTargetNodeMethod.invoke(group), 42L);
        assertEquals(((Set)getStragglersMethod.invoke(group)).size(), 1);
    }

    @Test
    public void testAllUnknownNodes() throws Exception {
        Map<Identity, Long> map = new HashMap<Identity, Long>(4);
        map.put(new DummyIdentity("one"), -1L);
        map.put(new DummyIdentity("two"), -1L);
        map.put(new DummyIdentity("three"), -1L);
        map.put(new DummyIdentity("four"), -1L);
        AffinityGroup group = newRelocatingAffinityGroup(0L, map, 0L);
        assertEquals(getTargetNodeMethod.invoke(group), -1L);
        assertEquals(((Set)getStragglersMethod.invoke(group)).size(), 4);
    }

    @Test
    public void testChangeTargetNode() throws Exception {
        Map<Identity, Long> map = new HashMap<Identity, Long>(4);
        map.put(new DummyIdentity("one"), 42L);
        map.put(new DummyIdentity("two"), 42L);
        map.put(new DummyIdentity("three"), 33L);
        map.put(new DummyIdentity("four"), -1L);
        AffinityGroup group = newRelocatingAffinityGroup(0L, map, 0L);
        assertEquals(getTargetNodeMethod.invoke(group), 42L);
        assertEquals(((Set)getStragglersMethod.invoke(group)).size(), 2);
        setTargetNodeMethod.invoke(group, 33L);
        assertEquals(getTargetNodeMethod.invoke(group), 33L);
        assertEquals(((Set)getStragglersMethod.invoke(group)).size(), 3);
    }

    @Test
    public void testEquals() throws Exception {
        Map<Identity, Long> map = new HashMap<Identity, Long>(1);
        map.put(new DummyIdentity("one"), 42L);
        AffinityGroup group1 = newRelocatingAffinityGroup(123L, map, 567L);

        // two equal groups
        AffinityGroup group2 = newRelocatingAffinityGroup(123L, map, 567L);
        assert group1.equals(group2);

        // generation numbers differ
        AffinityGroup group3 = newRelocatingAffinityGroup(123L, map, 890L);
        assert !group1.equals(group3);

        // different group map
        Map<Identity, Long> map2 = new HashMap<Identity, Long>(1);
        map2.put(new DummyIdentity("one"), 42L);
        map2.put(new DummyIdentity("two"), 42L);
        AffinityGroup group4 = newRelocatingAffinityGroup(123L, map2, 567L);
        assert !group1.equals(group4);
    }

    @Test
    public void testOrder() throws Exception {
        NavigableSet<AffinityGroup> set = new TreeSet<AffinityGroup>();

        // Add four groups with 2, 3, 4, and 1 identity each.
        // Note that group ID == number of identities
        Map<Identity, Long> map = new HashMap<Identity, Long>(4);
        map.put(new DummyIdentity("one"), -1L);
        map.put(new DummyIdentity("two"), -1L);
        set.add(newRelocatingAffinityGroup(2L, new HashMap<Identity, Long>(map), 0L));
        map.put(new DummyIdentity("three"), -1L);
        set.add(newRelocatingAffinityGroup(3L, new HashMap<Identity, Long>(map), 0L));
        map.put(new DummyIdentity("four"), -1L);
        set.add(newRelocatingAffinityGroup(4L, new HashMap<Identity, Long>(map), 0L));
        map.clear();
        map.put(new DummyIdentity("one"), -1L);
        set.add(newRelocatingAffinityGroup(1L, map, 0L));

        // note that this assumes the group will order by number of identities
        // if the weighting changes, this test may need to be modified
        int size = 1;
        for (AffinityGroup group : set) {
            assert group.getIdentities().size() == size;
            size++;
        }
    }
}
