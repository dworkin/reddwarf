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
package com.sun.sgs.impl.profile;

import com.sun.sgs.management.ProfileControllerMXBean;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.profile.ProfileConsumer;
import java.util.Set;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.StandardMBean;

/**
 * The MXBean for controlling profile information.
 */
class ProfileController extends StandardMBean 
                        implements ProfileControllerMXBean 
{
    /* The backing profile collector. */
    private final ProfileCollector collector;

    /** 
     * Creates a new Profile Controller MXBean object.
     * @param collector the backing profile collector
     */
    public ProfileController(ProfileCollector collector) {
        super(ProfileControllerMXBean.class, true);
        this.collector = collector;
    }

    // ProfileControllerMXBean
    
    /** {@inheritDoc} */
    public String[] getProfileConsumers() {
        Set<String> keys = collector.getConsumers().keySet();
        return keys.toArray(new String[keys.size()]);
    }

    /** {@inheritDoc} */
    public ProfileLevel getConsumerLevel(String consumer) {
        ProfileConsumer cons = collector.getConsumers().get(consumer);
        if (cons == null) {
            throw new IllegalArgumentException(
                    "no consumer found named " + consumer);
        }
        
        return cons.getProfileLevel();
    }

    /** {@inheritDoc} */
    public void setConsumerLevel(String consumer, ProfileLevel level) {
        ProfileConsumer cons = collector.getConsumers().get(consumer);
        if (cons == null) {
            throw new IllegalArgumentException(
                    "no consumer found named " + consumer);
        }
        cons.setProfileLevel(level);
    }

    /** {@inheritDoc} */
    public ProfileLevel getDefaultProfileLevel() {
        return collector.getDefaultProfileLevel();
    }

    /** {@inheritDoc} */
    public void setDefaultProfileLevel(ProfileLevel level) {
        collector.setDefaultProfileLevel(level);
    }

    // Overrides for StandardMBean information, giving JMX clients
    // (like JConsole) more information for better displays.
    
    /** {@inheritDoc} */
    protected String getDescription(MBeanInfo info) {
        return "An MXBean for controlling profile consumers";
    }

    /** {@inheritDoc} */
    protected String getDescription(MBeanAttributeInfo info) {
        String description = null;
        if (info.getName().equals("DefaultProfileLevel")) {
            description = "The default profiling level for new consumers, " 
                        + "used only at consumer create time.";
        } else if (info.getName().equals("ProfileConsumers")) {
            description = "The profile consumers, listed by name.";
        }
        return description;
    }

    /** {@inheritDoc} */
    protected String getDescription(MBeanOperationInfo op) {
        if (op.getName().equals("getConsumerLevel")) {
            return "Get the current profile level of the named consumer.";
        } else if (op.getName().equals("setConsumerLevel")) {
            return "Set the current profile level of the named consumer.";
        }
        return null;
    }

    /** {@inheritDoc} */
    protected String getDescription(MBeanOperationInfo op, 
                                    MBeanParameterInfo param, 
                                    int sequence) 
    {
        if (op.getName().equals("getConsumerLevel")) {
            switch (sequence) {
                case 0:
                    return "The profile consumer name";
                default:
                    return null;
            }
        } else if (op.getName().equals("setConsumerLevel")) {
            switch (sequence) {
                case 0:
                    return "The profile consumer name";
                case 1:
                    return "The profiling level, MIN, MEDIUM, or MAX";
                default:
                    return null;
            }
        }
        return null;
    }

    /** {@inheritDoc} */
    protected String getParameterName(MBeanOperationInfo op, 
                                      MBeanParameterInfo param, 
                                      int sequence) 
    {
        if (op.getName().equals("getConsumerLevel")) {
            switch (sequence) {
                case 0:
                    return "consumer";
                default:
                    return null;
            }
        } else if (op.getName().equals("setConsumerLevel")) {
            switch (sequence) {
                case 0:
                    return "consumer";
                case 1:
                    return "level";
                default:
                    return null;
            }
        }
        return null;
    }

    /** {@inheritDoc} */
    protected int getImpact(MBeanOperationInfo op) {
        if (op.getName().equals("getConsumerLevel")) {
            return MBeanOperationInfo.INFO;
        } else if (op.getName().equals("setConsumerLevel")) {
            return MBeanOperationInfo.ACTION;
        }
        return MBeanOperationInfo.UNKNOWN;
    }
}
