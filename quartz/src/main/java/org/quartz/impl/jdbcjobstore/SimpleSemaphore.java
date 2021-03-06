/* 
 * Copyright 2001-2009 Terracotta, Inc. 
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not 
 * use this file except in compliance with the License. You may obtain a copy 
 * of the License at 
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0 
 *   
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations 
 * under the License.
 * 
 */

package org.quartz.impl.jdbcjobstore;

import java.sql.Connection;
import java.util.HashSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Internal in-memory lock handler for providing thread/resource locking in 
 * order to protect resources from being altered by multiple threads at the 
 * same time.
 * 
 * @author jhouse
 */
public class SimpleSemaphore implements Semaphore {

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Data members.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    ThreadLocal lockOwners = new ThreadLocal();

    HashSet locks = new HashSet();

    private final Log log = LogFactory.getLog(getClass());

    /*
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     * 
     * Interface.
     * 
     * ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
     */

    protected Log getLog() {
        return log;
    }

    private HashSet getThreadLocks() {
        HashSet threadLocks = (HashSet) lockOwners.get();
        if (threadLocks == null) {
            threadLocks = new HashSet();
            lockOwners.set(threadLocks);
        }
        return threadLocks;
    }

    /**
     * Grants a lock on the identified resource to the calling thread (blocking
     * until it is available).
     * 
     * @return true if the lock was obtained.
     */
    public synchronized boolean obtainLock(Connection conn, String lockName) {

        lockName = lockName.intern();

        Log log = getLog();

        if(log.isDebugEnabled()) {
            log.debug(
                "Lock '" + lockName + "' is desired by: "
                        + Thread.currentThread().getName());
        }

        if (!isLockOwner(conn, lockName)) {
            if(log.isDebugEnabled()) {
                log.debug(
                    "Lock '" + lockName + "' is being obtained: "
                            + Thread.currentThread().getName());
            }
            while (locks.contains(lockName)) {
                try {
                    this.wait();
                } catch (InterruptedException ie) {
                    if(log.isDebugEnabled()) {
                        log.debug(
                            "Lock '" + lockName + "' was not obtained by: "
                                    + Thread.currentThread().getName());
                    }
                }
            }

            if(log.isDebugEnabled()) {
                log.debug(
                    "Lock '" + lockName + "' given to: "
                            + Thread.currentThread().getName());
            }
            getThreadLocks().add(lockName);
            locks.add(lockName);
        } else if(log.isDebugEnabled()) {
            log.debug(
                "Lock '" + lockName + "' already owned by: "
                        + Thread.currentThread().getName()
                        + " -- but not owner!",
                new Exception("stack-trace of wrongful returner"));
        }

        return true;
    }

    /**
     * Release the lock on the identified resource if it is held by the calling
     * thread.
     */
    public synchronized void releaseLock(Connection conn, String lockName) {

        lockName = lockName.intern();

        if (isLockOwner(conn, lockName)) {
            if(getLog().isDebugEnabled()) {
                getLog().debug(
                    "Lock '" + lockName + "' retuned by: "
                            + Thread.currentThread().getName());
            }
            getThreadLocks().remove(lockName);
            locks.remove(lockName);
            this.notifyAll();
        } else if (getLog().isDebugEnabled()) {
            getLog().debug(
                "Lock '" + lockName + "' attempt to retun by: "
                        + Thread.currentThread().getName()
                        + " -- but not owner!",
                new Exception("stack-trace of wrongful returner"));
        }
    }

    /**
     * Determine whether the calling thread owns a lock on the identified
     * resource.
     */
    public synchronized boolean isLockOwner(Connection conn, String lockName) {

        lockName = lockName.intern();

        return getThreadLocks().contains(lockName);
    }

    /**
     * This Semaphore implementation does not use the database.
     */
    public boolean requiresConnection() {
        return false;
    }
}
