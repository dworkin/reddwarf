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

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import javax.servlet.RequestDispatcher;

/**
 * Services configuration http requests.
 */
public class ConfigServlet extends HttpServlet {
    
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws IOException, ServletException {
        doPost(request, response);
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws IOException, ServletException {
        
        //build breadcrumb trail
        List<String> trail = new ArrayList<String>();
        trail.add("Darkstar Test Cluster");
        trail.add(request.getContextPath());
        trail.add("Configuration");
        trail.add(request.getContextPath() + request.getServletPath());
        
        //check to see which config page to show
        String specificConfig = request.getPathInfo();
        if(specificConfig != null && !specificConfig.equals("/")) {
            String path = specificConfig.substring(1);
            if(path.equals("applications")) {
                trail.add("Applications");
                trail.add(request.getContextPath() + request.getServletPath() + specificConfig);
                request.setAttribute("trail", trail);
                handleApplications(request, response);
            } else if(path.equals("resources")) {
                trail.add("Resources");
                trail.add(request.getContextPath() + request.getServletPath() + specificConfig);
                request.setAttribute("trail", trail);
                handleResources(request, response);
            } else if(path.equals("test-specs")) {
                trail.add("Test Specifications");
                trail.add(request.getContextPath() + request.getServletPath() + specificConfig);
                request.setAttribute("trail", trail);
                handleTestSpecifications(request, response);
            } else if(path.equals("test-suites")) {
                trail.add("Test Suites");
                trail.add(request.getContextPath() + request.getServletPath() + specificConfig);
                request.setAttribute("trail", trail);
                handleTestSuites(request, response);
            } else {
                RequestDispatcher errorJsp = request.getRequestDispatcher("/jsp/error.jsp");
                errorJsp.forward(request, response);
            }
        } else {
            RequestDispatcher mainJsp = request.getRequestDispatcher("/jsp/config.jsp");
            mainJsp.forward(request, response);
        }
    }
    
    private void handleApplications(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        RequestDispatcher mainJsp = request.getRequestDispatcher("/jsp/config-applications.jsp");
        mainJsp.forward(request, response);
    }
    
    private void handleResources(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        RequestDispatcher mainJsp = request.getRequestDispatcher("/jsp/config-resources.jsp");
        mainJsp.forward(request, response);
    }
    
    private void handleTestSpecifications(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        RequestDispatcher mainJsp = request.getRequestDispatcher("/jsp/config-test-specs.jsp");
        mainJsp.forward(request, response);
    }
    
    private void handleTestSuites(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        RequestDispatcher mainJsp = request.getRequestDispatcher("/jsp/config-test-suites.jsp");
        mainJsp.forward(request, response);
    }
}
