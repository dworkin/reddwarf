/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.sgs.test.impl.service.nodemap.affinity;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.AffinitySet;
import com.sun.sgs.impl.service.nodemap.affinity.GroupSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * Implementation of GroupSet for testing.
 */
public class SimpleGroupSet extends HashSet<AffinityGroup> implements GroupSet {

    @Override
    public void add(long groupId, Map<Identity, Long> members, long generation) {
        add(new AffinitySet(groupId, generation, members.keySet()));
    }

    @Override
    public Set<AffinityGroup> getGroups() {
        return Collections.unmodifiableSet(this);
    }
}
