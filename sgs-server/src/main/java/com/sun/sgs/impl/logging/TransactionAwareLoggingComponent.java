/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.service.logging;

import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

import com.sun.sgs.kernel.ComponentRegistry;

import com.sun.sgs.service.LoggingService;
import com.sun.sgs.service.TransactionProxy;

import java.util.Properties;

import java.util.logging.Handler;
import java.util.logging.Logger;


/**
 * An implementation of {@code LoggingService} that manages the
 * transactional semantics for all the {@code Logger} namepsaces of an
 * application.
 * 
 * <p>
 * 
 * This implementation supports three properties in the constructor,
 * which are set in the system properties file
 *
 * <p><dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <b>
 *	{@code com.sun.sgs.impl.service.logging.app.namespace}
 *	</b><br>
 *	<i>Default:</i> {@code ""}
 *
 * <dd style="padding-top: .5em">The root of the application
 * namespace.  All loggers under this root will be have
 * transactional-semantics.  The {@code
 * com.sun.sgs.impl.service.logging.app.nontransactional.namespaces}
 * property may be used to mark some sub-namespaces under this root as
 * being non-transactional. <p></dd>
 *
 * <dt>	<i>Property:</i> <b>
 *	{@code com.sun.sgs.impl.service.logging.nontransactional.namespaces}
 *	</b><br>
 *	<i>Default:</i>  {@code ""}<br>
 *
 * <dd style="padding-top: .5em">A {@code :} delimited list of
 * namespaces or {@code Logger} names, of which the {@code Logger}
 * each should have nontransactional semantics. <p>
 *
 * <dt> <i>Property:</i> <b>
 *	{@code com.sun.sgs.impl.service.logging.transactional.namespaces}
 *	</b><br>
 *	<i>Default:</i> {@code ""}
 *
 * <dd style="padding-top: .5em">A {@code :} delimited list of
 * namespaces or {@code Logger} names, of which the {@code Logger}
 * each should have transactional semantics.  This allows developers
 * to mark sub-namespaces that had been marked as non-transaction as
 * still being trasactional.  Since all namespaces are transactional
 * by default, this option is ignored if {@code
 * com.sun.sgs.impl.service.logging.app.nontransactional.namespaces}
 * has not been set. <p></dd>
 * 
 * </dl>
 *
 * <p>
 *
 * When the service is constructed, if the {@code
 * com.sun.sgs.impl.service.logging.app.namespace} is provided, all
 * {@code Logger} instances in this namespace will be marked as
 * transactional.  If this property is not specified, no action is
 * taken by this service.
 *
 * <p>
 *
 * Next, if the {@code
 * com.sun.sgs.impl.service.logging.app.nontransactional.namespaces}
 * property is specified, the {@code Logger} instances associated with
 * these namespaces are reverted to being non-transactional.
 *
 * <p>
 *
 * Lastly, if both of the previously mentioned properties have
 * specified, the {@code
 * com.sun.sgs.impl.service.logging.app.transactional.namespaces}
 * property is used to mark the {@code Logger} instances of any
 * previously-marked, non-transactional namespaces as being
 * transactional.  This property should only be used to specified
 * sub-namespaces of those specified by the {@code
 * com.sun.sgs.impl.service.logging.app.nontransactional.namespaces}
 * property.
 *
 * @see TransactionalHandler
 */
public class LoggingServiceImpl implements LoggingService {

    /**
     * Package name for this class
     */
    private static final String PKG_NAME = "com.sun.sgs.impl.service.logging";

    /**
     * The property specified in the system properties for
     * denoting the root namespace of the application.
     */
    private static final String TXN_APP_NAMESPACE_PROPERTY =
	PKG_NAME + ".app.namespace";

    /**
     * The properity specified in the system properties file for
     * denoting which loggers namespaces should be transactional
     */
    private static final String TXN_NS_PROPERTY = 
	PKG_NAME + ".transactional.namespaces";

    /**
     * The properity specified in the system properties file for
     * denoting which loggers namespaces should be non-transactional
     */
    private static final String NONTXN_NS_PROPERTY = 
	PKG_NAME + ".nontransactional.namespaces";

    /**
     * The identifier used for this {@code Service}.
     */
    public static final String NAME = LoggingServiceImpl.class.getName();

    /**
     * The {@code TransactionProxy} used by the {@link
     * TransactionalHandler} handlers.
     */
    private final TransactionProxy proxy;

    /**
     * Constructs an instance of the {@code LoggingService} with the
     * specified properties.
     *
     * <p>
     *
     * The application context is resolved at construction time
     * (rather than when {@link #ready} is called), because this
     * service does not use Managers and will not run application
     * code.  Managers are not available until {@code ready} is
     * called.
     *
     * <p>
     *
     * @param properties the properties for configuring this service
     * @param systemRegistry the registry of available system
     *        components
     * @param txnProxy the transaction proxy
     */
    public LoggingServiceImpl(Properties properties,
			      ComponentRegistry systemRegistry,
			      TransactionProxy txnProxy) {
	this.proxy = txnProxy;

	PropertiesWrapper props = new PropertiesWrapper(properties);

	String appNamespace = props.getProperty(TXN_APP_NAMESPACE_PROPERTY, 
						null);
	
	// if the application does not specify any namespace, do not
	// perform any changes to the logger.
	if (appNamespace == null) 
	    return;

	// mark the non-transactional name spaces first
	String nontransactionalLoggersStr = 
	    props.getProperty(NONTXN_NS_PROPERTY, "");

	String[] nontransactionalLoggers = 
	    nontransactionalLoggersStr.split(":");

	for (String s : nontransactionalLoggers) {
	    Logger logger = Logger.getLogger(s);
	    makeNonTransactional(logger);
	}

	// then revert any non-transactional name spaces to
	// transactional
	String transactionalLoggersStr = props.getProperty(TXN_NS_PROPERTY, "");

	String[] transactionalLoggers = transactionalLoggersStr.split(":");

	for (String s : transactionalLoggers) {
	    Logger logger = Logger.getLogger(s);
	    makeTransactional(logger);
	}

    }

    /**
     * {@inheritDoc}
     */
    public String getName() {
	return NAME;
    }

    /**
     * {@inheritDoc}
     *
     * This implementation replaces all non-transactional {@link
     * Handler} instances with {@code TransactionalHander} instances.
     */
    public void makeTransactional(Logger logger) {
	for (Handler h : logger.getHandlers()) {
	    // check that we aren't already dealing with a logger that
	    // has already been made transactional
	    if (h instanceof TransactionalHandler)
		continue;
	    logger.addHandler(new TransactionalHandler(proxy, h));
	    logger.removeHandler(h);
	    // ensure that any log calls to this logger don't work
	    // their way up the logger hierarchy, which could result
	    // in non-transactional logging
	    logger.setUseParentHandlers(false);
	}
    }

    /**
     * {@inheritDoc}
     *
     * This implementation replaces all {@code TransactionalHander}
     * instances with the non-transactional {@link Handler}instances
     * that back them.
     */
    public void makeNonTransactional(Logger logger) {
	for (Handler h : logger.getHandlers()) {
	    // check that this logger was already made transactional
	    if (h instanceof TransactionalHandler) {
		TransactionalHandler handler = (TransactionalHandler)h;
		logger.addHandler(handler.getBackingHandler());
		logger.removeHandler(h);
		// ensure that any log calls to this logger will now
		// pass use the parent handlers as well
		logger.setUseParentHandlers(true);
	    }
	}	
    }

    /**
     * {@inheritDoc}
     *
     * This implementation will never throw an {@code Exception}.
     */
    public void ready() throws Exception {
	// no op
    }

    /**
     * {@inheritDoc}
     */
    public boolean shutdown() {
	return true;
    }
}