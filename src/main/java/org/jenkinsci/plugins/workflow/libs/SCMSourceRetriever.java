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
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.scm.SCM;
import hudson.slaves.WorkspaceList;
import hudson.util.FormValidation;
import hudson.util.StreamTaskListener;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.steps.scm.GenericSCMStep;
import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Uses {@link SCMSource#fetch(String, TaskListener)} to retrieve a specific revision.
 */
public class SCMSourceRetriever extends LibraryRetriever {

    private final SCMSource scm;

    @DataBoundConstructor public SCMSourceRetriever(SCMSource scm) {
        this.scm = scm;
    }

    /**
     * A source of SCM checkouts.
     */
    public SCMSource getScm() {
        return scm;
    }

    @Override public void retrieve(String name, String version, boolean changesets, FilePath target, Run<?, ?> run, TaskListener listener) throws Exception {
        SCMRevision revision = scm.fetch(version, listener);
        if (revision == null) {
            throw new AbortException("No version " + version + " found for library " + name);
        }
        doRetrieve(name, changesets, scm.build(revision.getHead(), revision), target, run, listener);
    }

    static void doRetrieve(String name, boolean changesets, @Nonnull SCM scm, FilePath target, Run<?, ?> run, TaskListener listener) throws Exception {
        // Adapted from CpsScmFlowDefinition:
        SCMStep delegate = new GenericSCMStep(scm);
        delegate.setPoll(false); // TODO we have no API for determining if a given SCMHead is branch-like or tag-like; would we want to turn on polling if the former?
        delegate.setChangelog(changesets);
        FilePath dir;
        Node node = Jenkins.getActiveInstance();
        if (run.getParent() instanceof TopLevelItem) {
            FilePath baseWorkspace = node.getWorkspaceFor((TopLevelItem) run.getParent());
            if (baseWorkspace == null) {
                throw new IOException(node.getDisplayName() + " may be offline");
            }
            dir = baseWorkspace.withSuffix(getFilePathSuffix() + "libs").child(name);
        } else { // should not happen, but just in case:
            throw new AbortException("Cannot check out in non-top-level build");
        }
        Computer computer = node.toComputer();
        if (computer == null) {
            throw new IOException(node.getDisplayName() + " may be offline");
        }
        try (WorkspaceList.Lease lease = computer.getWorkspaceList().allocate(dir)) {
            delegate.checkout(run, lease.path, listener, node.createLauncher(listener));
            // Cannot add WorkspaceActionImpl to private CpsFlowExecution.flowStartNodeActions; do we care?
            // Copy sources with relevant files from the checkout:
            lease.path.copyRecursiveTo("src/**/*.groovy,vars/*.groovy,vars/*.txt,resources/", null, target);
        }
    }

    // TODO 1.652 has tempDir API but there is no API to make other variants
    private static String getFilePathSuffix() {
        return System.getProperty(WorkspaceList.class.getName(), "@");
    }

    @Override public FormValidation validateVersion(String name, String version) {
        StringWriter w = new StringWriter();
        try {
            StreamTaskListener listener = new StreamTaskListener(w);
            SCMRevision revision = scm.fetch(version, listener);
            if (revision != null) {
                // TODO validate repository structure using SCMFileSystem when implemented (JENKINS-33273)
                return FormValidation.ok("Currently maps to revision: " + revision);
            } else {
                listener.getLogger().flush();
                return FormValidation.warning("Revision seems invalid:\n" + w);
            }
        } catch (Exception x) {
            return FormValidation.warning(x, "Cannot validate default version.");
        }
    }

    @Symbol("modernSCM")
    @Extension public static class DescriptorImpl extends LibraryRetrieverDescriptor {

        @Override public String getDisplayName() {
            return "Modern SCM";
        }

        /**
         * Returns only implementations overriding {@link SCMSource#retrieve(String, TaskListener)}.
         */
        @Restricted(NoExternalUse.class) // Jelly, Hider
        public Collection<SCMSourceDescriptor> getSCMDescriptors() {
            List<SCMSourceDescriptor> descriptors = new ArrayList<>();
            for (SCMSourceDescriptor d : ExtensionList.lookup(SCMSourceDescriptor.class)) {
                if (Util.isOverridden(SCMSource.class, d.clazz, "retrieve", String.class, TaskListener.class)) {
                    descriptors.add(d);
                }
            }
            return descriptors;
        }

    }

    @Restricted(DoNotUse.class)
    @Extension public static class Hider extends DescriptorVisibilityFilter {

        @SuppressWarnings("rawtypes")
        @Override public boolean filter(Object context, Descriptor descriptor) {
            if (descriptor instanceof DescriptorImpl) {
                return !((DescriptorImpl) descriptor).getSCMDescriptors().isEmpty();
            } else {
                return true;
            }
        }

    }

}
