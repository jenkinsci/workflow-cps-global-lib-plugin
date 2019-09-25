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

package org.jenkinsci.plugins.workflow.libs.cache;

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension public class CacheStorageCleanup extends AsyncPeriodicWork {

    private static final Logger LOGGER = Logger.getLogger(CacheStorageCleanup.class.getName());

    private final Long recurrencePeriod;
    private final Long ttl;

    public CacheStorageCleanup() {
        super(CacheStorageCleanup.class.getSimpleName());
        recurrencePeriod = Long.getLong("jenkins.workflow-libs.cacheCleanup.recurrencePeriod", TimeUnit.HOURS.toMillis(1));
        ttl = Long.getLong("jenkins.workflow-libs.cacheCleanup.ttl", TimeUnit.DAYS.toMillis(7));
    }

    @Override public long getRecurrencePeriod() {
        return recurrencePeriod;
    }

    @Override protected void execute(TaskListener listener) {
        CacheStorage storage = CacheStorage.get();
        Iterable<String> keys;
        try {
            keys = storage.getKeys();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Couldn't access cache storage! Please empty the cache manually from the server.", e);
            return;
        }
        for (String key : keys) {
            try {
                // if action is not executed then it means that somebody has the write lock, and the cache entry should be up-to-date (don't delete it)
                storage.tryWrite(key, entry -> {
                    if (!entry.isUpToDate(ttl)) {
                        LOGGER.log(Level.INFO, String.format("\"%s\" cache entry has exceeded TTL, deleting...", key));
                        entry.delete();
                        LOGGER.log(Level.INFO, String.format("Deleted \"%s\" cache entry", key));
                    }
                    return null;
                });
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, String.format("Couldn't process \"%s\" cache entry", key), e);
            }
        }
    }
}
