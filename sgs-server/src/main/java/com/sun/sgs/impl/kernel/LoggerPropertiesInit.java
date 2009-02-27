/*
 * Copyright 2009 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.kernel;

import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.logging.LogManager;

/**
 *
 */
public class LoggerPropertiesInit {

    public LoggerPropertiesInit() throws Exception {
        InputStream stream = ClassLoader.getSystemResourceAsStream(
                BootProperties.DEFAULT_LOG_PROPERTIES);
        if(stream != null) {
            LogManager.getLogManager().readConfiguration(stream);
        }
        
        File loggingConfig = new File(System.getProperty("java.util.logging.config.file"));
        if(loggingConfig.exists() && loggingConfig.isFile() && loggingConfig.canRead()) {
            InputStream configStream = new FileInputStream(loggingConfig);
            LogManager.getLogManager().readConfiguration(configStream);
        }
    }

}
