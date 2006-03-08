package com.sun.gi.utils;

import javax.swing.JFrame;
import java.util.List;
import java.util.ArrayList;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import javax.swing.text.Document;
import javax.swing.text.*;
import java.util.Iterator;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class CommandLineWindow extends JFrame implements KeyListener {
    List listeners = new ArrayList();
    JTextArea tarea;
    int lineStart=0;
    public CommandLineWindow() {
        this("Command Line");
    }

    public CommandLineWindow(String string) {
        super("Command Line: "+string);
        tarea = new JTextArea();
        getContentPane().setLayout(new BorderLayout());
        tarea.setEditable(false);
        tarea.addKeyListener(this);
        getContentPane().add(tarea,BorderLayout.CENTER);
        setSize(200,200);
    }

    public void addListener(CommandLineWindowListener listener) {
        listeners.add(listener);
    }

    public void keyTyped(KeyEvent keyEvent) {
        Document d = tarea.getDocument();
        String instr = String.valueOf(keyEvent.getKeyChar());
        int endPos = d.getEndPosition().getOffset();
        try {
            d.insertString(endPos-1, instr, null);
        }
        catch (BadLocationException ex) {
            ex.printStackTrace();
        }
        if (keyEvent.getKeyChar() == '\n'){
            try {
                fireLineInput(d.getText(lineStart, (endPos-1)-lineStart));
            }
            catch (BadLocationException ex1) {
                ex1.printStackTrace();
            }
            lineStart = endPos;
        }
    }

    public void keyPressed(KeyEvent keyEvent) {
    }

    private void fireLineInput(String string) {
        for(Iterator i = listeners.iterator();i.hasNext();){
            ((CommandLineWindowListener)i.next()).lineInput(string);
        }
    }

    public void keyReleased(KeyEvent keyEvent) {
    }

    static public void main(String[] args){
        CommandLineWindow clw = new CommandLineWindow("Echo test");
        clw.addListener(new CommandLineWindowListener() {
            public void lineInput(String il){
                System.out.println(il);
            }
        });
        clw.setVisible(true);
    }
}