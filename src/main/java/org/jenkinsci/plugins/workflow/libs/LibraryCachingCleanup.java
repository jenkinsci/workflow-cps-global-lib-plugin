package org.jenkinsci.plugins.workflow.libs;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import jenkins.util.SystemProperties;

@Extension public class LibraryCachingCleanup extends AsyncPeriodicWork {
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "non-final for script console access")
    public static int EXPIRE_AFTER_READ_DAYS =
            SystemProperties.getInteger(LibraryCachingCleanup.class.getName() + ".EXPIRE_AFTER_READ_DAYS", 7);

    public LibraryCachingCleanup() {
        super("LibraryCachingCleanup");
    }

    @Override public long getRecurrencePeriod() {
        return TimeUnit.HOURS.toMillis(12);
    }

    @Override protected void execute(TaskListener listener) throws IOException, InterruptedException {
        FilePath globalCacheDir = LibraryCachingConfiguration.getGlobalLibrariesCacheDir();
        for (FilePath library : globalCacheDir.list()) {
            if (!removeIfExpiredCacheDirectory(library)) {
                // Prior to the SECURITY-2586 fix, library caches had a two-level directory structure.
                // These caches will never be used again, so we delete any that we find.
                for (FilePath version: library.list()) {
                    if (version.child(LibraryCachingConfiguration.LAST_READ_FILE).exists()) {
                        library.deleteRecursive();
                        break;
                    }
                }
            }
        }
    }

    /**
     * Delete the specified cache directory if it is outdated.
     * @return true if specified directory is a cache directory, regardless of whether it was outdated. Used to detect
     * whether the cache was created before or after the fix for SECURITY-2586.
     */
    private boolean removeIfExpiredCacheDirectory(FilePath library) throws IOException, InterruptedException {
        final FilePath lastReadFile = new FilePath(library, LibraryCachingConfiguration.LAST_READ_FILE);
        if (lastReadFile.exists()) {
            if (System.currentTimeMillis() - lastReadFile.lastModified() > TimeUnit.DAYS.toMillis(EXPIRE_AFTER_READ_DAYS)) {
                library.deleteRecursive();
                library.withSuffix("-name.txt").delete();
            }
            return true;
        }
        return false;
    }
}
