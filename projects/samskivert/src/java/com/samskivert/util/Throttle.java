//
// $Id: Throttle.java,v 1.1 2001/05/25 18:43:20 mdb Exp $

package com.samskivert.util;

/**
 * A throttle is used to prevent code from attempting a particular
 * operation too often. Often it is desirable to retry an operation under
 * failure conditions, but simplistic approaches to retrying operations
 * can lead to large numbers of spurious attempts to do something that
 * will obviously fail. The throttle class provides a mechanism for
 * limiting such attempts by measuring whether or not an activity has been
 * performed N times in the last M seconds. The user of the class decides
 * the appropriate throttle parameters and then simply calls through to
 * throttle to determine whether or not to go ahead with the operation.
 *
 * <p> For example:
 *
 * <pre>
 * protected Throttle _throttle = new Throttle(5, 60);
 *
 * public void performOp ()
 *     throws UnavailableException
 * {
 *     if (_throttle.throttleOp()) {
 *         throw new UnavailableException();
 *     }
 *
 *     // perform operation
 * }
 * </pre>
 */
public class Throttle
{
    /**
     * Constructs a new throttle instance that will allow the specified
     * number of operations to proceed within the specified period (the
     * period is measured in seconds).
     *
     * <p> As operations and period define a ratio, use the smallest value
     * possible for operations as an array is created to track the time at
     * which each operation was performed (e.g. use 6 ops per 10 seconds
     * rather than 60 ops per 100 seconds if possible). However, note that
     * you may not always want to reduce the ratio as much as possible if
     * you wish to allow bursts of operations up to some large value.
     */
    public Throttle (int operations, int period)
    {
        _ops = new long[operations];
        _period = 1000L * (long)period;
    }

    /**
     * Registers an attempt at an operation and returns true if the
     * operation should be performed or false if it should be throttled
     * (meaning N operations have already been performed in the last M
     * seconds).
     *
     * @return true if the throttle is activated, false if the operation
     * can proceed.
     */
    public boolean throttleOp ()
    {
        long now = System.currentTimeMillis();

        // if the oldest operation was performed less than _period ago, we
        // need to throttle
        if ((now - _ops[_lastOp]) < _period) {
            return true;
        }

        // otherwise overwrite the oldest operation with the current time
        // and move the oldest operation pointer to the second oldest
        // operation (which is now the oldest as we overwrote the oldest)
        _ops[_lastOp] = now;
        _lastOp = (_lastOp + 1) % _ops.length;
        return false;
    }

    public static void main (String[] args)
    {
        Throttle throttle = new Throttle(5, 10);

        // try doing one operation per second and we should hit the
        // throttle on the sixth operation and then kick in again on the
        // eleventh, only to stop again on the fifteenth
        for (int i = 0; i < 20; i++) {
            System.out.println((i+1) + ". Throttle: " +
                               throttle.throttleOp());
            // pause for a sec
            try { Thread.sleep(1000L); }
            catch (InterruptedException ie) {}
        }
    }

    protected long[] _ops;
    protected int _lastOp;
    protected long _period;
}
