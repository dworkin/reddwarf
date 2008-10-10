/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.app.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * The <code>Property</code> annotation can be used on a parameter of
 * a method or constructor annotated with {@link Inject}.  A parameter
 * annotated with this annotation will have its value injected by retrieving
 * the value of the named property passed in to the application's
 * {@link com.sun.sgs.app.AppListener AppListener}'s 
 * {@link com.sun.sgs.app.AppListener#initialize initialize} method.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Property {
    /**
     * The name of the property to be used for injecting a value into the
     * annotated parameter.
     */
    String value();
}
