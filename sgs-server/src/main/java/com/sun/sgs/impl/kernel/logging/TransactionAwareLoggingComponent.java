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

package com.sun.sgs.impl.kernel.logging;

import com.sun.sgs.impl.sharedutil.PropertiesWrapper;

import com.sun.sgs.impl.kernel.StandardProperties;

import com.sun.sgs.service.TransactionProxy;

import java.util.Properties;

import java.util.logging.Handler;
import java.util.logging.Logger;


/**
 * TODO
 * 
 * <p>
 * 
 * This implementation supports three properties in the constructor,
 * which are set in the system properties file
 *
 * <p><dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <b>
 *	{@code com.sun.sgs.logging.app.namespace}
 *	</b><br>
 *	<i>Default:</i> {@code ""}
 *
 * <dd style="padding-top: .5em">This property specifies the root of
 * the application namespace.  All loggers under this root will be
 * have transactional-semantics.  If this propert is left unset the
 * system will use the namespace specified in the {@code
 * com.sun.sgs.app.listener} property value.  The {@code
 * com.sun.sgs.logging.nontransactional.namespaces} property may
 * be used to mark some sub-namespaces under this root as being
 * non-transactional. <p></dd>
 * 
 * <dt> <i>Property:</i> <b>
 *	{@code com.sun.sgs.logging.nontransactional.namespaces}
 *	</b><br>
 *	<i>Default:</i> {@code ""}
 *
 * <dd style="padding-top: .5em"> This property specifies those
 * namespaces in the application whose loggers should left with
 * nontransactional semantics.  This is primarily designed for
 * diagnostic reporting as it allows developers to see the partial
 * output of the transaction before it is aborted.<p></dd>
 *
 * </dl>
 *
 * <p>
 *
 * When the component is constructed, it marks all of the {@code
 * Logger} instances specified by the root namespace in the {@code
 * com.sun.sgs.impl.service.logging.app.namespace} property as
 * transactional.  If this property is left unset, the component
 * infers a namespace from the namespace specified by the {@code
 * com.sun.sgs.app.listener} property.
 *
 * @see TransactionalHandler
 */
public final class TransactionAwareLoggingComponent {

    /**
     * Package name for this class
     */
    private static final String PROPERTIES_PREFIX = "com.sun.sgs.logging";

    /**
     * The property specified in the system properties for
     * denoting the root namespace of the application.
     */
    private static final String APP_NAMESPACE_PROPERTY =
	PROPERTIES_PREFIX + ".app.namespace";

    /**
     * The properity specified in the system properties file for
     * denoting which loggers namespaces should be non-transactional
     */
    private static final String NONTXN_NS_PROPERTY = 
	PROPERTIES_PREFIX + ".nontransactional.namespaces";

    /**
     * The {@code TransactionProxy} used by the {@link
     * TransactionalHandler} handlers.
     */
    private final TransactionProxy proxy;

    /**
     * Creates a {@code TransactionAwareLoggingComponent} that
     * provides transactional semantices to all the specified
     * application loggers.
     *
     * @param properties the properties for configuring this component
     * @param txnProxy the transaction proxy
     */
    public TransactionAwareLoggingComponent(Properties properties,
					    TransactionProxy txnProxy) {
	this.proxy = txnProxy;

	PropertiesWrapper props = new PropertiesWrapper(properties);

	String appListener = props.getProperty(StandardProperties.APP_LISTENER);
	String defaultAppNamespace = 
	    appListener.substring(0, appListener.lastIndexOf("."));

	// if the applicate does not specify a specific namespace, we
	// use the namespace provided by the application listener.
	String appNamespace = props.getProperty(APP_NAMESPACE_PROPERTY, 
						defaultAppNamespace);
	
	// change the application's non-transactional name spaces to
	// transactional
	makeTransactional(Logger.getLogger(appNamespace));

	// then revert any transactional the non-transactional name
	// spaces based on the application settings.
	String nontransactionalLoggersStr = 
	    props.getProperty(NONTXN_NS_PROPERTY, "");

	String[] nontransactionalLoggers = 
	    nontransactionalLoggersStr.split(":");

	for (String s : nontransactionalLoggers) {
	    Logger logger = Logger.getLogger(s);
	    makeNonTransactional(logger);
	}

    }

    /**
     * Replaces all non-transactional {@link Handler} instances with
     * {@code TransactionalHander} instances.
     */
    void makeTransactional(Logger logger) {
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
     * Replaces all {@code TransactionalHander}
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
}