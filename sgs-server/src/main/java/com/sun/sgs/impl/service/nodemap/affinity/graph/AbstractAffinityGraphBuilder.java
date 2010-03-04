/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.service.nodemap.affinity.graph;

import com.sun.sgs.impl.service.nodemap.affinity.BasicState;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A base class to support common AffinityGraphBuilder implementations.
 * <p>
 * This class supports the following properties:
 * <p>
 * <dl style="margin-left: 1em">
 *
 * <dt>	<i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.affinity.snapshot.period
 *	</b></code><br>
 *	<i>Default:</i> {@code 300000} (5 minutes)<br>
 *
 * <dd style="padding-top: .5em">The amount of time, in milliseconds, for
 *      each snapshot of retained data.  Older snapshots are discarded as
 *      time goes on. A longer snapshot period gives us more history, but
 *      also longer compute times to use that history, as more data must
 *      be processed.<p>
 *
 * <dt>	<i>Property:</i> <code><b>
 *	com.sun.sgs.impl.service.nodemap.affinity.snapshot.count
 *	</b></code><br>
 *	<i>Default:</i> {@code 1}
 *
 * <dd style="padding-top: .5em">The number of snapshots to retain.  A
 *       larger value means more history will be retained.  Using a smaller
 *       snapshot period with a larger count means more total history will be
 *       retained, with a smaller amount discarded at the start of each
 *       new snapshot.<p>
 * </dl>
 *
 */
public class AbstractAffinityGraphBuilder extends BasicState {
    /** The base name for graph builder properties. */
    public static final String PROP_BASE =
            "com.sun.sgs.impl.service.nodemap.affinity";

    /** The property controlling time snapshots, in milliseconds. */
    public static final String PERIOD_PROPERTY = PROP_BASE + ".snapshot.period";

    /** The default time snapshot period. */
    public static final long DEFAULT_PERIOD = 1000 * 60 * 5;

    /** The property controlling how many past snapshots to retain. */
    public static final String PERIOD_COUNT_PROPERTY =
            PROP_BASE + ".snapshot.count";

    /** The default snapshot count. */
    public static final int DEFAULT_PERIOD_COUNT = 1;

    /** Our logger. */
    protected static final LoggerWrapper logger =
            new LoggerWrapper(Logger.getLogger(PROP_BASE));

    /** Our properties. */
    protected final PropertiesWrapper wrappedProps;

    /** The time, in milliseconds for each period snapshot. */
    protected final long snapshot;

    /** The number of snapshots the graph pruner should maintain. */
    protected final int periodCount;

    /**
     * Parses common graph builder properties.
     *
     * @param properties the configuration properties for this graph builder
     */
    protected AbstractAffinityGraphBuilder(Properties properties) {
        wrappedProps = new PropertiesWrapper(properties);
        snapshot =
                wrappedProps.getLongProperty(PERIOD_PROPERTY, DEFAULT_PERIOD);
        periodCount = wrappedProps.getIntProperty(
                PERIOD_COUNT_PROPERTY, DEFAULT_PERIOD_COUNT,
                1, Integer.MAX_VALUE);
         logger.log(Level.CONFIG,
                       "Created graph builder with properties:" +
                       "\n  " + PERIOD_PROPERTY + "=" + snapshot +
                       "\n  " + PERIOD_COUNT_PROPERTY + "=" + periodCount);
    }
}
