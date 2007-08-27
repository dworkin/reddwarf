/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.germwar.client.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JLabel;

/**
 * A GUI widget that displays a digital timer that counts down from set values
 * and fires an ActionEvent when it reaches 0.
 */
public class ClockTimer extends JLabel {
    /** Command string of the ActionEvents fired when the timer expires. */
    public static final String TIMER_EXPIRED_ACTION = "timer";

    /** Controls the countdown. */
    private Timer timer = new Timer();

    /** The current turn countdown task. */
    private TimerTask timerTask = null;

    /** Listeners registered for expired timer events. */
    private List<ActionListener> actionListeners = new LinkedList<ActionListener>();

    // Constructor

    /**
     * Creates a new {@code ClockTimer}.
     */
    public ClockTimer() {
        super("00:00.0");
        setFont(new Font("Webdings", Font.BOLD, 24));
    }

    /**
     * Stops the timer (if running) and resets the displayed time to "00:00.0".
     */
    public void clear() {
        stop();
        setText("00:00.0");
        setForeground(Color.BLACK);
    }

    /**
     * Stops the timer (if running) and starts it to count down from {@code
     * time} seconds.  When the timer expires, an {@link ActionEvent} will be
     * sent to any registered listeners.
     */
    public void start(int time) {
        stop();
        timerTask = new ClockTimerTask(time);
        timer.scheduleAtFixedRate(timerTask, 0, 100);
    }

    /**
     * Stops (pauses) the timer (if running).
     */
    public void stop() {
        if (timerTask != null) timerTask.cancel();
    }

    /**
     * Adds an {@code ActionListener}.
     */
    public void addActionListener(ActionListener l) {
        actionListeners.add(l);
    }

    /**
     * Removes an {@code ActionListener}.
     */
    public void removeActionListener(ActionListener l) {
        actionListeners.remove(l);
    }

    /** Inner class: ClockTimerTask */
    private final class ClockTimerTask extends TimerTask {
        /** When this task was created, in milliseconds. */
        private final long created = System.currentTimeMillis();

        /** When this task should count down from, in seconds. */
        private final int countdown;

        /**
         * Creates a new {@code ClockTimerTask} to count down from {@code
         * countdown} seconds.
         */
        public ClockTimerTask(int countdown) {
            this.countdown = countdown;
        }

        /*
         * {@inheritDoc}
         */
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            long elapsed = now - created;
            long dsecLeft = 0, secLeft = 0, minLeft = 0;

            if (elapsed >= countdown) {
                ActionEvent evt = new ActionEvent(ClockTimer.this,
                    ActionEvent.ACTION_PERFORMED, TIMER_EXPIRED_ACTION);
                
                for (ActionListener l : actionListeners)
                    l.actionPerformed(evt);
                
                this.cancel();
            } else {
                dsecLeft = (countdown - elapsed + 99)/100;  /** round up */
            }

            /** Show red when under 3 seconds left. */
            Color color = dsecLeft > 30 ? Color.BLACK : Color.RED;

            secLeft = dsecLeft / 10;
            dsecLeft -= secLeft*10;

            minLeft = secLeft / 60;
            secLeft -= minLeft*60;

            setText(String.format("%d:0%d.%d", minLeft, secLeft, dsecLeft));
            setForeground(color);
        }
    }
}
