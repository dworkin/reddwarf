/*
 * Copyright 2008 Sun Microsystems, Inc.
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

package com.sun.sgs.test.util;

import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

/**
 * A simple test filter to use with JUnit 4.5, which will run only the
 * test named by the system property "test.method".
 */
public class NamedTestFilter extends Filter {
    /** If this property is set, then only run the single named test method. */
    private static final String testMethod = System.getProperty("test.method");
    public String describe() {
        return "runs test specified by test.method system property";
    }
    public boolean shouldRun(Description description) {
        if (description.isTest()) {
            if (testMethod == null || 
                description.getDisplayName().startsWith(testMethod)) 
            {
                return true;
            } else {
                return false;
            }
        }
        return true;
    }
}