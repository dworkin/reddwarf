/* 
 * Copyright (c) 2001, Sun Microsystems Laboratories 
 * All rights reserved. 
 * 
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met: 
 * 
 *     Redistributions of source code must retain the 
 *     above copyright notice, this list of conditions 
 *     and the following disclaimer. 
 *             
 *     Redistributions in binary form must reproduce 
 *     the above copyright notice, this list of conditions 
 *     and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution. 
 *             
 *     Neither the name of Sun Microsystems, Inc. nor 
 *     the names of its contributors may be used to endorse 
 *     or promote products derived from this software without 
 *     specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND 
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE. 
 */

/*
 * StockViewer.java
 */
package com.sun.multicast.reliable.applications.stock;

import com.sun.multicast.reliable.RMException;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.transport.tram.TRAMTransportProfile;
import com.sun.multicast.reliable.transport.SessionDownException;
import com.sun.multicast.reliable.transport.SessionDoneException;
import com.sun.multicast.reliable.transport.IrrecoverableDataException;
import com.sun.multicast.reliable.transport.MemberPrunedException;
import com.sun.multicast.reliable.transport.tram.TRAMStats;
import com.sun.multicast.reliable.channel.Channel;
//import com.sun.multicast.reliable.channel.LocalChannel;
import com.sun.multicast.reliable.channel.ChannelManagerFinder;
import com.sun.multicast.reliable.channel.PrimaryChannelManager;
//import com.sun.multicast.reliable.channel.LocalPCM;
import com.sun.multicast.reliable.channel.ChannelNotFoundException;
import java.awt.*;
import java.awt.event.*;
import java.applet.Applet;
import java.net.*;
import java.util.*;
import java.io.*;
import java.text.*;

/**
 * Stock quote viewer. Server uses yahoo to obtain stock quote information.
 */
public class StockViewer extends Frame implements 
    ActionListener, MouseListener, StockDebugFlags, Runnable {

    private static String applicationName = "SunNewsChannel";
    private static String channelName = "SunNewsChannel";
    private int waitTime = 300;     // seconds.
    private Channel channel;
    private PrimaryChannelManager pcm = null;
    private RMPacketSocket ms = null;
    private TRAMTransportProfile tp = null;
    Button bnQuit;
    Button bnSunweb;
    Button bnConfig;
    Button bnUpdate;
    DragListener drag;
    Font theFont = null;
    FontMetrics fm = null;
    Graphics lg = null;
    Graphics imgG = null;
    Image image = null;
    Label initLabel;
    Button initQuit;
    String initText;
    Label tickersLabel;
    Label changeLabel;
    Label bannerLabel;
    java.awt.TextArea bannerPane;
    java.awt.TextArea bylinePane;
    Thread timer = null;
    Window win;
    Panel initPanel;
    Panel northPanel;
    Panel centralPanel;
    Panel southPanel;
    int gPos = 0;
    int pos = 0;
    String message = null;
    String text = null;
    String tickers = "SUNW+IBM+AOL";
    static boolean sunwDemo = false;
    static boolean sunTicker = false;
    String downForRepairMsg = null;
    int statCount = 0;
    boolean channelFound;
    boolean firstTime = true;
    int cacheSize = 60;
    boolean quit = false;

    String headlinePage;
    Calendar articleCalendar;
    int year;
    int month;
    int day;
    String urlContentString;
    boolean newSunweb = false;
    String myTickers = "SUNW+IBM+AOL";
    String tickerList[];
    int currentTickerIndex = 0;
    int tickerCount = 0;
    int SUNWIndex = 0;
    int PressIndex = 0;
    int ShowNewsIndex = 0;
    boolean showPressNews = true;
    boolean showSUNWNews = true;
    boolean showShowNews = false;
    int maxIndex = 10;

    /* Default setup, will mostly be overriden by attributes. */

    int x = 80;
    int y = 0;
    int width = 600;
    int height = 25;
    int bannerHeight = 20;
    int fontSize = 14;
    int bannerFontSize;
    int bannerPaneRows = 3;
    int bannerPaneColumns = 40;
    Cursor handCursor = new Cursor(HAND_CURSOR);
    Cursor defaultCursor;
    
    boolean bold = false;
    String stockServerAddress = "224.100.100.100";
    int dataPort = 4567;  // TRAM's reserved port number - 4567
    String fileName = null;
    int SAPTimeout = 0;
    int logMask = TRAMTransportProfile.LOG_INFO;
    Properties properties = null;
    private String configFile;
    private DataReceiver dataReceiver;
    private String logFile = "StockViewer.log";
    private PrintStream logStream = null;
    private String receiverKillIndicator = "/tmp/KillStockViewer";


    private void log(String line) {
        logStream.println(line);
        logStream.flush();
    }   

    private void exit(int exitStatus) {
	if (dataReceiver != null) {
	    dataReceiver.quit();
	    dataReceiver = null;
	}

	quit = true;
	System.exit(exitStatus);
    }

    public StockViewer(String args[]) {
	ArgParser argParser = new ArgParser(args);

        logFile = argParser.getString("ViewerLog", "Vl", logFile);

	defaultCursor = getCursor();   // save cursor in case we change it later

        try {
            logStream = new PrintStream(new BufferedOutputStream(
            new FileOutputStream(logFile, false)));
        } catch (Exception e) {
            System.out.println(e.toString());
            System.exit(1);
        }

	this.logStream = logStream;
	System.setOut(logStream);

	properties = new Properties();

	Properties systemProperties = System.getProperties();

	String configFile = systemProperties.getProperty("user.home") + 
	    "/.StockViewer.cfg";

	if (argParser.getBoolean("SDoDataTransfer", "X", false)) {
	    dataReceiver = new DataReceiver(argParser);
	    dataReceiver.go();

            while (!dataReceiver.initDone()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
	    }
	}

	/*
	 * Start the StockViewer
	 */
	try {
	    properties.load(new FileInputStream(configFile));

	    String tmp;

	    if ((tmp = properties.getProperty("stockviewer.x")) != null)
		x = Integer.parseInt(tmp);

	    if ((tmp = properties.getProperty("stockviewer.y")) != null)
		y = Integer.parseInt(tmp);

	    if (x < 0) {
		log("Invalid x coordinate " + x + 
		    " Setting x to default value");
		// System.err.println("Invalid x coordinate " + x + 
		//    " Setting x to default value");

		x = 80;

	        properties.put("stockviewer.x", "" + x);
	        saveProperties(properties);
	    }

	    if (y < 0) {
		log("Invalid y coordinate " + y + 
		    " Setting y to default value");
		// System.err.println("Invalid y coordinate " + y + 
		//     " Setting y to default value");

	        properties.put("stockviewer.y", "" + y);
	        saveProperties(properties);
		y = 0;
	    }

	    // first choice for tickers is the command line, 
	    // then config file, then default
	    tmp = argParser.getString("VTickers", null, null);
	    if (tmp != null) 
		myTickers = tmp;
	    else if ((tmp = properties.getProperty("stockviewer.tickers")) != 
		null) {
		myTickers = tmp;
	    }

	    if ((tmp = properties.getProperty("stockviewer.SUNWNews")) != 
		null) {

		if (tmp.compareTo("true") == 0)
		    showSUNWNews = true;
		else
		    showSUNWNews = false;
	    }

	    if ((tmp = properties.getProperty("stockviewer.PressNews")) != 
		null) {

		if (tmp.compareTo("true") == 0)
		    showPressNews = true;
		else
		    showPressNews = false;
	    }
	    if ((tmp = properties.getProperty("stockviewer.ShowNews")) != 
		null) {

		if (tmp.compareTo("true") == 0)
		    showShowNews = true;
		else
		    showShowNews = false;
	    }
	} catch (Exception e) {
	    log("Couldn't get properties from " + configFile);
        }

	x = argParser.getInteger("Vx", null, x);
	y = argParser.getInteger("Vy", null, y);

	sunwDemo = argParser.getBoolean("VSUNWDemo", null, sunwDemo);
	sunTicker = argParser.getBoolean("VSunTicker", null, sunTicker);

	if (sunTicker) {
	    applicationName = new String("SunTickerChannel");
	    channelName = new String("SunTickerChannel");
	}

	width = argParser.getInteger("Vwidth", null, width);
	height = argParser.getInteger("Vheight", null, height);
	fontSize = argParser.getInteger("VfontSize", null, fontSize);
	bold = argParser.getBoolean("VBold", null, bold);
	stockServerAddress = 
	    argParser.getString("StockServerAddress", "Sa", stockServerAddress);
        dataPort = argParser.getInteger("SDataPort", "Sp", dataPort);
        fileName = argParser.getString("SChannelFile", null, null);
	SAPTimeout = argParser.getInteger("VSAPTimeout", null, 0);
	logMask = argParser.getInteger("VLogMask", "Vm", logMask);

	//
	// Set width and height based on font size
	//
	setDimensions();

	log("x = " + x);
	log("y = " + y);
	log("width = " + width);
	log("height = " + height);
	log("bannerheight = " + bannerHeight);
	log("fontSize = " + fontSize);
	log("stockServerAddress = " + stockServerAddress);
	log("dataPort = " + dataPort);
	log("fileName = " + fileName);
	log("SAPTimeout = " + SAPTimeout);
	log("sunwDemo = " + sunwDemo);
	log("sunTicker = " + sunTicker);
	log("cacheSize = " + cacheSize);
	log("logMask = " + logMask);

        win = new Window(this);

	initPanel = new Panel();
	northPanel = new Panel();
	centralPanel = new Panel();
	southPanel = new Panel();

        drag = new DragListener(win);
        
	downForRepairMsg = 
	    argParser.getString("VDownForRepair", null, downForRepairMsg);

        if (sunwDemo) 
	    initDisplaySunwDemo();
	
	else if (sunTicker)
	    initSunTicker();
	
	else initDisplay();

        win.setVisible(true);

	//
	// We use the fileName and SAPTimeout arguments to determine
	// how to find the channel.
	//
	//	Filename   SAPTimeout
	//	--------   ----------
	//	  False	     0		Use SAP, don't timeout
	//	  False      non-0	Use SAP, give up after timeout
	//	  True       0		Use the specified fileName
	//        True	     non-0	Try SAP until timeout, then use fileName
	//
	if (fileName == null)
	    channelFound = locateChannel(null);			// use SAP
	else {
	    if (SAPTimeout == 0) {
	    	channelFound = locateChannel(fileName);		// use fileName
	    } else {
		//
		// try SAP first, then try fileName
		//
		if ((channelFound = locateChannel(null)) == false) {
		    log("Couldn't find advertisement for " + channelName);

		    log("Using channel information from " + fileName);

		    channelFound = locateChannel(fileName);
		}
	    }
	}

	if (channelFound) {
            timer = new Thread(this);
            timer.start();
	} else {
	    log("Unable to locate " + channelName + ".  Giving up...");

	    if (sunwDemo || sunTicker) {
	        initLabel.setText("Unable to locate " + channelName + 
		    ".  Giving up...");
	    }

	    try {
		Thread.sleep(5);
	    } catch (Exception e) {}
	}
    }

    private void setDimensions() {
	if (sunwDemo) {
	    width = fontSize * 28;
	    height = fontSize * 20 / 14;

	    if (height < 25)
		height = 25;

	    bannerHeight = height * 4 / 5;
	    bannerFontSize = 11 * height / 25;
	}

	if (sunTicker) {
	    width = fontSize * 28;
	    height = fontSize * 20 / 14;

	    if (height < 25)
		height = 25;

	    bannerFontSize = 13;
	    bannerHeight = 17 * (bannerPaneRows + 1) + 20;
	}
    }

    private void getNewData() {
        if (StockViewer_Debug) {
            log("StockViewer: getNewData.");
        }

        text = message;

	message = null;
        pos = 0;
    }

    public void actionPerformed(ActionEvent event) {
        if (StockViewer_Debug) {
            log("StockViewer: action.");
        }

        getNewData();
    }

    private boolean locateChannel(String fileName) {
        if (StockViewer_Debug) {
            log("StockViewer: locateChannel.");
        }

	try {
	    pcm = (PrimaryChannelManager)
		ChannelManagerFinder.getPrimaryChannelManager(null);
        } catch (Exception e) {
            log(e.toString());
            e.printStackTrace(System.out);
	    return false;
        }	
	
	if (fileName != null) {
            if (StockViewer_Debug)
                log("Using filed channel");

	    try {
	        channel = (Channel) pcm.readChannel(fileName);
	        tp = (TRAMTransportProfile) channel.getTransportProfile();

		tp.setLogMask(logMask);

                ms = tp.createRMPacketSocket(TransportProfile.RECEIVER);
            } catch (Exception e) {
                log(e.toString());
                e.printStackTrace(System.out);
	        return false;
            }
	    return true;
	} 

	int n = 0;
	    
        while (true) {
	    n++;
	    if (downForRepairMsg == null && (sunwDemo || sunTicker) && 
		(n % 15) == 0) {
	
		initLabel.setText(initText);
	    }

	    if (downForRepairMsg == null && (sunwDemo || sunTicker)) 
	        initLabel.setText(initLabel.getText() + "."); // once per second

	    try {
                long channelids[] = pcm.getChannelList(channelName, 
                 			               applicationName);

                if (channelids.length > 0) {
                    channel = pcm.getChannel(channelids[0]);
		    log("Found channel..." + channel.getChannelName());
                    tp = (TRAMTransportProfile) channel.getTransportProfile();

		    tp.setLogMask(logMask);

                    ms = tp.createRMPacketSocket(TransportProfile.RECEIVER);

		    if (downForRepairMsg == null && (sunwDemo || sunTicker)) {
		        initLabel.setText("Waiting for first quote...");
		    }

		    return true;
                }

		if (SAPTimeout != 0 && n > SAPTimeout)
		    return false;		// Couldn't find the channel

                Thread.sleep(1000);
	    } catch (ChannelNotFoundException e) {
                // This shouldn't happen unless the channel went
                // away after we got the id.  Try again...
                log("ChannelNotFoundException!");
            } catch (Exception e) {
                log(e.toString());
                e.printStackTrace(System.out);
	        break;
            }
        }
	return false;
    }

    private synchronized void sunwDemo() {
        text = message;		// get new data

	if (downForRepairMsg != null || text == null) {
	    win.validate();
	    return;		// nothing to display
	}

	if (firstTime) {
	    firstTime = false;
            win.remove(southPanel);
	    win.remove(initPanel);
	    win.setBounds(x, y, width, height);
	    win.add(northPanel, BorderLayout.NORTH);
	    win.validate();
	}

	// search for SUNW...
	// Stock price follows immediately after.
	// Price change follows and is enclosed with parentheses.
	//
	// For example:  4:01PM SUNW...101 7/8 (+30 1/4)
	//
	// Sometimes there's a time before the quote and other times not.
	// If there's one before the quote we'll use that.

	String sunw = "SUNW...";

	int i;

	if ((i = text.indexOf(sunw)) == -1) {
	    log("Didn't find SUNW... in quote string");
	    
            if (firstTime)
                initLabel.setText(" Unable to get quote from Yahoo!");
            else
                changeLabel.setForeground(Color.blue);

	    return;
	}

	int lineStart = i - 8;

	String ticker = text.substring(i - 8, i - 1) + " ET   SUNW  ";
		
	int priceStart = i + sunw.length();

	if ((i = text.indexOf("(", priceStart)) == -1) {
	    log("Didn't find '(' in quote string");
	    return;
	}

	ticker += text.substring(priceStart, i - 1);

	int changeStart = i + 1;

	if ((i = text.indexOf(")", changeStart)) == -1) {
	    log("Didn't find ')' in quote string");
	    return;
	}

	// log(text.substring(lineStart, i + 1));

	String priceChange = text.substring(changeStart, i);

	if (priceChange.charAt(0) == '-')
	    changeLabel.setForeground(Color.red);
	else
	    changeLabel.setForeground(Color.green);

	tickersLabel.setText(ticker);
	changeLabel.setText(priceChange);
    }

    private synchronized void sunTicker() {

        text = message;		// get new data

	if (downForRepairMsg != null || text == null) {
	    win.validate();
	    return;		// nothing to display
	}


	if (firstTime) {
	    firstTime = false;

	    bannerPane.setFont(new Font("Monospaced", Font.PLAIN, 13));
	    bannerPane.setText(" Waiting for headline");

	    win.remove(initPanel);
	    win.add(northPanel, BorderLayout.NORTH);
	    win.validate();
	}

	// search for SUNW...
	// Stock price follows immediately after.
	// Price change follows and is enclosed with parentheses.
	//
	// For example:  4:01PM SUNW...101 7/8 (+30 1/4)
	//
	// Sometimes there's a time before the quote and other times not.
	// If there's one before the quote we'll use that.

	//	String sunw = "SUNW...";

	String currentTicker = tickerList[currentTickerIndex];
	if (++currentTickerIndex >= tickerCount)
	    currentTickerIndex = 0;

	int i;

	try {
	    if ((i = text.indexOf(" " + currentTicker + "...")) == -1) {
		if (currentTicker.length() != 0) {
		    log("Didn't find " + currentTicker + "... in quote string");
		    
		    if (firstTime)
			initLabel.setText(" Unable to get quote from Yahoo!");
		    else {
			changeLabel.setForeground(Color.blue);
			tickersLabel.setText(currentTicker);
			changeLabel.setText("no quote from Yahoo");
		    }
		}
	    } else {
		i++; // point to the symbol itself
		int lineStart = i - 8;
		
		String ticker = text.substring(i - 8, i - 1) + " ET   " + 
		    currentTicker + "  ";
		
		int priceStart = i + currentTicker.length() + 3;  // 3 for "..."
	    
		if ((i = text.indexOf("(", priceStart)) == -1) {
		    log("Didn't find '(' in quote string");
		    return;
		}
	    
		ticker += text.substring(priceStart, i - 1);
		
		int changeStart = i + 1;
	    
		if ((i = text.indexOf(")", changeStart)) == -1) {
		    log("Didn't find ')' in quote string");
		    return;
		}
	    
		// log(text.substring(lineStart, i + 1));
	    
		String priceChange = text.substring(changeStart, i);
		
		if (priceChange.charAt(0) == '-')
		    changeLabel.setForeground(Color.red);
		else
		    changeLabel.setForeground(Color.green);
		
		tickersLabel.setText(ticker);
		changeLabel.setText(priceChange);
	    }

	    displayHeadline();
	} catch (Exception e) {
	    changeLabel.setForeground(Color.blue);
	    tickersLabel.setText(currentTicker);
	    changeLabel.setText("bad format from Yahoo");
	}

	try {
	    int sleepTime = 5000;
	    
	    // sleep lightly just after every hour to flush out the input queue
	    Calendar rightNow = new GregorianCalendar();
	    if (rightNow.get(Calendar.MINUTE) < 5)
		sleepTime = 1000;
	    
	    Thread.sleep(sleepTime);
	} catch (InterruptedException e) {}
    }

    private void displayHeadline() {

	// first decide which ones we display
	if (SUNWIndex != 0) {
	    // see if we've hit the limit
	    if (++SUNWIndex > maxIndex) {
		if (showPressNews) {
		    PressIndex = 1;
		    SUNWIndex = 0;
		}
		else
		    SUNWIndex = 1;
	    }
	} else if (PressIndex != 0) {
	    // see if we've hit the limit
	    if (++PressIndex > maxIndex) {
		if (showSUNWNews) {
		    SUNWIndex = 1;
		    PressIndex = 0;
		}
		else
		    PressIndex = 1;
	    }
	}

	if (ShowNewsIndex != 0) {
	    // see if we've hit the limit
	    if (++ShowNewsIndex > maxIndex) 
		ShowNewsIndex = 1;
	}

	try {
	    if ((SUNWIndex != 0) && (text.indexOf("SUNW: ") != -1) && 
		(text.indexOf("Press: ") != -1)) {
		urlContentString = text.substring(text.indexOf("SUNW: ") + 6, 
		    text.indexOf("Press: ") - 1);
	    } else if ((PressIndex != 0) && (text.indexOf("Press: ") != -1)) {
		if (text.indexOf("Show: ") == -1) {
		    urlContentString = 
			text.substring(text.indexOf("Press: ") + 7);
		} else  {
		    urlContentString = text.substring(
			text.indexOf("Press: ") + 7, 
			text.indexOf("Show: ") - 1);
		}
	    } else if ((ShowNewsIndex != 0) && (text.indexOf("Show: ") != -1))
		urlContentString = text.substring(text.indexOf("Show: ") + 6);
	    else {
		scrollHeadline("    ");
		urlContentString = null;
	    }

	    // Isolate the url in case they click on it
	    if (urlContentString != null) {
		headlinePage = new String(urlContentString.substring(
		    urlContentString.indexOf("http")));
		headlinePage = headlinePage.substring(0, 
		    headlinePage.indexOf("\""));
		
		// Isolate the headline and display it
		scrollHeadline(urlContentString.substring(0, 
		    urlContentString.lastIndexOf("[")));
	    }
	} catch (Exception e) { 
	    // bad format, just skip it
	    return;
	}
    }

    private void scrollHeadline(String headline) {

	// first, remove any html formatting
	try {
	    while (headline.indexOf("<") != -1) {
		headline = headline.substring(0, headline.indexOf("<")) + 
		    headline.substring(headline.indexOf(">") + 1);
	    }
	    
	    if (bannerPane.getForeground() != Color.blue) 
		// if it's blue, keep it that way
		setBannerColor();
	    
	    bannerPane.setText(headline);

	    win.validate();
	} catch (Exception e) { 
	    System.err.println(e); 
	    System.err.println("Bad headline" + headline); 
	}

    }


    private void setBannerColor() {
	// if it's new today, set the color to green
	
	articleCalendar = new GregorianCalendar();
	year = articleCalendar.get(Calendar.YEAR);
	month = articleCalendar.get(Calendar.MONTH) + 1;
	day = articleCalendar.get(Calendar.DAY_OF_MONTH);
	
	String monthFiller = "", dayFiller = "";
	
	if (month < 10)
	    monthFiller = new String("0");
	
	if (day < 10)
	    dayFiller = new String("0");
	
	String today = new String(year + monthFiller + month + dayFiller + day);
	if ((headlinePage != null) && (headlinePage.indexOf(today) != -1)) {
	    bannerPane.setForeground(Color.green.darker());
	    // also set the SunWeb color to green
	    bnSunweb.setForeground(Color.green.darker());
	} else {
	    bannerPane.setForeground(Color.black);
	}
    }

    private synchronized void scroll() {
        if (text == null) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {}

            getNewData();

            return;
        }

        if (theFont == null) {
            theFont = new Font("Monospaced", Font.PLAIN, fontSize);
	
            tickersLabel.setFont(theFont);
        }

        int width = tickersLabel.getSize().width;
        int height = tickersLabel.getSize().height;

        if (image == null) {
            image = createImage(width, height);
            imgG = image.getGraphics();

            imgG.setFont(theFont);
        }

        imgG.setColor(Color.black);
        imgG.fillRect(0, 0, width, height);
        imgG.setColor(Color.yellow);

        if (fm == null) {
            fm = imgG.getFontMetrics();
        }
        if (text.length() < 2) {
            return;
        }

        gPos += 1;

        if (gPos >= fm.charWidth(text.charAt(pos))) {
            gPos = 0;
            pos++;

            if (pos == text.length() - 1) {
                pos = 0;

                getNewData();
            }
        }

        int end = pos + 130;

        if (end > text.length() - 1) {
            end = text.length() - 1;
        }

        String newText = text.substring(pos, end);

        if (lg == null) {
            lg = tickersLabel.getGraphics();
        }

        imgG.drawString(newText, -gPos, (height + fm.getAscent()) / 2);
        lg.drawImage(image, 0, 0, this);
    }

    public void run() {
        int payloadlen;
        long lengthsofar = 0;
        DatagramPacket recvPacket = null;

        if (StockViewer_Debug) {
            log("StockViewer: run.");
        }

	boolean firstTime = true;

        while (quit == false) {
	    boolean received = false;

            try {
		received = false;
                recvPacket = ms.receive();
                received = true;

		if (quit)
		    break;

		if (firstTime) {
		    firstTime = false;

            	    TRAMStats stat = (TRAMStats)ms.getRMStatistics();

            	    InetAddress[] addresses = stat.getSenderList();

            	    if (addresses != null)
                	log("Sender is " + addresses[0]);

		    log("User name is " + System.getProperty("user.name"));
		}
            } catch (SessionDoneException sd) {
                exit(1);
            } catch (SessionDownException sdwn) {
		log(new Date() + " Session Down Exception. " +
		    "The StockServer has stopped sending...");
                exit(2);
            } catch (IrrecoverableDataException ie) {
		log("IrrecoverableDataException");
	    } catch (MemberPrunedException mpe) {
		log("MemberPrunedException!");
	    } catch (RMException se) {
                se.printStackTrace(System.out);
            } catch (IOException e) {
                e.printStackTrace(System.out);
            } catch (Exception e) {
                e.printStackTrace(System.out);
                exit(3);
	    }

            if (received) {
                message = new String(recvPacket.getData());

		if (sunwDemo)
		    sunwDemo();	// display new info right now

		else if (sunTicker) 
		    sunTicker();	// display new info right now
	    }
        }
	log(new Date() + " The StockServer has stopped sending...");
        exit(2);
    }

    public static void main(String args[]) {
        StockViewer stockViewer = new StockViewer(args);

	if (!stockViewer.channelFound)
	    stockViewer.exit(4);

        while (stockViewer.quit == false) {
	    if (sunwDemo == true) {
		stockViewer.sunwDemo();

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {}
	    } else if (sunTicker == true) {
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {}
	    } else
                stockViewer.scroll();

            try {
                FileInputStream in = 
		    new FileInputStream(stockViewer.receiverKillIndicator);

		in.close();
                stockViewer.quit = true;
            } catch (Exception e) {
            }
        }
    }

    class DragListener extends MouseAdapter implements MouseMotionListener {
        Point anchor;
        Component comp;

        public DragListener(Component comp) {
            anchor = new Point(0, 0);
            this.comp = comp;
        }

        public void mousePressed(MouseEvent e) {
            anchor = e.getPoint();
        }

        public void mouseDragged(MouseEvent e) {
            Point location = comp.getLocation();

            location.translate(e.getX() - anchor.x, e.getY() - anchor.y);
            comp.setLocation(location);

	    x = (int)location.getX();
	    y = (int)location.getY();

	    properties.put("stockviewer.x", "" + (int)location.getX());
	    properties.put("stockviewer.y", "" + (int)location.getY());

	    saveProperties(properties);
	}

        public void mouseMoved(MouseEvent e) {}

    }

    public void saveProperties(Properties properties) {
	FileOutputStream tmp = null;

	Properties systemProperties = System.getProperties();

	String configFile = systemProperties.getProperty("user.home") + 
	    "/.StockViewer.cfg";

        try {
	    tmp = new FileOutputStream(configFile);

            // JDK1.2

            properties.store(tmp, "Sun Labs - Stock Viewer Property Settings");
	    tmp.close();
        } catch (NoSuchMethodError ex) {

            // JDK1.1

            properties.save(tmp, "Sun Labs - Stock Viewer Property Settings");
	    try {
		tmp.close();
	    } catch (Exception ex1) {
	    }
        } catch (Exception ex2) {
	    log("Exception! " + ex2);
	    // ignore errors
	}
    }

    private boolean mouseEntered = false;
 
    public void mouseEntered(MouseEvent mouseEvent) {
	mouseEntered = true;
	if (mouseEvent.getComponent() == bannerPane) {
	    if (headlinePage != null) {
		bannerPane.setCursor(handCursor);
		bannerPane.setForeground(Color.blue);
	    }
	} else if (mouseEvent.getComponent() == bylinePane) {
	    bylinePane.setCursor(handCursor);
	    bylinePane.setForeground(Color.blue);
	}
    }

    public void mouseExited(MouseEvent mouseEvent) {
	mouseEntered = false;
	if (mouseEvent.getComponent() == bannerPane) {
	    bannerPane.setCursor(defaultCursor);
	    setBannerColor();
	} else if (mouseEvent.getComponent() == bylinePane) {
	    bylinePane.setCursor(defaultCursor);
	    bylinePane.setForeground(Color.black);
	}    
    }

    public void mousePressed(MouseEvent mouseEvent) {
    }

    public void mouseClicked(MouseEvent mouseEvent) {
    }

    public void mouseReleased(MouseEvent mouseEvent) {
	if (mouseEvent.getComponent() == bnQuit) {
	    if (downForRepairMsg == null && mouseEntered == true) {
		if (ms != null)
		    ms.abort();
		
		exit(0);
	    }
	} else if (mouseEvent.getComponent() == bannerLabel)
	    startNetscape(headlinePage);
	else if (mouseEvent.getComponent() == bannerPane)
	    startNetscape(headlinePage);
	else if (mouseEvent.getComponent() == bylinePane)
	    startNetscape("http://bcn.east/jrms/ticker.html");
 	else if (mouseEvent.getComponent() == bnSunweb)
	    startNetscape("http://sunweb.ebay");
 	else if (mouseEvent.getComponent() == bnConfig)
	    startConfig();
    }

    private void startConfig() {
	(new Config(this, true)).show();
    }

    private void startNetscape(String url) {
	String UnixString = 
	    "netscape -noraise -remote OpenURL(" + url+ ",new-window)";

	String WindowsString = "netscape " + url;

	Runtime runTime = Runtime.getRuntime();

	if (url != null) {
	    try {
		if (System.getProperty("file.separator").equals("/")) 
		    // for Unix-like systems...
		    runTime.exec(UnixString);

		else
		    // for Windows-like systems...
		    runTime.exec(WindowsString);
	    } catch (IOException e) {
		System.out.println("exception " + e);
	    }
	}
    }

    private void initDisplaySunwDemo() {
	int style = Font.PLAIN;
	
	if (bold)
	    style = Font.BOLD;

	theFont = new Font("Monospaced", style, fontSize);
	setFont(theFont);

	win.setLayout(new BorderLayout());
	win.setBounds(x, y, width, height + bannerHeight);
	win.setBackground(Color.black);
	win.setForeground(Color.white);
	
	GridBagConstraints c = new GridBagConstraints();
	
	GridBagLayout initLayout = new GridBagLayout();
	initPanel.setLayout(initLayout);
	
	if (downForRepairMsg != null)
	    initText = downForRepairMsg;
	else
	    initText = " Locating " + applicationName;
	
	initLabel = new Label(initText);
	c.weightx = .5;
	c.fill = GridBagConstraints.BOTH;
	initLayout.setConstraints(initLabel, c);
	initPanel.add(initLabel);
	
	if (downForRepairMsg != null)
	    initQuit = new Button("Wait");
	else
	    initQuit = new Button("Quit");
	
	initQuit.addMouseListener(this);
	initQuit.setBackground(Color.gray);
	c.weightx = 0;
	initLayout.setConstraints(initQuit, c);
	initPanel.add(initQuit);
	
	win.add(initPanel, BorderLayout.NORTH);
	
	GridBagLayout northLayout = new GridBagLayout();
	northPanel.setLayout(northLayout);
	
	tickersLabel = new Label();
	c.weightx = .62;
	c.fill = GridBagConstraints.BOTH;
	c.anchor = GridBagConstraints.WEST;
	northLayout.setConstraints(tickersLabel, c);
	northPanel.add(tickersLabel);
	
	changeLabel = new Label();
	c.weightx = .20;
	c.anchor = GridBagConstraints.CENTER;
	northLayout.setConstraints(changeLabel, c);
	northPanel.add(changeLabel);
	
	bnQuit = new Button("Quit");
	bnQuit.addMouseListener(this);
	bnQuit.setBackground(Color.gray);
	c.weightx = 0;
	c.anchor = GridBagConstraints.EAST;
	northLayout.setConstraints(bnQuit, c);
	northPanel.add(bnQuit);
	
	southPanel.setLayout(new BorderLayout());
	
	bannerLabel = 
	    new Label(" StockViewer by SunLabs: http://bcn.east/projects/jrms");
	bannerLabel.setLocation(0, 0);
	bannerLabel.setSize(width, bannerHeight);
	bannerLabel.setBackground(Color.gray);
	bannerLabel.setFont(new Font("Monospaced", Font.ITALIC, 
	    bannerFontSize));

	southPanel.add(bannerLabel);

	win.add(southPanel, BorderLayout.SOUTH);

	initLabel.addMouseListener(drag);
	initLabel.addMouseMotionListener(drag);
	initQuit.addMouseListener(this);
	tickersLabel.addMouseListener(drag);
	tickersLabel.addMouseMotionListener(drag);
	changeLabel.addMouseListener(drag);
	changeLabel.addMouseMotionListener(drag);
	bnQuit.addMouseListener(this);
	bannerLabel.addMouseMotionListener(drag);
    }

    private void initSunTicker() {

	int style = Font.PLAIN;
	
	if (bold)
	    style = Font.BOLD;

	theFont = new Font("Monospaced", style, fontSize);
	setFont(theFont);

	win.setLayout(new BorderLayout());
	win.setBounds(x, y, width, height + bannerHeight);
	win.setBackground(Color.black);
	win.setForeground(Color.white);
	
	GridBagConstraints c = new GridBagConstraints();
	
	GridBagLayout initLayout = new GridBagLayout();
	initPanel.setLayout(initLayout);
	
	if (downForRepairMsg != null)
	    initText = downForRepairMsg;
	else
	    initText = " Locating " + applicationName;
	
	initLabel = new Label(initText);
	c.weightx = .5;
	c.fill = GridBagConstraints.BOTH;
	initLayout.setConstraints(initLabel, c);
	initPanel.add(initLabel);
	
	if (downForRepairMsg != null)
	    initQuit = new Button(" Wait ");
	else
	    initQuit = new Button(" Quit ");
	
	initQuit.addMouseListener(this);
	initQuit.setBackground(Color.gray);
	c.weightx = 0;
	initLayout.setConstraints(initQuit, c);
	initPanel.add(initQuit);
	
	win.add(initPanel, BorderLayout.NORTH);
	
	GridBagLayout northLayout = new GridBagLayout();
	northPanel.setLayout(northLayout);
	
	tickersLabel = new Label();
	c.weightx = .62;
	c.fill = GridBagConstraints.BOTH;
	c.anchor = GridBagConstraints.WEST;
	northLayout.setConstraints(tickersLabel, c);
	northPanel.add(tickersLabel);
	
	changeLabel = new Label();
	c.weightx = .20;
	c.anchor = GridBagConstraints.CENTER;
	northLayout.setConstraints(changeLabel, c);
	northPanel.add(changeLabel);
	
	bnQuit = new Button(" Quit ");
	bnQuit.setSize(bnQuit.getSize());
	bnQuit.addMouseListener(this);
	bnQuit.setBackground(Color.gray);
	c.weightx = 0;
	c.anchor = GridBagConstraints.EAST;
	northLayout.setConstraints(bnQuit, c);
	northPanel.add(bnQuit);
	
	GridBagLayout centralLayout = new GridBagLayout();
	centralPanel.setLayout(centralLayout);
	
	c.weightx = .62;
	c.fill = GridBagConstraints.BOTH;
	c.anchor = GridBagConstraints.WEST;
	bannerPane = new TextArea("Waiting for headline                   ", 
	    bannerPaneRows, bannerPaneColumns, 
	    java.awt.TextArea.SCROLLBARS_NONE);
	bannerPane.setFont(new Font("Monospaced", Font.PLAIN, 13));
	bannerPane.setEditable(false);
	centralLayout.setConstraints(bannerPane, c);
	bannerPane.setBackground(Color.white);

	centralPanel.add(bannerPane);
	win.add(centralPanel, BorderLayout.CENTER);

	bnSunweb = new Button("SunWeb");
	bnSunweb.addMouseListener(this);
	bnSunweb.setBackground(Color.gray);
	c.weightx = 0;
	c.anchor = GridBagConstraints.EAST;
	centralLayout.setConstraints(bnSunweb, c);
	centralPanel.add(bnSunweb);
	
	GridBagLayout southLayout = new GridBagLayout();
	southPanel.setLayout(southLayout);
	c.weightx = .62;
	c.fill = GridBagConstraints.BOTH;
	c.anchor = GridBagConstraints.WEST;
	bylinePane = new TextArea(
	    "Ticker by SunLabs: http://bcn.east/jrms        ", 
	    1, bannerPaneColumns, java.awt.TextArea.SCROLLBARS_NONE);
	southLayout.setConstraints(bylinePane, c);
	bylinePane.setBackground(Color.white);
	bylinePane.setFont(new Font("Monospaced", Font.ITALIC, bannerFontSize));
	bylinePane.setEditable(false);

	southPanel.add(bylinePane);
	win.add(southPanel, BorderLayout.SOUTH);

	bnConfig = new Button("Config");
	bnConfig.addMouseListener(this);
	bnConfig.setBackground(Color.gray);
	c.weightx = 0;
	c.anchor = GridBagConstraints.EAST;
	southLayout.setConstraints(bnConfig, c);
	southPanel.add(bnConfig);
	

	initLabel.addMouseListener(drag);
	initLabel.addMouseMotionListener(drag);
	initQuit.addMouseListener(this);
	tickersLabel.addMouseListener(drag);
	tickersLabel.addMouseMotionListener(drag);
	changeLabel.addMouseListener(drag);
	changeLabel.addMouseMotionListener(drag);
	bannerPane.addMouseListener(this);
	bannerPane.addMouseMotionListener(drag);
	bylinePane.addMouseListener(this);
	bylinePane.addMouseMotionListener(drag);

	// initialize the list of requested tickers
	initTickers();
	initNews();
    }


    public void initTickers() {
	String counterString = new String(myTickers);

	tickerCount = 1;

	while (counterString.indexOf("+") != -1) {
	    tickerCount++;
	    counterString = 
		counterString.substring(counterString.indexOf("+") + 1);
	}
	
	tickerList = new String[tickerCount];
	
	int i;
	String theTickers = new String(myTickers);
	for (i = 0; i < tickerCount - 1; i++) {
	    tickerList[i] = 
		new String(theTickers.substring(0, theTickers.indexOf("+")));
	    tickerList[i] = tickerList[i];
	    theTickers = theTickers.substring(theTickers.indexOf("+") + 1);
	}
	// get the last one
	tickerList[i] = new String(theTickers);

	currentTickerIndex = 0;
    }

    public void initNews() {

	PressIndex = 0;
	SUNWIndex = 0;

	if (showSUNWNews)
	    SUNWIndex = 1;

	else if (showPressNews) 
	    PressIndex = 1;

	else if (showShowNews)
	    ShowNewsIndex = 1;
    }

    private void initDisplay() {
	win.setLayout(new BorderLayout());

	tickersLabel = new Label(tickers);

	theFont = new Font("Monospaced", Font.PLAIN, fontSize);
	
	tickersLabel.setFont(theFont);
	tickersLabel.setBackground(Color.black);
	tickersLabel.setForeground(Color.white);
	
	win.add("Center", tickersLabel);
	
	bnUpdate = new Button("Update");

	bnUpdate.addActionListener(this);
	bnUpdate.setBackground(Color.gray);

	bnQuit = new Button("Quit");
	
	bnQuit.addMouseListener(this);
	bnQuit.setBackground(Color.gray);

	win.add("West", bnUpdate);
	win.add("East", bnQuit);

	tickersLabel.addMouseListener(drag);
	tickersLabel.addMouseMotionListener(drag);
	win.setBackground(Color.black);
	win.setBounds(x, y, width, height);
    }
}
