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

package org.jenkinsci.plugins.workflow.libs;

import hudson.AbortException;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Item;
import hudson.util.FormValidation;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;

import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;


/**
 * User configuration for one library.
 */
public class LibraryConfiguration extends AbstractDescribableImpl<LibraryConfiguration> {
    public static final String GLOBAL_LIBRARIES_DIR = "global-libraries-cache";

    private final String name;
    private final LibraryRetriever retriever;
    private String defaultVersion;
    private boolean implicit;
    private boolean allowVersionOverride = true;
    private boolean includeInChangesets = true;
    
    private boolean cacheEnabled = false;
    private int cacheRefreshTimeMinutes = 0;
    private List<String> cacheExcludedVersions = new ArrayList<>();

    @DataBoundConstructor public LibraryConfiguration(String name, LibraryRetriever retriever) {
        this.name = name;
        this.retriever = retriever;
    }

    /**
     * Library name.
     * Should match {@link Library#value}, up to the first occurrence of {@code @}, if any.
     */
    public String getName() {
        return name;
    }

    public LibraryRetriever getRetriever() {
        return retriever;
    }

    /**
     * The default version to use with {@link LibraryRetriever#retrieve} if none other is specified.
     */
    public String getDefaultVersion() {
        return defaultVersion;
    }

    @DataBoundSetter public void setDefaultVersion(String defaultVersion) {
        this.defaultVersion = Util.fixEmpty(defaultVersion);
    }

    /**
     * Whether the library should be made accessible to qualifying jobs
     * without any explicit {@link Library} declaration.
     */
    public boolean isImplicit() {
        return implicit;
    }

    @DataBoundSetter public void setImplicit(boolean implicit) {
        this.implicit = implicit;
    }

    /**
     * Whether jobs should be permitted to override {@link #getDefaultVersion}.
     */
    public boolean isAllowVersionOverride() {
        return allowVersionOverride;
    }

    @DataBoundSetter public void setAllowVersionOverride(boolean allowVersionOverride) {
        this.allowVersionOverride = allowVersionOverride;
    }

    /**
     * Whether to include library changes in reported changes in a job {@link #getIncludeInChangesets}.
     */
    public boolean getIncludeInChangesets() {
        return includeInChangesets;
    }

    @DataBoundSetter public void setIncludeInChangesets(boolean includeInChangesets) {
        this.includeInChangesets = includeInChangesets;
    }

    public boolean getCacheEnabled() {
        return cacheEnabled;
    }

    @DataBoundSetter public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }

    public int getCacheRefreshTimeMinutes() {
        return cacheRefreshTimeMinutes;
    }

    @DataBoundSetter public void setCacheRefreshTimeMinutes(int cacheRefreshTimeMinutes) {
        this.cacheRefreshTimeMinutes = cacheRefreshTimeMinutes;
    }

    public String getCacheExcludedVersionsStr() {
        if (cacheExcludedVersions == null || cacheExcludedVersions.isEmpty()) {
            return "";
        } else {
            return String.join(" ", cacheExcludedVersions);
        }
    }

    @DataBoundSetter public void setCacheExcludedVersionsStr(String cacheExcludedVersionsStr) {
        if (cacheExcludedVersionsStr.isEmpty()) {
            this.cacheExcludedVersions = new ArrayList<String>();
        } else {
            this.cacheExcludedVersions = Arrays.asList(cacheExcludedVersionsStr.split(" "));
        }
    }

    public LibraryCachingConfiguration getCachingConfiguration() {
        return new LibraryCachingConfiguration(cacheEnabled, cacheRefreshTimeMinutes, cacheExcludedVersions);
    }

    @Nonnull boolean defaultedChangelogs(@CheckForNull Boolean changelog) throws AbortException {
      if (changelog == null) {
        return includeInChangesets;
      } else {
        return changelog;
      }
    }

    @Nonnull String defaultedVersion(@CheckForNull String version) throws AbortException {
        if (version == null) {
            if (defaultVersion == null) {
                throw new AbortException("No version specified for library " + name);
            } else {
                return defaultVersion;
            }
        } else if (allowVersionOverride) {
            return version;
        } else {
            throw new AbortException("Version override not permitted for library " + name);
        }
    }

    public static File getGlobalLibrariesCacheDir() {
        Jenkins jenkins = Jenkins.getInstance();
        final File jenkinsHome = jenkins.getRootDir();
        return new File(jenkinsHome, LibraryConfiguration.GLOBAL_LIBRARIES_DIR);
    }


    @Extension public static class DescriptorImpl extends Descriptor<LibraryConfiguration> {

        // TODO JENKINS-20020 ought to be unnecessary
        @Restricted(NoExternalUse.class) // Jelly
        public Collection<LibraryRetrieverDescriptor> getRetrieverDescriptors() {
            StaplerRequest req = Stapler.getCurrentRequest();
            Item it = req != null ? req.findAncestorObject(Item.class) : null;
            return DescriptorVisibilityFilter.apply(it != null ? it : Jenkins.getActiveInstance(), ExtensionList.lookup(LibraryRetrieverDescriptor.class));
        }

        public FormValidation doCheckName(@QueryParameter String name) {
            if (name.isEmpty()) {
                return FormValidation.error("You must enter a name.");
            }
            // Currently no character restrictions.
            return FormValidation.ok();
        }

        public FormValidation doCheckDefaultVersion(@QueryParameter String defaultVersion, @QueryParameter boolean implicit, @QueryParameter boolean allowVersionOverride, @QueryParameter String name) {
            if (defaultVersion.isEmpty()) {
                if (implicit) {
                    return FormValidation.error("If you load a library implicitly, you must specify a default version.");
                }
                if (!allowVersionOverride) {
                    return FormValidation.error("If you deny overriding a default version, you must define that version.");
                }
                return FormValidation.ok();
            } else {
                for (LibraryResolver resolver : ExtensionList.lookup(LibraryResolver.class)) {
                    for (LibraryConfiguration config : resolver.fromConfiguration(Stapler.getCurrentRequest())) {
                        if (config.getName().equals(name)) {
                            return config.getRetriever().validateVersion(name, defaultVersion);
                        }
                    }
                }
                return FormValidation.ok("Cannot validate default version until after saving and reconfiguring.");
            }
        }

        public FormValidation doClearCache(@QueryParameter String name) throws InterruptedException {
            FilePath cacheDir = new FilePath(new File(LibraryConfiguration.getGlobalLibrariesCacheDir(), name));
                try {
                    if (cacheDir.exists()) {
                        cacheDir.deleteRecursive();
                    }           
                } catch (IOException ex) {
                    return FormValidation.error("The cache dir was not deleted successfully.\n" + ex.toString());
                }
            return FormValidation.ok("The cache dir was deleted successfully.");
        }

        /* TODO currently impossible; autoCompleteField does not support passing neighboring fields:
        public AutoCompletionCandidates doAutoCompleteDefaultVersion(@QueryParameter String defaultVersion, @QueryParameter String name) {
            AutoCompletionCandidates candidates = new AutoCompletionCandidates();
            for (LibraryResolver resolver : ExtensionList.lookup(LibraryResolver.class)) {
                for LibraryConfiguration config : resolver.fromConfiguration(Stapler.getCurrentRequest()) {
                    // TODO define LibraryRetriever.completeVersions
                    if (config.getName().equals(name) && config.getRetriever() instanceof SCMSourceRetriever) {
                        try {
                            for (String revision : ((SCMSourceRetriever) config.getRetriever()).getScm().fetchRevisions(null)) {
                                if (revision.startsWith(defaultVersion)) {
                                    candidates.add(revision);
                                }
                            }
                        } catch (IOException | InterruptedException x) {
                            LOGGER.log(Level.FINE, null, x);
                        }
                    }
                }
            }
            return candidates;
        }
        */

    }

}
