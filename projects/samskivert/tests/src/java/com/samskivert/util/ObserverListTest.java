//
// $Id: ObserverListTest.java,v 1.1 2002/05/16 22:00:07 mdb Exp $

package com.samskivert.util;

import junit.framework.Test;
import junit.framework.TestCase;

import com.samskivert.Log;

/**
 * Tests the {@link ObserverList} class.
 */
public class ObserverListTest extends TestCase
{
    public ObserverListTest ()
    {
        super(ObserverListTest.class.getName());
    }

    public void runTest ()
    {
//         Log.info("Testing safe list.");
        testList(new ObserverList(ObserverList.SAFE_IN_ORDER_NOTIFY));

//         Log.info("Testing unsafe list.");
        testList(new ObserverList(ObserverList.FAST_UNSAFE_NOTIFY));
    }

    public void testList (final ObserverList list)
    {
        final int[] notifies = new int[1];

        for (int i = 0; i < 1000; i++) {
            // add some test observers
            list.add(new TestObserver(1));
            list.add(new TestObserver(2));
            list.add(new TestObserver(3));
            list.add(new TestObserver(4));

            int ocount = list.size();
            notifies[0] = 0;

            list.apply(new ObserverList.ObserverOp() {
                public boolean apply (Object obs) {
                    notifies[0]++;
                    ((TestObserver)obs).foozle();

                    // 1/3 of the time, remove the observer; 1/3 of the
                    // time append a new observer; 1/3 of the time do
                    // nothing
                    double rando = Math.random();
                    if (rando < 0.33) {
                        return false;

                    } else if (rando > 0.66) {
                        list.add(new TestObserver(5));
                    }
                    return true;
                }
            });

//             Log.info("had " + ocount + "; notified " + notifies[0] +
//                      "; size " + list.size() + ".");

            assertTrue("had " + ocount + "; notified " + notifies[0],
                       ocount == notifies[0]);
            list.clear();
        }
    }

    public static Test suite ()
    {
        return new ObserverListTest();
    }

    public static void main (String[] args)
    {
        ObserverListTest test = new ObserverListTest();
        test.runTest();
    }

    protected static class TestObserver
    {
        public TestObserver (int index)
        {
            _index = index;
        }

        public void foozle ()
        {
//             Log.info("foozle! " + _index);
        }

        public String toString ()
        {
            return Integer.toString(_index);
        }
        protected int _index;
    }
}
