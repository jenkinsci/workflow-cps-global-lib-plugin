package org.jenkinsci.plugins.workflow.libs;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;

@Extension public class LibraryCachingCleanup extends AsyncPeriodicWork {
    private final Long recurrencePeriod;
    private final Long unreadCacheClearTime;

    public LibraryCachingCleanup() {
        super("LibraryCachingCleanup");
        recurrencePeriod = Long.getLong("jenkins.workflow-libs.cacheCleanupPeriodDays", TimeUnit.DAYS.toMillis(7));
        unreadCacheClearTime = Long.getLong("jenkins.workflow-libs.unreadCacheClearDays", TimeUnit.DAYS.toMillis(7));
    }

    @Override public long getRecurrencePeriod() {
        return recurrencePeriod;
    }

    @Override protected void execute(TaskListener listener) throws IOException, InterruptedException {
        FilePath globalCacheDir = LibraryCachingConfiguration.getGlobalLibrariesCacheDir();
        for (FilePath library: globalCacheDir.list()) {
            for (FilePath version: library.list()) {
                final FilePath lastReadFile = new FilePath(version, LibraryCachingConfiguration.LAST_READ_FILE);
                ReentrantReadWriteLock retrieveLock = LibraryAdder.cacheRetrieveLock.get(version.getName());
                if (retrieveLock.writeLock().tryLock()) {
                    try {
                      if (lastReadFile.exists() && (lastReadFile.lastModified() + unreadCacheClearTime) < System.currentTimeMillis()) {
                          version.deleteRecursive();
                      }
                    } finally {
                        retrieveLock.writeLock().unlock();
                    }
                }
            }
        }
    }
}
