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

package com.sun.sgs.test.impl.service.nodemap.affinity;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroup;
import com.sun.sgs.impl.service.nodemap.affinity.AffinityGroupFactory;
import com.sun.sgs.impl.service.nodemap.affinity.AffinitySet;
import java.util.Map;

/**
 * Implementation of AffinityGroupFactory for testing.
 */
public class GroupFactory implements AffinityGroupFactory<AffinityGroup> {

    @Override
    public AffinityGroup newInstance(long groupId,
                                     long generation,
                                     Map<Identity, Long> members)
    {
        assert !members.isEmpty();
        return new AffinitySet(groupId, generation, members.keySet());
    }
}
