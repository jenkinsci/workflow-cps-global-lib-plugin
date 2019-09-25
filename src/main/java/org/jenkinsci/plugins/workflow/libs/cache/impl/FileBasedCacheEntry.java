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
import org.jenkinsci.plugins.workflow.libs.cache.WritableCacheEntry;

public class FileBasedCacheEntry implements WritableCacheEntry {

    private static final int LAST_MODIFIED_OF_NONEXISTENT_ENTRY = -1;

    private final FilePath rootDir;
    private final FilePath timestampMarker;
    private final FilePath dataDir;

    public FileBasedCacheEntry(FilePath rootDir) {
        this.rootDir = rootDir;
        timestampMarker = new FilePath(rootDir, "timestamp.txt");
        dataDir = new FilePath(rootDir, "data");
    }

    @Override public boolean isUpToDate(long ttl) throws IOException, InterruptedException {
        long lastModified = getLastModified();
        if (lastModified == LAST_MODIFIED_OF_NONEXISTENT_ENTRY) {
            return false;
        }
        if (ttl < 0) {
            return true;
        }
        return System.currentTimeMillis() - lastModified < ttl;
    }

    @Override public long getLastModified() throws IOException, InterruptedException {
        if (timestampMarker.exists()) {
            return timestampMarker.lastModified();
        }
        return LAST_MODIFIED_OF_NONEXISTENT_ENTRY;
    }

    @Override public void copyTo(FilePath dest) throws IOException, InterruptedException {
        copy(dataDir, dest);
    }

    @Override public void copyFrom(FilePath src) throws IOException, InterruptedException {
        copy(src, dataDir);
        timestampMarker.touch(System.currentTimeMillis());
    }

    protected void copy(FilePath src, FilePath dest) throws IOException, InterruptedException {
        if (!src.exists()) {
            throw new IOException(String.format("Cannot copy %s to %s because the source directory does not exist", src.getRemote(), dest.getRemote()));
        }
        if (dest.exists()) {
            dest.deleteRecursive();
        }
        src.copyRecursiveTo(dest);
    }

    @Override public void delete() throws IOException, InterruptedException {
        dataDir.deleteRecursive();
        // delete metadata after deletion of data (which could be heavy)
        rootDir.deleteRecursive();
    }
}
