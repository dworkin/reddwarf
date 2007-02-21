package com.sun.sgs.impl.util;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Wrapper around a {@link Logger} that provides convenience methods for
 * logging exceptions and messages with multiple arguments, and that insulates
 * the caller from unchecked exceptions thrown during the act of logging.
 */
public class LoggerWrapper {

    private static final String CLASS_NAME = LoggerWrapper.class.getName();

    private final Logger logger;

    /**
     * Creates an instance that delegates to the given <code>Logger</code>.
     *
     * @param	logger the <code>Logger</code> to wrap
     */
    public LoggerWrapper(Logger logger) {
	if (logger == null) {
	    throw new NullPointerException("null logger");
	}
	this.logger = logger;
    }

    /**
     * Returns the wrapped <code>Logger</code>.
     *
     * @return	the wrapped <code>Logger</code>
     */
    public Logger getLogger() {
	return logger;
    }

    /**
     * Returns the result of invoking {@link Logger#isLoggable isLoggable} on
     * the wrapped <code>Logger</code>.
     *
     * @param	level the level at which to check if logging is enabled
     * @return	the result of invoking <code>isLoggable</code> on the wrapped
     * 		<code>Logger</code>
     */
    public boolean isLoggable(Level level) {
	return logger.isLoggable(level);
    }

    /**
     * If calling {@link Logger#isLoggable isLoggable} with the given
     * <code>level</code> value on the wrapped <code>Logger</code> returns
     * <code>true</code>, invokes the {@link Logger#log(LogRecord) log} method
     * of the wrapped <code>Logger</code>, passing a {@link LogRecord}
     * constructed with the given <code>level</code> and <code>message</code>
     * values.  The {@linkplain LogRecord#setSourceClassName source class name}
     * and {@linkplain LogRecord#setSourceMethodName source method name} of the
     * <code>LogRecord</code> are set to the class and method names of the
     * caller of this method, if they can be determined.  Any exceptions or
     * errors thrown by the underlying <code>log</code> call are swallowed.
     *
     * @param	level the level at which to log
     * @param	message the log message, which can be <code>null</code>
     */
    public void log(Level level, String message) {
	if (logger.isLoggable(level)) {
	    log(new LogRecord(level, message));
	}
    }

    /**
     * If calling {@link Logger#isLoggable isLoggable} with the given
     * <code>level</code> value on the wrapped <code>Logger</code> returns
     * <code>true</code>, invokes the {@link Logger#log(LogRecord) log} method
     * of the wrapped <code>Logger</code>, passing a {@link LogRecord}
     * constructed with the given <code>level</code> and <code>message</code>
     * values.  The given <code>param</code> value is set as the sole
     * {@linkplain LogRecord#setParameters parameter} of the
     * <code>LogRecord</code>.  The {@linkplain LogRecord#getSourceClassName
     * source class name} and {@linkplain LogRecord#getSourceMethodName source
     * method name} of the <code>LogRecord</code> are set to the class and
     * method names of the caller of this method, if they can be determined.
     * Any exceptions or errors thrown by the underlying <code>log</code> call
     * are swallowed.
     *
     * @param	level the level at which to log
     * @param	message the log message, which can be <code>null</code>
     * @param	param the parameter value for the log message, which can be
     * 		<code>null</code>
     */
    public void log(Level level, String message, Object param) {
	if (logger.isLoggable(level)) {
	    LogRecord lr = new LogRecord(level, message);
	    lr.setParameters(new Object[]{ param });
	    log(lr);
	}
    }

    /**
     * If calling {@link Logger#isLoggable isLoggable} with the given
     * <code>level</code> value on the wrapped <code>Logger</code> returns
     * <code>true</code>, invokes the {@link Logger#log(LogRecord) log} method
     * of the wrapped <code>Logger</code>, passing a {@link LogRecord}
     * constructed with the given <code>level</code> and <code>message</code>
     * values.  The given <code>params</code> values are set as the {@linkplain
     * LogRecord#setParameters parameters} of the <code>LogRecord</code>.  The
     * {@linkplain LogRecord#getSourceClassName source class name} and
     * {@linkplain LogRecord#getSourceMethodName source method name} of the
     * <code>LogRecord</code> are set to the class and method names of the
     * caller of this method, if they can be determined.  Any exceptions or
     * errors thrown by the underlying <code>log</code> call are swallowed.
     *
     * @param	level the level at which to log
     * @param	message the log message, which can be <code>null</code>
     * @param	params the parameter values for the log message, which can be
     * 		<code>null</code>
     */
    public void log(Level level, String message, Object... params) {
	if (logger.isLoggable(level)) {
	    LogRecord lr = new LogRecord(level, message);
	    lr.setParameters(params);
	    log(lr);
	}
    }

    /**
     * If calling {@link Logger#isLoggable isLoggable} with the given
     * <code>level</code> value on the wrapped <code>Logger</code> returns
     * <code>true</code>, invokes the {@link Logger#log(LogRecord) log} method
     * of the wrapped <code>Logger</code>, passing a {@link LogRecord}
     * constructed with the given <code>level</code> and <code>message</code>
     * values.  The <code>thrown</code> value is set as the
     * <code>Throwable</code> value {@linkplain LogRecord#setThrown associated}
     * with the <code>LogRecord</code>.  The {@linkplain
     * LogRecord#setSourceClassName source class name} and {@linkplain
     * LogRecord#setSourceMethodName source method name} of the
     * <code>LogRecord</code> are set to the class and method names of the
     * caller of this method, if they can be determined.  Any exceptions or
     * errors thrown by the underlying <code>log</code> call are swallowed.
     *
     * @param	level the level at which to log
     * @param	thrown the <code>Throwable</code> associated with the log
     * 		message
     * @param	message the log message, which can be <code>null</code>
     */
    public void logThrow(Level level, Throwable thrown, String message) {
	if (logger.isLoggable(level)) {
	    LogRecord lr = new LogRecord(level, message);
	    lr.setThrown(thrown);
	    log(lr);
	}
    }

    /**
     * If calling {@link Logger#isLoggable isLoggable} with the given
     * <code>level</code> value on the wrapped <code>Logger</code> returns
     * <code>true</code>, invokes the {@link Logger#log(LogRecord) log} method
     * of the wrapped <code>Logger</code>, passing a {@link LogRecord}
     * constructed with the given <code>level</code> and <code>message</code>
     * values.  The <code>thrown</code> value is set as the
     * <code>Throwable</code> value {@linkplain LogRecord#setThrown associated}
     * with the <code>LogRecord</code>.  The given <code>param</code> value is
     * set as the sole {@linkplain LogRecord#setParameters parameter} of the
     * <code>LogRecord</code>.  The {@linkplain LogRecord#setSourceClassName
     * source class name} and {@linkplain LogRecord#setSourceMethodName source
     * method name} of the <code>LogRecord</code> are set to the class and
     * method names of the caller of this method, if they can be determined.
     * Any exceptions or errors thrown by the underlying <code>log</code> call
     * are swallowed.
     *
     * @param	level the level at which to log
     * @param	thrown the <code>Throwable</code> associated with the log
     * 		message
     * @param	message the log message, which can be <code>null</code>
     * @param	param the parameter value for the log message, which can be
     * 		<code>null</code>
     */
    public void logThrow(Level level,
			 Throwable thrown,
			 String message,
			 Object param)
    {
	if (logger.isLoggable(level)) {
	    LogRecord lr = new LogRecord(level, message);
	    lr.setThrown(thrown);
	    lr.setParameters(new Object[]{ param });
	    log(lr);
	}
    }

    /**
     * If calling {@link Logger#isLoggable isLoggable} with the given
     * <code>level</code> value on the wrapped <code>Logger</code> returns
     * <code>true</code>, invokes the {@link Logger#log(LogRecord) log} method
     * of the wrapped <code>Logger</code>, passing a {@link LogRecord}
     * constructed with the given <code>level</code> and <code>message</code>
     * values.  The <code>thrown</code> value is set as the
     * <code>Throwable</code> value {@linkplain LogRecord#setThrown associated}
     * with the <code>LogRecord</code>.  The given <code>params</code> values
     * are set as the {@linkplain LogRecord#setParameters parameters} of the
     * <code>LogRecord</code>.  The {@linkplain LogRecord#setSourceClassName
     * source class name} and {@linkplain LogRecord#setSourceMethodName source
     * method name} of the <code>LogRecord</code> are set to the class and
     * method names of the caller of this method, if they can be determined.
     * Any exceptions or errors thrown by the underlying <code>log</code> call
     * are swallowed.
     *
     * @param	level the level at which to log
     * @param	thrown the <code>Throwable</code> associated with the log
     * 		message
     * @param	message the log message, which can be <code>null</code>
     * @param	params the parameter values for the log message, which can be
     * 		<code>null</code>
     */
    public void logThrow(Level level,
			 Throwable thrown,
			 String message,
			 Object... params) 
    {
	if (logger.isLoggable(level)) {
	    LogRecord lr = new LogRecord(level, message);
	    lr.setThrown(thrown);
	    lr.setParameters(params);
	    log(lr);
	}
    }

    /**
     * If calling {@link Logger#isLoggable isLoggable} with the given
     * <code>level</code> value on the wrapped <code>Logger</code> returns
     * <code>true</code>, invokes the {@link Logger#log(LogRecord) log} method
     * of the wrapped <code>Logger</code>, passing a {@link LogRecord}
     * constructed with the given <code>level</code> and <code>message</code>
     * values.  The given <code>param</code> value is set as the sole
     * {@linkplain LogRecord#setParameters parameter} of the
     * <code>LogRecord</code>.  The {@linkplain LogRecord#getSourceClassName
     * source class name} and {@linkplain LogRecord#getSourceMethodName source
     * method name} of the <code>LogRecord</code> are set to the class and
     * method names of the caller of this method, if they can be determined.
     * Any exceptions or errors thrown by the underlying <code>log</code> call
     * are swallowed. <p>
     *
     * This method is deprecated to flag any possible confusion of this method
     * with the {@link #logThrow(Level, Throwable, String) logThrow} method.
     * If the caller intends to include a <code>Throwable</code> as a parameter
     * value in the log method, rather than including its stack trace by
     * calling <code>logThrow</code>, it can cast the parameter to type
     * <code>Object</code> to avoid the deprecation warning.
     *
     * @param	level the level at which to log
     * @param	message the log message, which can be <code>null</code>
     * @param	param the parameter value for the log message, which can be
     * 		<code>null</code>
     */
    @Deprecated
    public void log(Level level, String message, Throwable param) {
	log(level, message, (Object) param);
    }

    /**
     * If calling {@link Logger#isLoggable isLoggable} with the given
     * <code>level</code> value on the wrapped <code>Logger</code> returns
     * <code>true</code>, invokes the {@link Logger#log(LogRecord) log} method
     * of the wrapped <code>Logger</code>, passing a {@link LogRecord}
     * constructed with the given <code>level</code> and <code>message</code>
     * values.  The given <code>param</code> and <code>otherParams</code>
     * values are set as the {@linkplain LogRecord#setParameters parameters} of
     * the <code>LogRecord</code>.  The {@linkplain
     * LogRecord#getSourceClassName source class name} and {@linkplain
     * LogRecord#getSourceMethodName source method name} of the
     * <code>LogRecord</code> are set to the class and method names of the
     * caller of this method, if they can be determined.  Any exceptions or
     * errors thrown by the underlying <code>log</code> call are swallowed. <p>
     *
     * This method is deprecated to flag any possible confusion of this method
     * with the {@link #logThrow(Level, Throwable, String, Object) logThrow}
     * method.  If the caller intends to include a <code>Throwable</code> as a
     * parameter value in the log method, rather than including its stack trace
     * by calling <code>logThrow</code>, it can cast the first parameter to
     * type <code>Object</code> to avoid the deprecation warning.
     *
     * @param	level the level at which to log
     * @param	message the log message, which can be <code>null</code>
     * @param	param the parameter value for the log message, which can be
     * 		<code>null</code>
     * @param	otherParams additional parameter values for the log message,
     *		which can be <code>null</code>
     */
    @Deprecated
    public void log(Level level,
		    String message,
		    Throwable param,
		    Object... otherParams)
    {
	log(level, message, (Object) param, otherParams);
    }

    /**
     * Logs the given LogRecord to the wrapped Logger, after setting its
     * source class and method names to those of the caller of this class (if
     * the caller can be inferred).  Swallows any exceptions or errors that
     * result.
     */
    private void log(LogRecord lr) {
	StackTraceElement callerFrame = null;
	for (StackTraceElement frame : new Throwable().getStackTrace()) {
	    if (!CLASS_NAME.equals(frame.getClassName())) {
		callerFrame = frame;
		break;
	    }
	}
	if (callerFrame != null) {
	    lr.setSourceClassName(callerFrame.getClassName());
	    lr.setSourceMethodName(callerFrame.getMethodName());
	}
	try {
	    logger.log(lr);
	} catch (Throwable t) {
	    // swallow
	}
    }
}
