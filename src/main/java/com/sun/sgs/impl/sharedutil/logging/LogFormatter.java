/*
 * Copyright (c) 2007-2010, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * --
 */

package com.sun.sgs.impl.sharedutil.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
 * Defines a logging {@code Formatter} that uses a compact date and time format
 * that includes milliseconds, permits the date and time format to be
 * customized, and allows suppressing stack traces for exceptions. <p>
 *
 * This class recognizes the following {@link LogManager} configuration
 * properties:
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <b>{@value #TIME_FORMAT_PROPERTY}</b> <br>
 *	<i>Default:</i> {@value #DEFAULT_TIME_FORMAT}
 *
 * <dd style="padding-top: .5em">
 *	Specifies the format string that will be used in a call to {@link
 *	Formatter#format Formatter.format} to display the date and time of a
 *	logging call.  The argument to the format call will be a {@code long}
 *	representing the current time in milliseconds.  The default prints the
 *	time in the format {@code 2008-02-14 11:52:59.679}. <p>
 *
 * <dt> <i>Property:</i> <b>{@value #PRINT_STACK_PROPERTY}</b> <br>
 *	<i>Default:</i> {@code true}
 *
 * <dd style="padding-top: .5em">
 *	Specifies whether to print stack traces when formatting a log record
 *	that contains an exception.
 *
 * </dl> <p>
 *
 * For example, to use this class to format logging output to the console so
 * that it prints the time in milliseconds since the epoch and does not include
 * stack traces, put the following text in the logging configuration file:
 *
 * <pre>
 *   .level=INFO
 *   handlers=java.util.logging.ConsoleHandler
 *   java.util.logging.ConsoleHandler.formatter=com.sun.sgs.impl.sharedutil.logging.LogFormatter
 *   com.sun.sgs.impl.sharedutil.logging.LogFormatter.time.format=%tQ
 *   com.sun.sgs.impl.sharedutil.logging.LogFormatter.print.stack=false
 * </pre>
 */
public class LogFormatter extends java.util.logging.Formatter {
    
    /** The default time format string. */
    public static final String DEFAULT_TIME_FORMAT = "%tF %1$tT.%1$tL";

    /** The logging property for specifying the time format. */
    public static final String TIME_FORMAT_PROPERTY =
	"com.sun.sgs.impl.sharedutil.logging.LogFormatter.time.format";

    /**
     * The logging property for specifying whether to print stack traces when
     * logging a thrown exception.
     */
    public static final String PRINT_STACK_PROPERTY =
	"com.sun.sgs.impl.sharedutil.logging.LogFormatter.print.stack";

    /** The time format. */
    private final String timeFormat;

    /** Whether to print stack traces. */
    private final boolean printStack;

    /** Creates an instance of this class. */
    public LogFormatter() {
	LogManager logManager = LogManager.getLogManager();
	String value = logManager.getProperty(TIME_FORMAT_PROPERTY);
	timeFormat = (value != null) ? value : DEFAULT_TIME_FORMAT;
	value = logManager.getProperty(PRINT_STACK_PROPERTY);
	printStack = (value == null) || Boolean.parseBoolean(value);
    }

    /* -- Implement java.util.logging.Formatter -- */

    /** {@inheritDoc} */
    public String format(LogRecord record) {
	Formatter formatter = new Formatter();
	formatter.format(timeFormat, record.getMillis());
	if (record.getSourceClassName() != null) {	
	    formatter.format(": %s", record.getSourceClassName());
	    if (record.getSourceMethodName() != null) {	
		formatter.format(".%s", record.getSourceMethodName());
	    }
	} else {
	    formatter.format(": %s", record.getLoggerName());
	}
	formatter.format("%n%s: %s%n",
			 record.getLevel().getLocalizedName(),
			 formatMessage(record));
	if (record.getThrown() != null) {
	    if (printStack) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		record.getThrown().printStackTrace(pw);
		pw.close();
		formatter.format("%s%n", sw.toString());
	    } else {
		formatter.format("%s%n", record.getThrown());
	    }
	}
	return formatter.toString();
    }
}
