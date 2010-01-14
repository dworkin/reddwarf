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

package com.sun.sgs.test.impl.service.nodemap.affinity;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.affinity.RelocatingAffinityGroup;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test the {@link RelocatingAffinityGroup} class.
 */
@RunWith(FilteredNameRunner.class)
public class TestRelocatingAffinityGroup {

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorNoIdentities() throws Exception {
        Map<Identity, Long> emptyMap = Collections.emptyMap();
        new RelocatingAffinityGroup(0L, emptyMap, 0L);
    }

    @Test
    public void testSingleIdentity() throws Exception {
        Map<Identity, Long> singletonMap = new HashMap<Identity, Long>(1);
        singletonMap.put(new DummyIdentity(), 42L);
        RelocatingAffinityGroup group =
                new RelocatingAffinityGroup(0L, singletonMap, 0L);
        assert group.getTargetNode() == 42L;
        assert group.getStragglers().isEmpty();
    }

    @Test
    public void testMultiIdentity() throws Exception {
        Map<Identity, Long> map = new HashMap<Identity, Long>(4);
        map.put(new DummyIdentity("one"), 42L);
        map.put(new DummyIdentity("two"), 42L);
        map.put(new DummyIdentity("three"), 42L);
        map.put(new DummyIdentity("four"), 42L);
        RelocatingAffinityGroup group =new RelocatingAffinityGroup(0L, map, 0L);
        assert group.getTargetNode() == 42L;
        assert group.getStragglers().isEmpty();
    }
    
    @Test
    public void testOneStraggler() throws Exception {
        Map<Identity, Long> map = new HashMap<Identity, Long>(4);
        map.put(new DummyIdentity("one"), 42L);
        map.put(new DummyIdentity("two"), 42L);
        map.put(new DummyIdentity("three"), 42L);
        map.put(new DummyIdentity("four"), 1L);
        RelocatingAffinityGroup group =new RelocatingAffinityGroup(0L, map, 0L);
        assert group.getTargetNode() == 42L;
        assert group.getStragglers().size() == 1;
    }

    @Test
    public void testMultiStraggler() throws Exception {
        Map<Identity, Long> map = new HashMap<Identity, Long>(4);
        map.put(new DummyIdentity("one"), 42L);
        map.put(new DummyIdentity("two"), 42L);
        map.put(new DummyIdentity("three"), 1L);
        map.put(new DummyIdentity("four"), 2L);
        RelocatingAffinityGroup group =new RelocatingAffinityGroup(0L, map, 0L);
        assert group.getTargetNode() == 42L;
        assert group.getStragglers().size() == 2;
    }

    @Test
    public void testSplitNodes() throws Exception {
        Map<Identity, Long> map = new HashMap<Identity, Long>(4);
        map.put(new DummyIdentity("one"), 42L);
        map.put(new DummyIdentity("two"), 42L);
        map.put(new DummyIdentity("three"), 1L);
        map.put(new DummyIdentity("four"), 1L);
        RelocatingAffinityGroup group =new RelocatingAffinityGroup(0L, map, 0L);
        assert (group.getTargetNode() == 42L) || (group.getTargetNode() == 1L);
        assert group.getStragglers().size() == 2;
    }

    @Test
    public void testOneUnknownNode() throws Exception {
        Map<Identity, Long> map = new HashMap<Identity, Long>(4);
        map.put(new DummyIdentity("one"), 42L);
        map.put(new DummyIdentity("two"), 42L);
        map.put(new DummyIdentity("three"), 42L);
        map.put(new DummyIdentity("four"), -1L);
        RelocatingAffinityGroup group =new RelocatingAffinityGroup(0L, map, 0L);
        assert group.getTargetNode() == 42L;
        assert group.getStragglers().size() == 1;
    }

    @Test
    public void testAllUnknownNodes() throws Exception {
        Map<Identity, Long> map = new HashMap<Identity, Long>(4);
        map.put(new DummyIdentity("one"), -1L);
        map.put(new DummyIdentity("two"), -1L);
        map.put(new DummyIdentity("three"), -1L);
        map.put(new DummyIdentity("four"), -1L);
        RelocatingAffinityGroup group =new RelocatingAffinityGroup(0L, map, 0L);
        assert group.getTargetNode() == -1;
        assert group.getStragglers().size() == 4;
    }

    @Test
    public void testOrder() throws Exception {
        NavigableSet<RelocatingAffinityGroup> set =
                                    new TreeSet<RelocatingAffinityGroup>();

        // Add four groups with 2, 3, 4, and 1 identity each.
        // Note that group ID == number of identities
        Map<Identity, Long> map = new HashMap<Identity, Long>(4);
        map.put(new DummyIdentity("one"), -1L);
        map.put(new DummyIdentity("two"), -1L);
        set.add(new RelocatingAffinityGroup(
                                    2L, new HashMap<Identity, Long>(map), 0L));
        map.put(new DummyIdentity("three"), -1L);
        set.add(new RelocatingAffinityGroup(
                                    3L, new HashMap<Identity, Long>(map), 0L));
        map.put(new DummyIdentity("four"), -1L);
        set.add(new RelocatingAffinityGroup(
                                    4L, new HashMap<Identity, Long>(map), 0L));
        map.clear();
        map.put(new DummyIdentity("one"), -1L);
        set.add(new RelocatingAffinityGroup(1L, map, 0L));

        // note that this assumes the group will order by number of identities
        // if the weighting changes, this test may need to be modified
        int size = 1;
        for (RelocatingAffinityGroup group : set) {
            assert group.getIdentities().size() == size;
            size++;
        }
    }
}
