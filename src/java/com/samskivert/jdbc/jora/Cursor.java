//-< Cursor.java >---------------------------------------------------*--------*
// JORA                       Version 2.0        (c) 1998  GARRET    *     ?  *
// (Java Object Relational Adapter)                                  *   /\|  *
//                                                                   *  /  \  *
//                          Created:     10-Jun-98    K.A. Knizhnik  * / [] \ *
//                          Last update: 20-Jun-98    K.A. Knizhnik  * GARRET *
//-------------------------------------------------------------------*--------*
// Cursor for navigation thru result of SELECT statement 
//-------------------------------------------------------------------*--------*

package com.samskivert.jdbc.jora;

import java.util.*;
import java.sql.*;

import com.samskivert.Log;

/** Cursor is used for successive access to records fetched by SELECT 
 *  statement. As far as records can be retrived from several derived tables 
 *  (polymorphic form of select), this class can issue several requests
 *  to database. Cursor also provides methods for updating/deleting current 
 *  record.
 */
public class Cursor<V>
{
    /**
     * A cursor is initially positioned before its first row; the
     * first call to next makes the first row the current row; the
     * second call makes the second row the current row, etc. 
     *
     * <P>If an input stream from the previous row is open, it is
     * implicitly closed. The ResultSet's warning chain is cleared
     * when a new row is read.
     *
     * @return object constructed from fetched record or null if there
     * are no more rows 
     */
    public V next ()
	throws SQLException
    {  
        // if we closed everything up after the last call to next(),
        // nTables will be zero here and we should bail immediately
        if (nTables == 0) {
            return null;
        }

        do { 
            if (result == null) {
                if (table.isAbstract) {
                    table = table.derived;
                    continue;
                }
                if (qbeObject != null) { 
                    PreparedStatement qbeStmt;
                    synchronized(session.preparedStmtHash) { 
                        Object s = session.preparedStmtHash.get(query); 
                        if (s == null) { 
                            qbeStmt=
                                session.connection.prepareStatement(query);
                            session.preparedStmtHash.put(query, qbeStmt);
                        } else { 
                            qbeStmt = (PreparedStatement)s;
                        }
                    }
                    synchronized(qbeStmt) { 
                        table.bindQueryVariables(
                            qbeStmt, qbeObject, qbeMask);
                        result = qbeStmt.executeQuery();
                        qbeStmt.clearParameters();
                    }
                } else { 
                    if (stmt == null) { 
                        stmt = session.connection.createStatement();
                    }
                    result = stmt.executeQuery(query);
                } 
            }
            if (result.next()) { 
                return currObject = table.load(result);
            } 
            result.close();
            result = null;
            currObject = null;
            table = table.derived;
        } while (--nTables != 0);

        if (stmt != null) { 
            stmt.close();
        }
	return null;
    }

    /**
     * Returns the first element matched by this cursor or null if no elements
     * were matched. Checks to ensure that no subsequent elements were matched
     * by the query, logs a warning if there were spurious additional matches.
     */
    public V get ()
        throws SQLException
    {
        V result = next();
        if (result != null) {
            int spurious = 0;
            while (next() != null) {
                spurious++;
            }
            if (spurious > 0) {
                Log.warning("Cursor.get() quietly tossed " + spurious +
                            " spurious additional records. " +
                            "[query=" + query + "].");
            }
        }
        return result;
    }

    /** Update current record pointed by cursor. This method can be called 
     *  only after next() method, which returns non-null object. This objects
     *  is used to update current record fields.<P>
     *
     *  If you are going to update or delete selected records, you should add
     *  "for update" clause to select statement. So parameter of
     *  <CODE>jora.Table.select()</CODE> statement should contain "for update"
     *  clause: 
     *  <CODE>record.table.Select("where name='xyz' for update");</CODE><P>
     * 
     *  <I><B>Attention!</I></B> 
     *  Not all database drivers support update operation with 
     *  cursor. This method will not work with such database drivers.
     */
    public void update ()
	throws SQLException
    {
        if (currObject == null) { 
	    throw new NoCurrentObjectError();
	}
        table.updateVariables(result, currObject);
    }

    /** Delete current record pointed by cursor. This method can be called 
     *  only after next() method, which returns non-null object.<P>
     *
     *  If you are going to update or delete selected records, you should add
     *  "for update" clause to select statement. So parameter of
     *  <CODE>jora.Table.select()</CODE> statement should contain "for update"
     *  clause: 
     *  <CODE>record.table.Select("where name='xyz' for update");</CODE><P>
     * 
     *  <I><B>Attention!</I></B> 
     *  Not all database drivers support delete operation with cursor. 
     *  This method will not work with such database drivers.
     */
    public void delete ()
	throws SQLException
    {
        if (currObject == null) { 
	    throw new NoCurrentObjectError();
	}
        result.deleteRow();
    }

    /**
     * Close the Cursor, even if we haven't read all the possible
     * objects.
     */
    public void close ()
        throws SQLException
    {
        if (stmt != null) {
            stmt.close(); // also automatically closes result
            result = null;
            stmt = null;
        }
        nTables = 0;
    }

    /** Extracts no more than <I>maxElements</I> records from database and
     *  store them into array. It is possible to extract rest records
     *  by successive next() or toArray() calls. Selected objects should
     *  have now components of InputStream, Blob or Clob type, because
     *  their data will be not available after fetching next record.
     *
     * @param maxElements limitation for result array size (and also for number
     *  of fetched records)
     * @return List with objects constructed from fetched records.
     */
    public ArrayList<V> toArrayList (int maxElements)
	throws SQLException
    {
        ArrayList<V> al = new ArrayList(Math.min(maxElements, 100));
        V o;
        while (--maxElements >= 0 && (o = next()) != null) {
            al.add(o);
        }
        return al;
    }

    /** Store all objects returned by SELECT query into a list of Object.
     *  Selected objects should have now components of InputStream, Blob or
     *  Clob type, because their data will be not available after fetching
     *  next record.
     *
     * @return Array with objects constructed from fetched records.
     */
    public ArrayList<V> toArrayList()
	throws SQLException
    {
        return toArrayList(Integer.MAX_VALUE);
    }

    // Internals 

    protected Cursor (
        Table<V> table, Session session, int nTables, String query)
    {
	if (session == null) { 
	    session = ((SessionThread)Thread.currentThread()).session;
	}	    
        this.table = table;
	this.session = session;
        this.nTables = nTables;
        this.query = query;
    }

    protected Cursor (Table<V> table, Session session, int nTables, V obj,
                      FieldMask mask, boolean like)
    { 
	if (session == null) { 
	    session = ((SessionThread)Thread.currentThread()).session;
	}	    
        this.table = table;
	this.session = session;
	this.nTables = nTables;
        this.like = like;
	qbeObject = obj;
        qbeMask = mask;
	query = table.buildQueryList(obj, mask, like);
	stmt = null;
    }
  
    private Table<V>  table; 
    private Session   session;
    private int       nTables;
    private ResultSet result;
    private String    query;
    private Statement stmt;
    private V         currObject;
    private V         qbeObject;
    private FieldMask qbeMask;
    private boolean   like;
}

