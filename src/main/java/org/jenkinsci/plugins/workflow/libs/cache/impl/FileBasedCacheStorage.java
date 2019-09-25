/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.libs.cache.impl;

import hudson.FilePath;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.libs.cache.CacheStorage;
import org.jenkinsci.plugins.workflow.libs.cache.CacheStorageReadAction;
import org.jenkinsci.plugins.workflow.libs.cache.CacheStorageResult;
import org.jenkinsci.plugins.workflow.libs.cache.CacheStorageWriteAction;

public class FileBasedCacheStorage implements CacheStorage {

    public static final String CACHE_DIR = "global-libraries-cache";

    private static final String WRITE_LOCK_FILE_NAME = "write.lock";
    private static final String READ_LOCKS_DIR_NAME = "readLocks";

    private final FilePath cacheDir;

    private final int waitForReleaseReadLocksTries;
    private final long pausesBetweenWaitingForReleaseReadLocks;
    private final int waitForReadLockTries;
    private final long pausesBetweenWaitingForReadLocks;

    public FileBasedCacheStorage() {
        cacheDir = new FilePath(Jenkins.get().getRootPath(), CACHE_DIR);
        waitForReleaseReadLocksTries = Integer.getInteger("jenkins.workflow-libs.fileBasedCacheStorage.waitForReleaseReadLocks", 18);
        pausesBetweenWaitingForReleaseReadLocks = Long.getLong("jenkins.workflow-libs.fileBasedCacheStorage.pausesBetweenWaitingForReleaseReadLocks", TimeUnit.SECONDS.toMillis(10));
        waitForReadLockTries = Integer.getInteger("jenkins.workflow-libs.fileBasedCacheStorage.waitForReadLockTries", 36);
        pausesBetweenWaitingForReadLocks = Long.getLong("jenkins.workflow-libs.fileBasedCacheStorage.pausesBetweenWaitingForReadLocks", TimeUnit.SECONDS.toMillis(5));
    }

    @Override public <T> CacheStorageResult<T> tryWrite(String key, CacheStorageWriteAction<T> action) throws Exception {
        final FileBasedLock lock = tryWriteLock(key);
        if (lock != null) {
            try {
                return CacheStorageResult.executed(action.execute(getEntry(key)));
            } finally {
                lock.release();
            }
        }
        return CacheStorageResult.notExecuted();
    }

    protected FileBasedLock tryWriteLock(String key) throws IOException, InterruptedException {
        FilePath writeLock = createFilePath(key, WRITE_LOCK_FILE_NAME);
        if (writeLock.exists()) {
            return null;
        }
        writeLock.getParent().mkdirs();
        writeLock.touch(System.currentTimeMillis());
        FileBasedLock lock = new FileBasedLock(writeLock);
        try {
            if (waitForReleaseReadLocks(key)) {
                return lock;
            }
        } catch (Exception e) {
            // do nothing
        }
        lock.release();
        return null;
    }

    protected boolean waitForReleaseReadLocks(String key) throws IOException, InterruptedException {
        FilePath readLocks = createFilePath(key, READ_LOCKS_DIR_NAME);
        for (int i = 0; i <= waitForReleaseReadLocksTries; ++i) {
            if (readLocks.list().isEmpty()) {
                return true;
            }
            Thread.sleep(pausesBetweenWaitingForReleaseReadLocks);
        }
        return false;
    }

    @Override public <T> CacheStorageResult<T> tryRead(String key, CacheStorageReadAction<T> action) throws Exception {
        final FileBasedLock lock = tryReadLock(key);
        if (lock != null) {
            try {
                return CacheStorageResult.executed(action.execute(getEntry(key)));
            } finally {
                lock.release();
            }
        }
        return CacheStorageResult.notExecuted();
    }

    protected FileBasedLock tryReadLock(String key) throws IOException, InterruptedException {
        FilePath readLocksDir = createFilePath(key, READ_LOCKS_DIR_NAME);
        FilePath readLock = createFilePath(key, READ_LOCKS_DIR_NAME, UUID.randomUUID().toString());
        FilePath writeLock = createFilePath(key, WRITE_LOCK_FILE_NAME);
        for (int i = 0; i < waitForReadLockTries; ++i) {
            if (!writeLock.exists()) {
                readLocksDir.mkdirs();
                readLock.touch(System.currentTimeMillis());
                return new FileBasedLock(readLock);
            }
            Thread.sleep(pausesBetweenWaitingForReadLocks);
        }
        return null;
    }

    @Override public Collection<String> getKeys() throws IOException, InterruptedException {
        Collection<String> keys = new LinkedList<>();
        for (FilePath directory : cacheDir.listDirectories()) {
            keys.add(directory.getName());
        }
        return keys;
    }

    @Override public void forceDelete(String key) throws IOException, InterruptedException {
        getEntry(key).delete();
    }

    protected FileBasedCacheEntry getEntry(String key) throws IOException, InterruptedException {
        FilePath entryDir = createFilePath(key);
        return new FileBasedCacheEntry(entryDir);
    }

    protected FilePath createFilePath(String... paths) {
        return new FilePath(cacheDir, String.join("/", paths));
    }
}
