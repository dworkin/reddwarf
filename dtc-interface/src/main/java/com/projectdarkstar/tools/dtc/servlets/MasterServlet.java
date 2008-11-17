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

package com.projectdarkstar.tools.dtc.servlets;

import com.projectdarkstar.tools.dtc.servlets.reads.Read;
import com.projectdarkstar.tools.dtc.servlets.reads.MainRead;
import com.projectdarkstar.tools.dtc.servlets.reads.ConfigRead;
import com.projectdarkstar.tools.dtc.servlets.reads.ConfigApplicationsRead;
import com.projectdarkstar.tools.dtc.servlets.reads.ConfigResourcesRead;
import com.projectdarkstar.tools.dtc.servlets.reads.ConfigTestSpecificationsRead;
import com.projectdarkstar.tools.dtc.servlets.reads.ConfigTestSuitesRead;
import com.projectdarkstar.tools.dtc.service.RequestService;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import javax.ejb.EJB;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.RequestDispatcher;

/**
 * Master servlet used to servlet all http requests to the server.
 * Requests are delegated to ServletActions based on the URL of the
 * request.
 */
public class MasterServlet extends HttpServlet {
    
    @EJB
    RequestService rs;
    
    private Map<String, Read> reads;
    
    @Override
    public void init() {
        reads = new HashMap<String, Read>();
        reads.put("/main", new MainRead());
        reads.put("/config", new ConfigRead());
        reads.put("/config/applications", new ConfigApplicationsRead());
        reads.put("/config/resources", new ConfigResourcesRead());
        reads.put("/config/test-specs", new ConfigTestSpecificationsRead());
        reads.put("/config/test-suites", new ConfigTestSuitesRead());
    }

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws IOException, ServletException {
        if(rs.getServerAppsPage(0, 0).size() == 0) {
            RequestDispatcher mainJsp = request.getRequestDispatcher("/jsp/main.jsp");
            mainJsp.forward(request, response);
            return;
        }
        
        String path = request.getServletPath();
        if(request.getPathInfo() != null) {
            path = path + request.getPathInfo();
        }
        if(path.endsWith("/")) {
            path = path.substring(0, path.length() - 2);
        }
        
        Read read = reads.get(path);
        if(read != null) {
            read.read(request, response);
        } else {
            RequestDispatcher mainJsp = request.getRequestDispatcher("/jsp/error.jsp");
            mainJsp.forward(request, response);
        }
    }
}
