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
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.slaves.WorkspaceList;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;
import org.jenkinsci.plugins.workflow.cps.GlobalVariableSet;
import org.jenkinsci.plugins.workflow.cps.global.UserDefinedGlobalVariable;
import org.jenkinsci.plugins.workflow.cps.replay.OriginalLoadedScripts;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayAction;
import org.jenkinsci.plugins.workflow.steps.scm.GenericSCMStep;
import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;

/**
 * Given {@link LibraryResolver}, actually adds to the Groovy classpath.
 */
@Extension public class LibraryAdder extends ClasspathAdder {

    private static final Logger LOGGER = Logger.getLogger(LibraryAdder.class.getName());

    @Override public List<Addition> add(CpsFlowExecution execution, List<String> libraries) throws Exception {
        Queue.Executable executable = execution.getOwner().getExecutable();
        Run<?,?> build;
        if (executable instanceof Run) {
            build = (Run) executable;
        } else {
            // SCM.checkout does not make it possible to do checkouts outside the context of a Run.
            return Collections.emptyList();
        }
        // First parse the library declarations (if any) looking for requested versions.
        Map<String,String> libraryVersions = new HashMap<>();
        for (String library : libraries) {
            String[] parsed = parse(library);
            libraryVersions.put(parsed[0], parsed[1]);
        }
        // Now we will see which libraries we want to load for this job.
        Map<String,LibraryRecord> librariesAdded = new LinkedHashMap<>();
        Map<String,SCMSource> sources = new HashMap<>();
        TaskListener listener = execution.getOwner().getListener();
        for (LibraryResolver kind : ExtensionList.lookup(LibraryResolver.class)) {
            boolean kindTrusted = kind.isTrusted();
            for (LibraryConfiguration cfg : kind.forJob(build.getParent(), libraryVersions)) {
                String name = cfg.getName();
                if (!cfg.isImplicit() && !libraryVersions.containsKey(name)) {
                    continue; // not using this one at all
                }
                if (librariesAdded.containsKey(name)) {
                    listener.getLogger().println("Only using first definition of library " + name);
                    continue;
                }
                String version = cfg.defaultedVersion(libraryVersions.get(name));
                librariesAdded.put(name, new LibraryRecord(name, version, kindTrusted));
                sources.put(name, cfg.getScm());
            }
        }
        // Record libraries we plan to load. We need LibrariesAction there first so variables can be interpolated.
        build.addAction(new LibrariesAction(new ArrayList<>(librariesAdded.values())));
        // Now actually try to check out the libraries.
        CheckoutContext checkoutContext = CheckoutContext.forBuild(build, execution);
        List<Addition> additions = new ArrayList<>();
        for (LibraryRecord record : librariesAdded.values()) {
            listener.getLogger().println("Loading library " + record.name + "@" + record.version);
            for (URL u : doAdd(record.name, record.version, sources.get(record.name), record.trusted, listener, checkoutContext, build, execution, record.variables)) {
                additions.add(new Addition(u, record.trusted));
            }
        }
        return additions;
    }

    @Override public List<Addition> readd(CpsFlowExecution execution) {
        Queue.Executable executable;
        try {
            executable = execution.getOwner().getExecutable();
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
            return null;
        }
        Run<?, ?> build;
        if (executable instanceof Run) {
            build = (Run) executable;
        } else {
            return null;
        }
        LibrariesAction action = build.getAction(LibrariesAction.class);
        if (action == null) {
            return null;
        }
        List<Addition> additions = new ArrayList<>();
        for (LibraryRecord record : action.getLibraries()) {
            try {
                FilePath libDir = new FilePath(execution.getOwner().getRootDir()).child("libs/" + record.name);
                for (String root : new String[] {"src", "vars"}) {
                    FilePath dir = libDir.child(root);
                    if (dir.isDirectory()) {
                        additions.add(new Addition(dir.toURI().toURL(), record.trusted));
                    }
                }
            } catch (IOException | InterruptedException x) {
                LOGGER.log(Level.WARNING, "could not readd " + record, x);
            }
        }
        return additions;
    }

    private static class CheckoutContext {
        final @Nonnull Node node;
        final @Nonnull Computer computer;
        final @Nonnull FilePath root;
        CheckoutContext(Node node, Computer computer, FilePath root) {
            this.node = node;
            this.computer = computer;
            this.root = root;
        }
        // Adapted from CpsScmFlowDefinition:
        static CheckoutContext forBuild(@Nonnull Run<?,?> build, @Nonnull CpsFlowExecution execution) throws IOException {
            FilePath root;
            Node node = Jenkins.getActiveInstance();
            if (build.getParent() instanceof TopLevelItem) {
                FilePath baseWorkspace = node.getWorkspaceFor((TopLevelItem) build.getParent());
                if (baseWorkspace == null) {
                    throw new IOException(node.getDisplayName() + " may be offline");
                }
                root = baseWorkspace.withSuffix(getFilePathSuffix() + "libs");
            } else { // should not happen, but just in case:
                root = new FilePath(execution.getOwner().getRootDir()).child("libs");
            }
            Computer computer = node.toComputer();
            if (computer == null) {
                throw new IOException(node.getDisplayName() + " may be offline");
            }
            return new CheckoutContext(node, computer, root);
        }
    }

    private static @Nonnull String[] parse(@Nonnull String identifier) {
        int at = identifier.indexOf('@');
        if (at == -1) {
            return new String[] {identifier, null}; // pick up defaultVersion
        } else {
            return new String[] {identifier.substring(0, at), identifier.substring(at + 1)};
        }
    }

    // TODO 1.652 has tempDir API but there is no API to make other variants
    private static String getFilePathSuffix() {
        return System.getProperty(WorkspaceList.class.getName(), "@");
    }

    /** Perform an SCM checkout and copy the relevant files. */
    private static List<URL> doAdd(@Nonnull String name, @Nonnull String version, @Nonnull SCMSource scm, boolean trusted, @Nonnull TaskListener listener, @Nonnull CheckoutContext checkoutContext, @Nonnull Run<?,?> run, @Nonnull CpsFlowExecution execution, @Nonnull Set<String> variables) throws Exception {
        SCMRevision revision = scm.fetch(version, listener);
        if (revision == null) {
            throw new AbortException("No version " + version + " found for library " + name);
        }
        SCMStep delegate = new GenericSCMStep(scm.build(revision.getHead(), revision));
        delegate.setPoll(!revision.isDeterministic()); // TODO is this desirable?
        delegate.setChangelog(true); // TODO is this desirable?
        FilePath dir = checkoutContext.root.child(name);
        try (WorkspaceList.Lease lease = checkoutContext.computer.getWorkspaceList().acquire(dir)) {
            delegate.checkout(run, dir, listener, checkoutContext.node.createLauncher(listener));
            // Cannot add WorkspaceActionImpl to private CpsFlowExecution.flowStartNodeActions; do we care?
            // Replace any classes requested for replay:
            if (!trusted) {
                for (String clazz : ReplayAction.replacementsIn(execution)) {
                    for (String root : new String[] {"src", "vars"}) {
                        String rel = root + "/" + clazz.replace('.', '/') + ".groovy";
                        FilePath f = dir.child(rel);
                        if (f.exists()) {
                            String replacement = ReplayAction.replace(execution, clazz);
                            if (replacement != null) {
                                listener.getLogger().println("Replacing contents of " + rel);
                                f.write(replacement, null); // TODO as below, unsure of encoding used by Groovy compiler
                            }
                        }
                    }
                }
            }
            // Copy sources with relevant files from the checkout:
            FilePath libDir = new FilePath(execution.getOwner().getRootDir()).child("libs/" + name);
            if (dir.copyRecursiveTo("src/**/*.groovy,vars/*.groovy,vars/*.txt,resources/", null, libDir) == 0) {
                throw new AbortException("Library " + name + " expected to contain at least one of src or vars directories");
            }
            List<URL> urls = new ArrayList<>();
            FilePath srcDir = libDir.child("src");
            if (srcDir.isDirectory()) {
                urls.add(srcDir.toURI().toURL());
            }
            FilePath varsDir = libDir.child("vars");
            if (varsDir.isDirectory()) {
                urls.add(varsDir.toURI().toURL());
                for (FilePath var : varsDir.list("*.groovy")) {
                    variables.add(var.getBaseName());
                }
            }
            return urls;
        }
    }

    /**
     * Loads resources for {@link ResourceStep}.
     * @param execution a build
     * @param name a resource name, à la {@link Class#getResource(String)} but with no leading {@code /} allowed
     * @return a map from {@link LibraryRecord#name} to file contents
     */
    static @Nonnull Map<String,String> findResources(@Nonnull CpsFlowExecution execution, @Nonnull String name) throws IOException, InterruptedException {
        Map<String,String> resources = new TreeMap<>();
        Queue.Executable executable = execution.getOwner().getExecutable();
        if (executable instanceof Run) {
            Run<?,?> run = (Run) executable;
            LibrariesAction action = run.getAction(LibrariesAction.class);
            if (action != null) {
                FilePath libs = new FilePath(run.getRootDir()).child("libs");
                for (LibraryRecord library : action.getLibraries()) {
                    FilePath f = libs.child(library.name + "/resources/" + name);
                    if (f.exists()) {
                        resources.put(library.name, f.readToString());
                    }
                }
            }
        }
        return resources;
    }

    @Extension public static class GlobalVars extends GlobalVariableSet {

        @Override public Collection<GlobalVariable> forRun(Run<?,?> run) {
            if (run == null) {
                return Collections.emptySet();
            }
            LibrariesAction action = run.getAction(LibrariesAction.class);
            if (action == null) {
                return Collections.emptySet();
            }
            List<GlobalVariable> vars = new ArrayList<>();
            for (LibraryRecord library : action.getLibraries()) {
                for (String variable : library.variables) {
                    vars.add(new UserDefinedGlobalVariable(variable, new File(run.getRootDir(), "libs/" + library.name + "/vars/" + variable + ".txt")));
                }
            }
            return vars;
        }

        // TODO implement forJob by checking each LibraryConfiguration and scanning SCMFileSystem when implemented (JENKINS-33273)

    }

    @Extension public static class LoadedLibraries extends OriginalLoadedScripts {

        @Override public Map<String,String> loadScripts(CpsFlowExecution execution) {
            Map<String,String> scripts = new HashMap<>();
            try {
                Queue.Executable executable = execution.getOwner().getExecutable();
                if (executable instanceof Run) {
                    Run<?,?> run = (Run) executable;
                    LibrariesAction action = run.getAction(LibrariesAction.class);
                    if (action != null) {
                        FilePath libs = new FilePath(run.getRootDir()).child("libs");
                        for (LibraryRecord library : action.getLibraries()) {
                            if (library.trusted) {
                                continue; // TODO for simplicity we do not allow replay of trusted libraries, even if you have RUN_SCRIPTS
                            }
                            for (String rootName : new String[] {"src", "vars"}) {
                                FilePath root = libs.child(library.name + "/" + rootName);
                                for (FilePath groovy : root.list("**/*.groovy")) {
                                    String clazz = groovy.getRemote().replaceFirst("^\\Q" + root.getRemote() + "\\E[/\\\\](.+)[.]groovy", "$1").replace('/', '.');
                                    scripts.put(clazz, groovy.readToString()); // TODO no idea what encoding the Groovy compiler uses
                                }
                            }
                        }
                    }
                }
            } catch (IOException | InterruptedException x) {
                LOGGER.log(Level.WARNING, null, x);
            }
            return scripts;
        }

    }

}
