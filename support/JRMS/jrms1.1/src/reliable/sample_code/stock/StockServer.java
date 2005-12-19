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
 * StockServer.java
 */
package com.sun.multicast.reliable.applications.stock;

import com.sun.multicast.reliable.RMException;
import com.sun.multicast.reliable.transport.RMPacketSocket;
import com.sun.multicast.reliable.transport.NoMembersException;
import com.sun.multicast.reliable.transport.TransportProfile;
import com.sun.multicast.reliable.transport.tram.TRAMTransportProfile;
import com.sun.multicast.reliable.transport.tram.MROLE;
import com.sun.multicast.reliable.transport.tram.TMODE;
import com.sun.multicast.reliable.channel.Channel;
import com.sun.multicast.reliable.channel.ChannelManagerFinder;
import com.sun.multicast.reliable.channel.PrimaryChannelManager;
import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Stock quote server. Uses yahoo to obtain stock quote information.
 */
public class StockServer implements StockDebugFlags {
    private static String applicationName = "SunNewsChannel";
    private static String channelName = "SunNewsChannel";
    private Channel channel;
    private PrimaryChannelManager pcm = null;
    private RMPacketSocket ms = null;
    private TRAMTransportProfile tp = null;
    private String stockServerAddress = "224.100.100.100";
    private int dataPort = 4567; // TRAM reserved port number - 4567.
    private int delayTime = 10;     // seconds.
    private long maxDataRate = 25000;
    private byte ttl = 1;
    private String abort = null;
    private String content;
    private String text;
    private String tickers = "SUNW+IBM+AOL";
    private String tickerList[];
    private int maxTickers = 100;
    private byte data[] = null;
    private int nTickers;
    private int pos = 0;
    private FileWriter outputFile;
    private FileReader inputFile;
    private String channelFile = null;
    private DataSender dataSender;
    private int cacheSize = 60;
    private String logFile = "StockSender.log";
    private PrintStream logStream = null;
    private boolean sunTicker = false;

    Calendar SUNWArticleCalendar;
    Date SUNWArticleDate;
    int SUNWYear;
    int SUNWMonth;
    int SUNWDay;
    int SUNWArticle;
    int SUNWMaxArticleCount = 10;
    int SUNWArticleCount = 
	SUNWMaxArticleCount;    // force reset of params for first time through
    String SUNWURLContentString;

    Calendar PressArticleCalendar;
    Date PressArticleDate;
    int PressYear;
    int PressMonth;
    int PressDay;
    int PressArticle;
    int PressMaxArticleCount = 10;
    int PressArticleCount = 
	PressMaxArticleCount;    // force reset of params for first time through
    String PressURLContentString;

    int ShowMaxArticleCount = 10;
    int ShowArticleCount = 
	ShowMaxArticleCount;    // force reset of params for first time through
    String ShowURLContentString;
    String News = null;

    public StockServer(String args[]) {
	ArgParser argParser = new ArgParser(args);

        logFile = argParser.getString("StockSenderLog", "Sl", logFile);
	sunTicker = argParser.getBoolean("SSunTicker", null, sunTicker);

        try {
            logStream = new PrintStream(new BufferedOutputStream(
            new FileOutputStream(logFile, true)));
        } catch (Exception e) {
            System.out.println(e.toString());
            System.exit(1);
        }

	this.logStream = logStream;
	System.setOut(logStream);

	setProps();

	if (argParser.getBoolean("SDoDataTransfer", "X", false)) {
	    /*
	     * Start the file sender in a separate thread.
	     */
	    dataSender = new DataSender(argParser);
	    dataSender.go();

	    while (!dataSender.initDone()) {
	        try {
		    Thread.sleep(1000);
		} catch (InterruptedException e) {
		}
	    }
	}

	/*
	 * Start the Stock Ticker Server
	 */
        tickers = argParser.getString("STickers", null, tickers); 
        tickerList = new String[maxTickers];

        makeTickerList();

	if (sunTicker) {
	    applicationName = new String("SunTickerChannel");
	    channelName = new String("SunTickerChannel");
	}

	channelFile = argParser.getString("SChannelFile", null, channelFile);

	if (channelFile != null) {
	    readChannel(channelFile);
	} else {
            stockServerAddress = argParser.getString("StockSenderAddress", 
		"Sa", stockServerAddress);
            dataPort = argParser.getInteger("SDataPort", "Sp", dataPort);
	    ttl = (byte)argParser.getInteger("STTL", null, 1);
            createChannel(stockServerAddress, dataPort);
	}

	String infile = argParser.getString("Sin", null, null);
	String outfile = argParser.getString("Sout", null, null);

	try {
	    if (infile != null)
                inputFile = new FileReader(infile);

	    if (outfile != null)
       	        outputFile = new FileWriter(outfile);
	} catch (IOException e) {
	    e.printStackTrace();
            System.exit(1);
	}
    }
    
    private void log(String line) {
	logStream.println(line);
	logStream.flush();
    }

    private void readChannel(String fileName) {
        if (StockServer_Debug) {
            log("StockServer: readChannel:" + fileName);
        }
        try {
            pcm = (PrimaryChannelManager)
		ChannelManagerFinder.getPrimaryChannelManager(null);
	    channel = (Channel) pcm.readChannel(fileName);
	    tp = (TRAMTransportProfile) channel.getTransportProfile();
            ms = tp.createRMPacketSocket(TransportProfile.RECEIVER);
            // fill in some other parameters file channel does not have
            // ...
        } catch (Exception e) {
            log(e.toString());
            e.printStackTrace();
        }
    }

    private void createChannel(String address, int port) {
        if (StockServer_Debug) {
            log("StockServer: createChannel:" + applicationName + " " + 
		channelName + " address: " + address + " port: " + port);
        }

        try {
            Date dataStartTime;
            InetAddress mcastAddress = InetAddress.getByName(address);

            pcm = (PrimaryChannelManager)
		ChannelManagerFinder.getPrimaryChannelManager(null);
            channel = (Channel) pcm.createChannel();

            channel.setChannelName(channelName);
            channel.setApplicationName(applicationName);

            tp = (TRAMTransportProfile) new TRAMTransportProfile(mcastAddress, 
                    port);

	    // transmit no more than this speed (bytes/sec)
            tp.setMaxDataRate(maxDataRate);
            
            // multicast time-to-live = number hops to go
            tp.setTTL(ttl);
            
            // ordered delivery
            tp.setOrdered(true);
            
            // maximum datagram packet size
            tp.setMaxBuf(20000);
            
            // preference regarding whether to seek repair if joining late
	    tp.setLateJoinPreference(
		TRAMTransportProfile.LATE_JOIN_WITH_NO_RECOVERY);
		
	    // uncomment to test static tree config
	    // tp.setTreeFormationPreference(
            //    TRAMTransportProfile.TREE_FORM_HAMTHA_STATIC_RW);
            // assume the following port is reserved for this service
            // tp.setUnicastPort(8888);

	    tp.setCacheSize(cacheSize);

            channel.setTransportProfile(tp);

	    // set additional parameters for local copy of tranport profile
	    
	    // make local TRAM a sender
            tp.setTmode(TMODE.SEND_ONLY);
            
            // make local TRAM a eager repair head
            tp.setMrole(MROLE.MEMBER_EAGER_HEAD);
            
	    tp.setLogMask(TRAMTransportProfile.LOG_VERBOSE);

	    // transport profile provides a method to create multicast socket
            ms = tp.createRMPacketSocket(TransportProfile.SENDER);

	    // set additional channel parameters
	    
            channel.setAbstract("StockServer");

            dataStartTime = new Date(new Date().getTime() + delayTime * 1000);
            channel.setDataStartTime(dataStartTime);

	    // request advertizing channel using SAP
            channel.setAdvertisingRequested(true);
            
            // start channel
            channel.setEnabled(true);
            
            // write the channel to a file for possible later use?
            // this might be useful if SAP does not work for some reason
            // use channelName as file name
            pcm.fileChannel(channel, channelName);
            Thread.sleep(1000);
        } catch (Exception e) {
            log(e.toString());
            e.printStackTrace();
        }
    }

    private boolean readURL(String sUrl) {
	boolean returnValue = false;

        if (StockServer_Debug) {
            log("StockServer: readURL:" + " url: " + sUrl);
        }

        content = null;
	data = new byte[50000];

        try {
            if (StockServer_Debug) {
                log("StockServer: readURL: opening URL.");
            }

            URL url = new URL(sUrl);

            BufferedInputStream is = 
                new BufferedInputStream((InputStream) url.getContent());

            if (StockServer_Debug) {
                log("StockServer: readURL: reading content.");
            }

            int total = 0;
            int size = 0;

            do {
		if (data.length > 30) {
		    String s = new String(data);

		    if (s.lastIndexOf("</html>") >= 0) {
			log("found </html>.  we've got all the data");
			break;		// we've got all the data
		    }
		}

                total += size;

		int available = 0;

		for (int i = 0; i < 8; i++) {
		    available = is.available();

		    if (available > 0)
			break;

	            try {
		    	Thread.sleep(1000);
		    } catch (InterruptedException e) {
		    }
		}

		if (available <= 0) {
                    if (StockServer_Debug)
		        log("StockServer:  No data available.  Giving up.");

		    return false;
		}

		int len = Math.min(data.length - total, available);

	        log("StockServer: readURL: about to read " + len +
		    " total " + total + " available " + available);

                size = is.read(data, total, len);

                if (StockServer_Debug) {
                    log("StockServer: readURL: reading... " +
			data.length + " total read so far " + total);
                }
            } while (size != -1 && total < data.length);

            if (StockServer_Debug) {
                log("StockServer: readURL: done reading... size " +
		    size + " Got " + total);
	    }

            content = new String(data);
	    returnValue = true;
        } catch (MalformedURLException e) {
            abort = "URL error";
            log("StockServer: Exception " + e.toString() +
		" reading URL");
        } catch (IOException ie) {
            log("StockServer: Exception " + ie.toString() +
		" reading URL");
            abort = "IO error";
        } catch (Exception other) {
	    abort = "Exception reading URL";
            log("StockServer: Exception " + other.toString() +
		" reading URL");
	}

        if (StockServer_Debug) {
            log("StockServer: readURL: done.  Returning " + returnValue);
        }

	return returnValue;
    }

    synchronized void updateURL() {
        if (StockServer_Debug) {
            log("StockServer: updateURL.");
        }

        // Read in the stock information again.

	while (true) {
	    if (readURL("http://quote.yahoo.com/quotes?symbols=" + tickers + 
	        "&detailed=f&options=t") == true) {

		break;
	    }
	
	    log("StockServer: readURL failed!");

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
	}

        pos = 0;
	if (sunTicker)
	    // need as much room in the packet as possible for the ticker
	    text = " ";
	else
	    text = ">>>>>>>>>>>>>>>>> ";

        if (content != null) {
	    
	    parseReply();

            try {
                if (outputFile != null) {
                    if (StockServer_Debug) {
                        log("Writing text to output file");
                    }

                    outputFile.write(text);
                    outputFile.flush();
                }
            } catch (IOException ie) {
                ie.printStackTrace();
            }
        } else {
	    log("Didn't get anything from yahoo! " + abort);
            text = abort;
        }
    }

    synchronized void readText() {
        int i, j = 0;
        char cbuf[] = new char[50000];

        try {
            for (; j < cbuf.length; j++) {
                i = inputFile.read(cbuf, j, 1);

                if (i == -1) {
                    --j;

                    break;
                }
                if (cbuf[j] != '>') {
                    break;
                } 
            }
            for (; j < cbuf.length; j++) {
                i = inputFile.read(cbuf, j, 1);

                if (i == -1) {
                    --j;

                    break;
                }
                if (cbuf[j] == '>') {
                    break;
                } 
            }
        } catch (IOException ie) {
            ie.printStackTrace();
        }

        text = new String(cbuf, 0, j);

        if (StockServer_Debug) {
            log("Text is " + text);
        }
    }

    //
    // The reply we get from the stock server is an html file.
    // We need to parse the file looking for fields in specific places.
    // Yuck!!!
    //
    private void parseReply() {
	String line, symbol, time, value, change, quote;
	int i, j;

	String lastTicker = new String(tickerList[nTickers]);

        if (StockServer_Debug) {
            log("StockServer: parseReply.");
        }

	//
	// Quotes immediately follow the line with "Symbol      Last Trade"
	//
	// There is one quote per line.
	//
	// Quotes end with the line "</pre" or "<table"
	//
	if ((i = content.indexOf("Symbol      Last Trade")) == -1) {
	    log("Couldn't find 'Symbol      Last Trade'");
	    return;
	}

	content = content.substring(i);

	line = nextLine();		// first line of quotes

	//
	// Each qoute line is expected to look something like this:
	// 
	// <...>SUNW<...>     1:54PM     <b>75 13/16</b>     +2 7/8     
	//

        while ((line = nextLine()) != null) {

	    if (line.startsWith("</pre") || line.startsWith("<table"))
		break;			// all done

	    if (content.indexOf("<") == -1) {
		log("couldn't find start of quote!");
		break;
	    }

	    if ((i = line.indexOf(">")) == -1) {
		log("couldn't find quote symbol!");
		break;
	    }

	    line = line.substring(i+1);			   // Symbol
            j = line.indexOf("<");
            symbol = line.substring(0, j);

            if (StockServer_Debug) {
		log("UPDATE: Symbols: " + symbol);
	    }

            line = line.substring(j+1);                    // Time
            i = line.indexOf(" ");
            time = line.substring(i, i+12).trim();
	
            if (StockServer_Debug) {
		//                log("UPDATE: Last Trade time: " + time);
            }

            line = line.substring(i+12);
            i = line.indexOf(">");                         // Value
            line = line.substring(i+1);
            j = line.indexOf("<");
            value = line.substring(0, j);

            if (StockServer_Debug) {
		//                  log("UPDATE: Last Trade value: " + value);
            }

            change = " ";

            line = line.substring(j+1);                   // Change.
            i = line.indexOf("-");
            if (i == -1) {
                i = line.indexOf("+");
            }
            if (i != -1) {
                line = line.substring(i);

                for (j = 0; j < line.length(); j++) {
                    if ((line.charAt(j) == ' ' && line.charAt(j+1) == ' ') || 
			line.charAt(j) == '<') {
                            break;
                    }
                }
                change = line.substring(0, j);

                if (StockServer_Debug) {
		    //                    log("UPDATE: Change: " + change);
		}
	    }

	    text += time + " " + symbol + "..." + value + " (" + change + ")  ";

	    //
	    // Now append any news items
	    // News starts after the line "<!-- Yahoo TimeStamp"
	    //
	    String contentSave = content;

	    while ((line = nextLine()) != null) {
		if (line.startsWith("<!-- Yahoo TimeStamp") == true)
		    break;
	    }

	    //
	    // We found the start of the news.  
	    // See if there is news for the current symbol.
	    //
	    // Each news entry must look something like this:
	    //
	    // Mon Jul 12 SUNW  <a "..."> ... </a> - <i> ... </i>
	    // Fri Oct  8 SUNW ...

	    while ((line = nextLine()) != null) {
		if (line.startsWith("</pre") || line.startsWith("<table"))
		    break;

                line = line.substring(11);	// symbol

                j = line.indexOf(" ");

                if (j == -1) 
                    continue;

		if (!symbol.equals(line.substring(0, j)))
		    continue;			// not news we want

		i = line.indexOf("\">");
                j = line.indexOf("</a>");

                if (i == -1 || j == -1)
                    continue;

		if (!sunTicker) {
		    // skip news for ticker
		    text += line.substring(i+2, j);	// append news item
		    text += "  ";
		}
	    }

	    content = contentSave;
            text += " ";

            if (StockServer_Debug) {
		log(text);
	    }
        }

	if (sunTicker) {
	    // add news from Sunweb and Press releases
	    nextSUNWHeadline();
	    nextPressHeadline();
	    nextShowHeadline();
            text += " SUNW: ";
	    text += SUNWURLContentString;
            text += " Press: ";
	    text += PressURLContentString;
	    if (ShowURLContentString != null) {
		text += " Show: ";
		text += ShowURLContentString;
	    }
	}
    }

    private String nextLine() {
	int i;
	String line;

	while ((i = content.indexOf("\n")) != -1) {
	    line = content.substring(0, i);
	    content = content.substring(i + 1);

	    if (i > 0)
		return line;
	}

	return null;
    }

    private void nextSUNWHeadline() {

	// First, adjust the parameters 
	if (SUNWArticleCount >= SUNWMaxArticleCount) {
	    // reset parameters to beginning values
	    SUNWArticleDate = new Date();
	    SUNWArticleCalendar = new GregorianCalendar();
	    SUNWYear = SUNWArticleCalendar.get(Calendar.YEAR);
	    SUNWMonth = SUNWArticleCalendar.get(Calendar.MONTH) + 1;
	    SUNWDay = SUNWArticleCalendar.get(Calendar.DAY_OF_MONTH);
	    SUNWArticle = 50;
	    SUNWArticleCount = 0;
	}

	else 
	    // look for another for the same day
	    SUNWArticle++;

	// Now, look for the next article

	boolean articleFound = false;
	while (!articleFound) {
	    String monthFiller = "", dayFiller = "";

	    if (SUNWMonth < 10)
		monthFiller = new String("0");

	    if (SUNWDay < 10)
		dayFiller = new String("0");
	    
	    String headlineString = 
		new String("http://empcomm3.Corp/NEWS/usenglish/sunnews/" + 
		SUNWYear + monthFiller + SUNWMonth + 
		"/headlinesshort/sunnews." + SUNWYear + monthFiller + 
		SUNWMonth + dayFiller + SUNWDay + "." + SUNWArticle + ".html");
	    try {
		if (StockServer_Debug) 
		    log("trying " + headlineString);

		URL headlineURL = new URL(headlineString);

		
		InputStream headlineStream = headlineURL.openStream();

		byte urlContent[] = new byte[1000];
		int result = headlineStream.read(urlContent);
		
		SUNWURLContentString = new String(urlContent);

		if ((SUNWURLContentString != null) && 
		    (SUNWURLContentString.indexOf("Error 404") == -1)) {

		    articleFound = true;
		    SUNWURLContentString = 
			SUNWURLContentString.substring(
			0, SUNWURLContentString.lastIndexOf("]") + 1);
		} else
		    nextSUNWHeadlineParams();

	    } catch (Exception e) {
		System.out.println(e.toString());
		nextSUNWHeadlineParams();
	    }
	}
	SUNWArticleCount++;
    }

    private void nextSUNWHeadlineParams() {

	// look for an article from the previous day
	SUNWArticle = 50;
	SUNWArticleDate = 
	    new Date(SUNWArticleDate.getTime() - 86400000); // the day before
	SUNWArticleCalendar.setTime(SUNWArticleDate);
	SUNWDay = SUNWArticleCalendar.get(Calendar.DAY_OF_MONTH);
	SUNWYear = SUNWArticleCalendar.get(Calendar.YEAR);
	SUNWMonth = SUNWArticleCalendar.get(Calendar.MONTH) +1;
    }

	
    private void nextPressHeadline() {

	// First, adjust the parameters 
	if (PressArticleCount >= PressMaxArticleCount) {
	    // reset parameters to beginning values
	    PressArticleDate = new Date();
	    PressArticleCalendar = new GregorianCalendar();
	    PressYear = PressArticleCalendar.get(Calendar.YEAR);
	    PressMonth = PressArticleCalendar.get(Calendar.MONTH) + 1;
	    PressDay = PressArticleCalendar.get(Calendar.DAY_OF_MONTH);
	    PressArticle = 01;
	    PressArticleCount = 0;
	}

	else 
	    // look for another for the same day
	    PressArticle++;

	// Now, look for the next article

	boolean articleFound = false;
	while (!articleFound) {
	    String monthFiller = "", dayFiller = "";

	    if (PressMonth < 10)
		monthFiller = new String("0");

	    if (PressDay < 10)
		dayFiller = new String("0");
	    
	    String headlineString = 
		new String(
		"http://empcomm3.Corp/NEWS/usenglish/Press/sunflash/" + 
		PressYear + monthFiller + PressMonth + 
		"/headlinesint/sunflash." + PressYear + monthFiller + 
		PressMonth + dayFiller + PressDay + ".0" + 
		PressArticle + ".html");
	    try {
		if (StockServer_Debug) 
		    log("trying " + headlineString);

		URL headlineURL = new URL(headlineString);

		InputStream headlineStream = headlineURL.openStream();

		byte urlContent[] = new byte[1000];
		int result = headlineStream.read(urlContent);
		
		PressURLContentString = new String(urlContent);

		if ((PressURLContentString != null) && 
		    (PressURLContentString.indexOf("Error 404") == -1)) {

		    articleFound = true;
		    PressURLContentString = 
			PressURLContentString.substring(
			0, PressURLContentString.lastIndexOf("]") + 1);
		}
		else
		    nextPressHeadlineParams();
	    } catch (Exception e) {
		System.out.println(e.toString());
		nextPressHeadlineParams();
	    }
	}
	PressArticleCount++;
    }

    private void nextPressHeadlineParams() {

	// look for an article from the previous day
	PressArticle = 01;
	PressArticleDate = 
	    new Date(PressArticleDate.getTime() - 86400000); // the day before
	PressArticleCalendar.setTime(PressArticleDate);
	PressDay = PressArticleCalendar.get(Calendar.DAY_OF_MONTH);
	PressYear = PressArticleCalendar.get(Calendar.YEAR);
	PressMonth = PressArticleCalendar.get(Calendar.MONTH) +1;
    }

    private void nextShowHeadline() {

	// If there are headlines in the News file, include them

	// First, adjust the parameters 
	if (ShowArticleCount >= ShowMaxArticleCount) {
	    // reset parameters to beginning values
	    ShowArticleCount = 0;
	}

	// Now, look for the next article

	ShowURLContentString = null;

	try {
	    if (ShowArticleCount == 0) {
		int i;
		char cbuf[] = new char[4000];
		FileReader inputFile = new FileReader("News");
    
		// read in the file again
		if ((i = inputFile.read(cbuf, 0, 4000)) != -1) {
		    News = new String(cbuf, 0, i);
		}
	    }
		
	    if ((News.indexOf("Show: ") != -1) && (News.indexOf("]") != -1)) {
		ShowURLContentString = 
		    News.substring(News.indexOf("Show: ") + 6, 
		        News.indexOf("]") + 1);
		
		News = News.substring(News.indexOf("]") + 1);
	    }
	}
	catch (FileNotFoundException ie) {
	    return;
	}
	catch (IOException ie) {
	    return;
	}

	ShowArticleCount++;
    }

	
	
    private void makeTickerList() {
        String ticker = new String(tickers) + "+";

        if (StockServer_Debug) {
            log("StockServer: makeTickerList.");
        }

        nTickers = 0;

        while (ticker.length() >= 2) {
            int i = ticker.indexOf("+");

	    String t = ticker.substring(0, i);

            if (StockServer_Debug) {
                log("StockServer:  ticker " + t);
            }

            tickerList[nTickers++] = t;
            ticker = ticker.substring(t.length() + 1, ticker.length());
        }
	nTickers--;	// back down 1
    }

    private String strip(String s) {
        if (StockServer_Debug) {
            log("StockServer: strip:" + " s: " + s);
        }

        try {
            while (s.charAt(0) == ' ') {
                s = s.substring(1);
            }
            while (s.charAt(s.length() - 1) == ' ') {
                s = s.substring(0, s.length() - 1);
            }

            return (s);
        } catch (Exception e) {
            return (s);
        }
    }

    private void sendDataPacket(String message) {
        int length = message.length();
        byte data[] = new byte[length];
        DatagramPacket sendPacket;

        if (StockServer_Debug) {
            log("StockServer: sendDataPacket:" + " length: " 
                               + length + " message: " + message);
        }

        data = message.getBytes();

        try {
            sendPacket =  new DatagramPacket(data, length);

            ms.send(sendPacket);
        } catch (RMException e) {
            log(e.toString());
            e.printStackTrace();
        } catch (NoMembersException nme) {
	    /* keep sending even though there are no members. */
        } catch (IOException e) {
            log(e.toString());
            e.printStackTrace();
        }
    }

    private void waitForDelayTime() {
        if (StockServer_Debug) {
            log("StockServer: waitForDelayTime.");
        }

        try {
            Thread.sleep(delayTime * 1000);
        } catch (InterruptedException e) {
        }
    }

    private void run() {
        if (StockServer_Debug) {
            log("StockServer: run.");
        }

	log("\nChannel Name = " + channelName);
	log("tickers = " + tickers);
	log("stockServerAddress = " + stockServerAddress);
	log("dataPort = " + dataPort);
	log("ttl = " + ttl);
	log("cache size = " + cacheSize + "\n");

        waitForDelayTime();

	int n = 0;

        try {
            while (true) {
                if (inputFile == null) {
                    updateURL();
                } else {
		    readText();
                }

                sendDataPacket(text);

		Thread.sleep(10000);
            }
        } catch (Exception e) {
            log("StockServer: run: exception: " + e);

            if (StockServer_Debug) {
                e.printStackTrace();
            }
        }
        System.out.println("Stock Server exiting...");
	System.exit(0);
    }

    public static void main(String[] args) {
        StockServer stockServer = new StockServer(args);
        stockServer.run();
    }

    private void setProps() {
        Properties props = new Properties(System.getProperties());

        props = new Properties(props);

        File theUserPropertiesFile;
        String sep = File.separator;

        theUserPropertiesFile = new File(System.getProperty("user.home") 
                                         + sep + ".hotjava" + sep 
                                         + "properties");

        try {
            FileInputStream in = new FileInputStream(theUserPropertiesFile);

            props.load(new BufferedInputStream(in));
            in.close();
        } catch (Exception e) {
            System.err.println("StockServer: setProps: Error loading " +
		"properties. Have you run hotjava or appletviewer before?  " +
		"Please do so, and set the firewall proxy in the preferences.");
        }

        System.setProperties(props);
    }

}
