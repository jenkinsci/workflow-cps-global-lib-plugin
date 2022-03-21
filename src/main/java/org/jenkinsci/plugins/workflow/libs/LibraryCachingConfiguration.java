package org.jenkinsci.plugins.workflow.libs;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang.StringUtils;

public final class LibraryCachingConfiguration extends AbstractDescribableImpl<LibraryCachingConfiguration> {
    private int refreshTimeMinutes;
    private String excludedVersionsStr;

    private static final String VERSIONS_SEPARATOR = " ";
    public static final String GLOBAL_LIBRARIES_DIR = "global-libraries-cache";
    public static final String LAST_READ_FILE = "last_read";
    public static final String RETRIEVE_LOCK_FILE = "retrieve.lock";

    @DataBoundConstructor public LibraryCachingConfiguration(int refreshTimeMinutes, String excludedVersionsStr) {
        this.refreshTimeMinutes = refreshTimeMinutes;
        this.excludedVersionsStr = excludedVersionsStr;
    }

    public int getRefreshTimeMinutes() {
        return refreshTimeMinutes;
    }

    public long getRefreshTimeMilliseconds() {
        return Long.valueOf(getRefreshTimeMinutes()) * 60000;
    }

    public Boolean isRefreshEnabled() {
        return refreshTimeMinutes > 0;
    }

    public String getExcludedVersionsStr() {
        return excludedVersionsStr;
    }

    private List<String> getExcludedVersions() {
        if (excludedVersionsStr == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(excludedVersionsStr.split(VERSIONS_SEPARATOR));
    }

    public Boolean isExcluded(String version) {
        // exit early if the version passed in is null or empty
        if (StringUtils.isBlank(version)) {
            return false;
        }
        for (String it : getExcludedVersions()) {
            // confirm that the excluded versions aren't null or empty
            // and if the version contains the exclusion thus it can be
            // anywhere in the string.
            if (StringUtils.contains(it, version)) {
                return true;
            }
        }
        return false;
    }

    @Override public String toString() {
        return "LibraryCachingConfiguration{refreshTimeMinutes=" + refreshTimeMinutes + ", excludedVersions="
                + excludedVersionsStr + '}';
    }

    public static FilePath getGlobalLibrariesCacheDir() {
        Jenkins jenkins = Jenkins.get();
        return new FilePath(jenkins.getRootPath(), LibraryCachingConfiguration.GLOBAL_LIBRARIES_DIR);
    }

    @Extension public static class DescriptorImpl extends Descriptor<LibraryCachingConfiguration> {
        public FormValidation doClearCache(@QueryParameter String name) throws InterruptedException {
            Jenkins.get().checkPermission(Jenkins.ADMINISTER);

            FilePath cacheDir = new FilePath(LibraryCachingConfiguration.getGlobalLibrariesCacheDir(), name);
            try {
                if (cacheDir.exists()) {
                    cacheDir.deleteRecursive();
                }
            } catch (IOException ex) {
                return FormValidation.error(ex, "The cache dir was not deleted successfully");
            }
            return FormValidation.ok("The cache dir was deleted successfully.");
        }

    }
}