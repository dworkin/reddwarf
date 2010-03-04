/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.service.task;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.task.ContinuePolicy;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An implementation of {@code ContinuePolicy} that always provides
 * positive continuation feedback until a fixed amount of time has elapsed
 * in the current transaction.  After this time period has elapsed, feedback
 * is always negative for the given transaction.  This time period is
 * configurable via the following configuration property:
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #CONTINUE_THRESHOLD_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> {@value #CONTINUE_THRESHOLD_DEFAULT}
 *
 * <dd style="padding-top: .5em">Specifies the time in milliseconds from the
 *      start of a transaction during which the
 *      {@link #shouldContinue() shouldContinue} method will return
 *      {@code true}.  After this period of time has elapsed in a transaction,
 *      this method will always return {@code false}.
 *
 * </dl> <p>
 */
public class FixedTimeContinuePolicy implements ContinuePolicy {

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(FixedTimeContinuePolicy.
                                           class.getName()));

    // the name of the current package
    private static final String PKG_NAME = "com.sun.sgs.impl.service.task";

    /**
     * The property key to set how long a task should run before
     * {@link #shouldContinue() shouldContinue} returns false.
     */
    public static final String CONTINUE_THRESHOLD_PROPERTY =
                               PKG_NAME + ".continue.threshold";

    /** The default continue threshold. */
    public static final long CONTINUE_THRESHOLD_DEFAULT = 10L;

    // the actual value of the continue threshold
    private final long continueThreshold;

    // the transaction proxy
    private final TransactionProxy txnProxy;

    /**
     * Construct a {@code FixedTimeContinuePolicy} instance.
     *
     * @param properties the system properties
     * @param systemRegistry the system registry
     * @param txnProxy the system's transaction proxy
     */
    public FixedTimeContinuePolicy(Properties properties,
                                   ComponentRegistry systemRegistry,
                                   TransactionProxy txnProxy) {
        logger.log(Level.CONFIG, "Creating FixedTimeContinuePolicy");
        this.txnProxy = txnProxy;
        
        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
        this.continueThreshold = wrappedProps.getLongProperty(
                CONTINUE_THRESHOLD_PROPERTY, CONTINUE_THRESHOLD_DEFAULT);
        if (continueThreshold <= 0) {
            throw new IllegalStateException("Continue threshold property " +
                                            "must be positive");
        }

        logger.log(Level.CONFIG,
                   "Created FixedTimeContinuePolicy with properties:" +
                   "\n  " + CONTINUE_THRESHOLD_PROPERTY + "=" +
                   continueThreshold);
    }

    /**
     * {@inheritDoc}
     */
    public boolean shouldContinue() {
        Transaction txn = txnProxy.getCurrentTransaction();
        return System.currentTimeMillis() - txn.getCreationTime() <
               continueThreshold;
    }

}
