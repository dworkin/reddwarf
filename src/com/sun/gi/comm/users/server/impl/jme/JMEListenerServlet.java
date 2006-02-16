/*
 * JMEListenerServlet.java
 *
 * Created on January 5, 2006, 9:48 AM
 */

package com.sun.gi.comm.users.server.impl.jme;

import com.sun.gi.comm.users.server.impl.JMEHttpUserManager;
import com.sun.gi.utils.http.ServletListener;
import java.io.*;
import java.util.Map;
import java.util.Queue;

import javax.servlet.*;
import javax.servlet.http.*;

/**
 * This servlet receives all requests and processes them
 * @author as93050
 * @version
 */
public class JMEListenerServlet extends HttpServlet {
    
    private static Map<String,ServletListener> servletListeners;
    private final static int PACKET_SIZE = 32000;    

    private final static String GAME_NAME = "GAME_NAME";
    
    /** Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     * @param request servlet request
     * @param response servlet response
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        response.setContentType("application/octet-stream");
        String requestType = request.getMethod();
        BufferedOutputStream out = new BufferedOutputStream(response.getOutputStream());
        HttpSession  session = request.getSession(true);
        String gameName = getGameName(request);
        byte[] data = null;
        if (requestType == "POST") {
            data = readPostInput(request);
        } 
        ServletListener listener = servletListeners.get(gameName);
        Queue<byte[]> outgoingMessageQueue = listener.dataArrived(data, session);
        int queueSize = outgoingMessageQueue.size();
        if (queueSize > 0) {
            out.write((byte)queueSize);
        }
        for (int i = 0;i < queueSize;i++) {
            byte[] packet = outgoingMessageQueue.remove();
            out.write(packet);
        }        
        out.flush();
        out.close();
    }

    private byte[] readPostInput(final HttpServletRequest request) throws IOException {
        byte[] data;
        BufferedInputStream is = new BufferedInputStream(request.getInputStream());
        int dat  = 0;
        data = new byte[PACKET_SIZE];           
        for  (int i = 0;((dat = is.read()) != -1);i++) {
            data[i] = (byte)dat;
        }
        is.close();
        return data;
    }
    
    // <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
    /** Handles the HTTP <code>GET</code> method.
     * @param request servlet request
     * @param response servlet response
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        processRequest(request, response);
    }
    
    /** Handles the HTTP <code>POST</code> method.
     * @param request servlet request
     * @param response servlet response
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException {
        processRequest(request, response);
    }
    
    /** Returns a short description of the servlet.
     */
    public String getServletInfo() {
        return "Short description";
    }
    
    /**
     * Called when the serclet is initialized (the first time it runs)
     * This sets up all the listeners (the user managers)  per game
     */
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        servletListeners = JMEHttpUserManager.getServletListeners();
    }
    // </editor-fold>
    
    

    private String getGameName(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        for (Cookie cookie : cookies) {
            if (cookie.getName().equals(GAME_NAME)) {
                return cookie.getValue();
            }
        }
        return null;
        
    }
}
