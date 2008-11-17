/*
 * Copyright 2008 Sun Microsystems, Inc.
 *
 * This file is part of the Darkstar Test Cluster
 *
 * Darkstar Test Cluster is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Darkstar Test Cluster is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.projectdarkstar.tools.dtc.servlets.reads;

import java.io.IOException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.RequestDispatcher;

/**
 * 
 */
public class ConfigTestSpecificationsRead extends ConfigRead {

    @Override
    public void read(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        request.setAttribute("trail", generateCrumbTrail(request.getContextPath()));
        RequestDispatcher mainJsp = request.getRequestDispatcher("/jsp/config-test-specs.jsp");
        mainJsp.forward(request, response);
    }
    
    @Override
    protected List<String> generateCrumbTrail(String contextPath) {
        List<String> trail = super.generateCrumbTrail(contextPath);
        trail.add("Test Specifications");
        trail.add(contextPath + "/config/test-specs");
        
        return trail;
    }

}
