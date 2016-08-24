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

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import hudson.AbortException;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.RelativePath;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.StreamTaskListener;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Collection;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.impl.SingleSCMSource;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;

/**
 * User configuration for one library.
 */
public class LibraryConfiguration extends AbstractDescribableImpl<LibraryConfiguration> {

    private final String name;
    private final SCMSource scm;
    private String defaultVersion;
    private boolean implicit;
    private boolean allowVersionOverride = true;

    @DataBoundConstructor public LibraryConfiguration(String name, SCMSource scm) {
        this.name = name;
        this.scm = scm;
    }

    /**
     * Library name.
     * Should match {@link Library#value}, up to the first occurrence of {@code @}, if any.
     */
    public String getName() {
        return name;
    }

    /**
     * A source of SCM checkouts.
     */
    public SCMSource getScm() {
        return scm;
    }

    /**
     * The default version to use with {@link #getScm} if none other is specified.
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

    @Extension public static class DescriptorImpl extends Descriptor<LibraryConfiguration> {

        public SCMSourceDescriptor getDefaultSCMDescriptor() {
            return Jenkins.getActiveInstance().getDescriptorByType(SingleSCMSource.DescriptorImpl.class);
        }

        /**
         * Returns only implementations overriding {@link SCMSource#retrieve(String, TaskListener)}.
         * (The default implementation only supports branch names, so you would be better off using {@link SingleSCMSource}.)
         */
        public Collection<SCMSourceDescriptor> getSCMDescriptors() {
            return Collections2.filter(ExtensionList.lookup(SCMSourceDescriptor.class), new Predicate<SCMSourceDescriptor>() {
                @Override public boolean apply(SCMSourceDescriptor input) {
                    return Util.isOverridden(SCMSource.class, input.clazz, "retrieve", String.class, TaskListener.class);
                }
            });
        }

        public FormValidation doCheckName(@QueryParameter String name) {
            if (name.isEmpty()) {
                return FormValidation.warning("You must enter a name.");
            } else {
                // Currently no character restrictions.
                return FormValidation.ok();
            }
        }

        public FormValidation doCheckDefaultVersion(@QueryParameter String defaultVersion, @QueryParameter boolean implicit, @QueryParameter boolean allowVersionOverride, @QueryParameter @RelativePath("scm") String id) {
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
                    SCMSource scm = resolver.getSCMSource(id, Stapler.getCurrentRequest());
                    if (scm instanceof SingleSCMSource) {
                        return FormValidation.ok("Cannot validate default version with legacy SCM plugins. Use a different SCM if available.");
                    } else if (scm != null) {
                        StringWriter w = new StringWriter();
                        try {
                            StreamTaskListener listener = new StreamTaskListener(w);
                            SCMRevision revision = scm.fetch(defaultVersion, listener);
                            if (revision != null) {
                                // TODO validate repository structure using SCMFileSystem when implemented (JENKINS-33273)
                                return FormValidation.ok("Currently maps to revision: " + revision);
                            } else {
                                listener.getLogger().flush();
                                return FormValidation.warning("Revision seems invalid:\n" + w);
                            }
                        } catch (IOException | InterruptedException x) {
                            return FormValidation.warning(x, "Cannot validate default version.");
                        }
                    }
                }
                return FormValidation.ok("Cannot validate default version until after saving and reconfiguring.");
            }
        }

        /* TODO does not work; autoCompleteField does not support passing neighboring fields:
        public AutoCompletionCandidates doAutoCompleteDefaultVersion(@QueryParameter String value, @QueryParameter @RelativePath("scm") String id) {
            AutoCompletionCandidates candidates = new AutoCompletionCandidates();
            for (LibraryResolver resolver : ExtensionList.lookup(LibraryResolver.class)) {
                SCMSource scm = resolver.getSCMSource(id, Stapler.getCurrentRequest());
                if (scm instanceof SingleSCMSource) {
                    // cannot complete anything
                } else if (scm != null) {
                    try {
                        for (String revision : scm.fetchRevisions(null)) {
                            if (revision.startsWith(value)) {
                                candidates.add(revision);
                            }
                        }
                    } catch (IOException | InterruptedException x) {
                        LOGGER.log(Level.FINE, null, x);
                    }
                }
            }
            return candidates;
        }
        */

    }

}