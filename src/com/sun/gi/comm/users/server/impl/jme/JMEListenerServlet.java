/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
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
 * 
 * @author as93050
 * @version
 */
public class JMEListenerServlet extends HttpServlet {

    private static Map<String, ServletListener> servletListeners;
    private final static int PACKET_SIZE = 32000;

    private final static String GAME_NAME = "GAME_NAME";

    /**
     * Processes requests for both HTTP <code>GET</code> and
     * <code>POST</code> methods.
     * 
     * @param request servlet request
     * @param response servlet response
     */
    protected void processRequest(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/octet-stream");
        String requestType = request.getMethod();
        BufferedOutputStream out = new BufferedOutputStream(
                response.getOutputStream());
        HttpSession session = request.getSession(true);
        String gameName = getGameName(request);
        byte[] data = null;
        if (requestType == "POST") {
            data = readPostInput(request);
        }
        ServletListener listener = servletListeners.get(gameName);
        Queue<byte[]> outgoingMessageQueue = listener.dataArrived(data, session);
        int queueSize = outgoingMessageQueue.size();
        if (queueSize > 0) {
            out.write((byte) queueSize);
        }
        for (int i = 0; i < queueSize; i++) {
            byte[] packet = outgoingMessageQueue.remove();
            out.write(packet);
        }
        out.flush();
        out.close();
    }

    private byte[] readPostInput(final HttpServletRequest request)
            throws IOException {
        byte[] data;
        BufferedInputStream is = new BufferedInputStream(
                request.getInputStream());
        int dat = 0;
        data = new byte[PACKET_SIZE];
        for (int i = 0; ((dat = is.read()) != -1); i++) {
            data[i] = (byte) dat;
        }
        is.close();
        return data;
    }

    /**
     * Handles the HTTP <code>GET</code> method.
     * 
     * @param request servlet request
     * @param response servlet response
     */
    protected void doGet(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     * 
     * @param request servlet request
     * @param response servlet response
     */
    protected void doPost(HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException {
        processRequest(request, response);
    }

    /**
     * Returns a short description of the servlet.
     */
    public String getServletInfo() {
        return "Short description";
    }

    /**
     * Called when the serclet is initialized (the first time it runs)
     * This sets up all the listeners (the user managers) per game
     */
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        servletListeners = JMEHttpUserManager.getServletListeners();
    }

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
