//
// $Id: Invoker.java,v 1.1 2004/06/29 03:14:22 mdb Exp $

package com.samskivert.util;

import java.util.HashMap;
import java.util.Iterator;

import com.samskivert.Log;

/**
 * The invoker is used to invoke self-contained units of code on an
 * invoking thread. Each invoker is associated with its own thread and
 * that thread is used to invoke all of the units posted to that invoker
 * in the order in which they were posted. The invoker also provides a
 * convenient mechanism for processing the result of an invocation back on
 * the main thread.
 *
 * <p> The invoker is a useful tool for services that need to block and
 * therefore cannot be run on the main thread. For example, an interactive
 * application might provide an invoker on which to run database queries.
 *
 * <p> Bear in mind that each invoker instance runs units on its own
 * thread and care must be taken to ensure that code running on separate
 * invokers properly synchronizes access to shared information. Where
 * possible, complete isolation of the services provided by a particular
 * invoker is desirable.
 */
public class Invoker extends LoopingThread
{
    /** An interface by which the invoker communicates back to the entity
     * that manages the main thread. */
    public static interface ResultReceiver
    {
        /** Posts an unit that should be executed on the main thread. */
        public void postUnit (Runnable unit);
    }

    /**
     * The unit encapsulates a unit of executable code that will be run on
     * the invoker thread. It also provides facilities for additional code
     * to be run on the main thread once the primary code has completed on
     * the invoker thread.
     */
    public static abstract class Unit implements Runnable
    {
        /** The default constructor. */
        public Unit () {}

        /** Creates an invoker unit which will report the supplied name in
         * {@link #toString}. */
        public Unit (String name)
        {
            _name = name;
        }

        /**
         * This method is called on the invoker thread and should be used
         * to perform the primary function of the unit. It can return true
         * to cause the <code>handleResult</code> method to be
         * subsequently invoked on the dobjmgr thread (generally to allow
         * the results of the invocation to be acted upon back in the
         * context of the regular world) or false to indicate that no
         * further processing should be performed.
         *
         * @return true if the <code>handleResult</code> method should be
         * invoked on the main thread, false if not.
         */
        public abstract boolean invoke ();

        /**
         * Invocation unit implementations can implement this function to
         * perform any post-unit-invocation processing back on the main
         * thread. It will be invoked if <code>invoke</code> returns true.
         */
        public void handleResult ()
        {
            // do nothing by default
        }

        // we want to be a runnable to make the receiver interface simple,
        // but we'd like for invocation unit implementations to be able to
        // put their result handling code into an aptly named method
        public void run ()
        {
            handleResult();
        }

        /** Returns the name of this invoker. */
        public String toString ()
        {
            return _name;
        }

        protected String _name = "Unknown";
    }

    /**
     * Creates an invoker that will post results to the supplied result
     * receiver.
     */
    public Invoker (String name, ResultReceiver receiver)
    {
        super(name);
        _receiver = receiver;
    }

    /**
     * Posts a unit to this invoker for subsequent invocation on the
     * invoker's thread.
     */
    public void postUnit (Unit unit)
    {
        // simply append it to the queue
        _queue.append(unit);
    }

    // documentation inherited
    public void iterate ()
    {
        // pop the next item off of the queue
        Unit unit = (Unit) _queue.get();

        long start;
        if (PERF_TRACK) {
            start = System.currentTimeMillis();
            _unitsRun++;
        }

        try {
            if (unit.invoke()) {
                // if it returned true, we post it to the receiver thread
                // to invoke the result processing
                _receiver.postUnit(unit);
            }

            // track some performance metrics
            if (PERF_TRACK) {
                long duration = System.currentTimeMillis() - start;
                Object key = unit.getClass();
                Histogram histo = (Histogram)_tracker.get(key);
                if (histo == null) {
                    // track in buckets of 50ms up to 500ms
                    _tracker.put(key, histo = new Histogram(0, 50, 10));
                }
                histo.addValue((int)duration);

                // report long runners
                if (duration > 500L) {
                    Log.warning("Invoker unit ran for '" + duration + "ms: " +
                                unit + "' (" + key + ").");
                }
            }

        } catch (Exception e) {
            Log.warning("Invocation unit failed [unit=" + unit + "].");
            Log.logStackTrace(e);
        }
    }

    /**
     * Shuts down the invoker thread by queueing up a unit that will cause
     * the thread to exit after all currently queued units are processed.
     */
    public void shutdown ()
    {
        _queue.append(new Unit() {
            public boolean invoke () {
                _running = false;
                return false;
            }
        });
    }

    /** The invoker's queue of units to be executed. */
    protected Queue _queue = new Queue();

    /** The result receiver with which we're working. */
    protected ResultReceiver _receiver;

    /** Tracks the counts of invocations by unit's class. */
    protected HashMap _tracker = new HashMap();

    /** The total number of invoker units run since the last report. */
    protected int _unitsRun;

    /** Whether or not to track invoker unit performance. */
    protected static final boolean PERF_TRACK = true;
}
