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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Functions;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.listeners.ItemListener;
import hudson.scm.SCM;
import hudson.slaves.WorkspaceList;
import hudson.util.FormValidation;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceDescriptor;
import java.util.regex.Pattern;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.structs.describable.CustomDescribableModel;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.steps.scm.GenericSCMStep;
import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * Uses {@link SCMSource#fetch(String, TaskListener)} to retrieve a specific revision.
 */
public class SCMSourceRetriever extends LibraryRetriever {

    private static final Logger LOGGER = Logger.getLogger(SCMSourceRetriever.class.getName());

    @SuppressFBWarnings(value="MS_SHOULD_BE_FINAL", justification="Non-final for write access via the Script Console")
    public static boolean INCLUDE_SRC_TEST_IN_LIBRARIES = Boolean.getBoolean(SCMSourceRetriever.class.getName() + ".INCLUDE_SRC_TEST_IN_LIBRARIES");
    /**
     * Matches ".." in positions where it would be treated as the parent directory.
     *
     * <p>Used to prevent {@link #libraryPath} from being used for directory traversal.
     */
    static final Pattern PROHIBITED_DOUBLE_DOT = Pattern.compile("(^|.*[\\\\/])\\.\\.($|[\\\\/].*)");

    private final SCMSource scm;

    /**
     * The path to the library inside of the SCM.
     *
     * {@code null} is the default and means that the library is in the root of the repository. Otherwise, the value is
     * considered to be a relative path inside of the repository and always ends in a forward slash
     *
     * @see #setLibraryPath
     */
    private @CheckForNull String libraryPath;

    @DataBoundConstructor public SCMSourceRetriever(SCMSource scm) {
        this.scm = scm;
    }

    /**
     * A source of SCM checkouts.
     */
    public SCMSource getScm() {
        return scm;
    }

    public String getLibraryPath() {
        return libraryPath;
    }

    @DataBoundSetter public void setLibraryPath(String libraryPath) {
        libraryPath = Util.fixEmptyAndTrim(libraryPath);
        if (libraryPath != null && !libraryPath.endsWith("/")) {
            libraryPath += '/';
        }
        this.libraryPath = libraryPath;
    }

    @Override public void retrieve(String name, String version, boolean changelog, FilePath target, Run<?, ?> run, TaskListener listener) throws Exception {
        SCMRevision revision = retrySCMOperation(listener, () -> scm.fetch(version, listener, run.getParent()));
        if (revision == null) {
            throw new AbortException("No version " + version + " found for library " + name);
        }
        doRetrieve(name, changelog, scm.build(revision.getHead(), revision), libraryPath, target, run, listener);
    }

    @Override public void retrieve(String name, String version, FilePath target, Run<?, ?> run, TaskListener listener) throws Exception {
        retrieve(name, version, true, target, run, listener);
    }

    private static <T> T retrySCMOperation(TaskListener listener, Callable<T> task) throws Exception{
        T ret = null;
        for (int retryCount = Jenkins.get().getScmCheckoutRetryCount(); retryCount >= 0; retryCount--) {
            try {
                ret = task.call();
                break;
            }
            catch (AbortException e) {
                // abort exception might have a null message.
                // If so, just skip echoing it.
                if (e.getMessage() != null) {
                    listener.error(e.getMessage());
                }
            }
            catch (InterruptedIOException e) {
                throw e;
            }
            catch (Exception e) {
                // checkout error not yet reported
                Functions.printStackTrace(e, listener.error("Checkout failed"));
            }

            if (retryCount == 0)   // all attempts failed
                throw new AbortException("Maximum checkout retry attempts reached, aborting");

            listener.getLogger().println("Retrying after 10 seconds");
            Thread.sleep(10000);
        }
        return ret;
    }

    @SuppressFBWarnings(value = "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE", justification = "apparently bogus complaint about redundant nullcheck in try-with-resources")
    static void doRetrieve(String name, boolean changelog, @Nonnull SCM scm, String libraryPath, FilePath target, Run<?, ?> run, TaskListener listener) throws Exception {
        // Adapted from CpsScmFlowDefinition:
        SCMStep delegate = new GenericSCMStep(scm);
        delegate.setPoll(false); // TODO we have no API for determining if a given SCMHead is branch-like or tag-like; would we want to turn on polling if the former?
        delegate.setChangelog(changelog);
        FilePath dir;
        Node node = Jenkins.get();
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
            retrySCMOperation(listener, () -> {
                delegate.checkout(run, lease.path, listener, node.createLauncher(listener));
                return null;
            });
            if (libraryPath == null) {
                libraryPath = ".";
            } else if (PROHIBITED_DOUBLE_DOT.matcher(libraryPath).matches()) {
                throw new AbortException("Library path may not contain '..'");
            }
            String excludes = INCLUDE_SRC_TEST_IN_LIBRARIES ? null : "src/test/";
            if (lease.path.child(libraryPath).child("src/test").exists()) {
                listener.getLogger().println("Excluding src/test/ from checkout of " + scm.getKey() + " so that shared library test code cannot be accessed by Pipelines.");
                listener.getLogger().println("To remove this log message, move the test code outside of src/. To restore the previous behavior that allowed access to files in src/test/, pass -D" + SCMSourceRetriever.class.getName() + ".INCLUDE_SRC_TEST_IN_LIBRARIES=true to the java command used to start Jenkins.");
            }
            // Cannot add WorkspaceActionImpl to private CpsFlowExecution.flowStartNodeActions; do we care?
            // Copy sources with relevant files from the checkout:
            lease.path.child(libraryPath).copyRecursiveTo("src/**/*.groovy,vars/*.groovy,vars/*.txt,resources/", excludes, target);
        }
    }

    // TODO there is WorkspaceList.tempDir but no API to make other variants
    private static String getFilePathSuffix() {
        return System.getProperty(WorkspaceList.class.getName(), "@");
    }

    @Override public FormValidation validateVersion(String name, String version, Item context) {
        StringWriter w = new StringWriter();
        try {
            StreamTaskListener listener = new StreamTaskListener(w);
            SCMRevision revision = scm.fetch(version, listener, context);
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
    @Extension public static class DescriptorImpl extends LibraryRetrieverDescriptor implements CustomDescribableModel {

        static FormValidation checkLibraryPath(@QueryParameter String libraryPath) {
            libraryPath = Util.fixEmptyAndTrim(libraryPath);
            if (libraryPath == null) {
                return FormValidation.ok();
            } else if (PROHIBITED_DOUBLE_DOT.matcher(libraryPath).matches()) {
                return FormValidation.error(Messages.SCMSourceRetriever_library_path_no_double_dot());
            }
            return FormValidation.ok();
        }

        @POST
        public FormValidation doCheckLibraryPath(@QueryParameter String libraryPath) {
            return checkLibraryPath(libraryPath);
        }

        @Override public String getDisplayName() {
            return "Modern SCM";
        }

        /**
         * Returns only implementations overriding {@link SCMSource#retrieve(String, TaskListener)} or {@link SCMSource#retrieve(String, TaskListener, Item)}.
         */
        @Restricted(NoExternalUse.class) // Jelly, Hider
        public Collection<SCMSourceDescriptor> getSCMDescriptors() {
            List<SCMSourceDescriptor> descriptors = new ArrayList<>();
            for (SCMSourceDescriptor d : ExtensionList.lookup(SCMSourceDescriptor.class)) {
                if (Util.isOverridden(SCMSource.class, d.clazz, "retrieve", String.class, TaskListener.class) || Util.isOverridden(SCMSource.class, d.clazz, "retrieve", String.class, TaskListener.class, Item.class)) {
                    descriptors.add(d);
                }
            }
            return descriptors;
        }

        @Override public UninstantiatedDescribable customUninstantiate(UninstantiatedDescribable ud) {
            Object scm = ud.getArguments().get("scm");
            if (scm instanceof UninstantiatedDescribable) {
                UninstantiatedDescribable scmUd = (UninstantiatedDescribable) scm;
                Map<String, Object> scmArguments = new HashMap<>(scmUd.getArguments());
                scmArguments.remove("id");
                Map<String, Object> retrieverArguments = new HashMap<>(ud.getArguments());
                retrieverArguments.put("scm", scmUd.withArguments(scmArguments));
                return ud.withArguments(retrieverArguments);
            }
            return ud;
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

    @Restricted(DoNotUse.class)
    @Extension
    public static class WorkspaceListener extends ItemListener {

        @Override
        public void onDeleted(Item item) {
            deleteLibsDir(item, item.getFullName());
        }

        @Override
        public void onLocationChanged(Item item, String oldFullName, String newFullName) {
            deleteLibsDir(item, oldFullName);
        }

        private static void deleteLibsDir(Item item, String itemFullName) {
            if (item instanceof Job
                    && item.getClass()
                            .getName()
                            .equals("org.jenkinsci.plugins.workflow.job.WorkflowJob")) {
                synchronized (item) {
                    String base =
                            expandVariablesForDirectory(
                                    Jenkins.get().getRawWorkspaceDir(),
                                    itemFullName,
                                    item.getRootDir().getPath());
                    FilePath dir =
                            new FilePath(new File(base)).withSuffix(getFilePathSuffix() + "libs");
                    try {
                        if (dir.isDirectory()) {
                            LOGGER.log(
                                    Level.INFO,
                                    () -> "Deleting obsolete shared library workspace " + dir);
                            dir.deleteRecursive();
                        }
                    } catch (IOException | InterruptedException e) {
                        LOGGER.log(
                                Level.WARNING,
                                e,
                                () -> "Could not delete obsolete shared library workspace " + dir);
                    }
                }
            }
        }

        private static String expandVariablesForDirectory(
                String base, String itemFullName, String itemRootDir) {
            // If the item is moved, it is too late to look up its original workspace location by
            // the time we get the notification. See:
            // https://github.com/jenkinsci/jenkins/blob/f03183ab09ce5fb8f9f4cc9ccee42a3c3e6b2d3e/core/src/main/java/jenkins/model/Jenkins.java#L2567-L2576
            Map<String, String> properties = new HashMap<>();
            properties.put("JENKINS_HOME", Jenkins.get().getRootDir().getPath());
            properties.put("ITEM_ROOTDIR", itemRootDir);
            properties.put("ITEM_FULLNAME", itemFullName); // legacy, deprecated
            properties.put(
                    "ITEM_FULL_NAME", itemFullName.replace(':', '$')); // safe, see JENKINS-12251
            return Util.replaceMacro(base, Collections.unmodifiableMap(properties));
        }
    }
}
