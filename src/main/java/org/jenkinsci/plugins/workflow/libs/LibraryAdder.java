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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.steps.scm.GenericSCMStep;
import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;

/**
 * Given {@link LibraryConfiguration.LibrariesForJob}, actually adds to the Groovy classpath.
 */
@Extension public class LibraryAdder implements LibraryDecorator.Adder {
    
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
            int at = library.indexOf('@');
            if (at == -1) {
                libraryVersions.put(library, null); // pick up defaultVersion
            } else {
                libraryVersions.put(library.substring(0, at), library.substring(at + 1));
            }
        }
        // Now we will see which libraries we want to load for this job.
        Map<String,String> librariesAdded = new LinkedHashMap<>();
        Map<String,SCMSource> sources = new HashMap<>();
        Map<String,Boolean> trusted = new HashMap<>();
        TaskListener listener = execution.getOwner().getListener();
        for (LibraryConfiguration.LibrariesForJob kind : ExtensionList.lookup(LibraryConfiguration.LibrariesForJob.class)) {
            boolean kindTrusted = kind.isTrusted();
            for (LibraryConfiguration cfg : kind.forJob(build.getParent())) {
                String name = cfg.getName();
                if (!cfg.isImplicit() && !libraryVersions.containsKey(name)) {
                    continue; // not using this one at all
                }
                if (librariesAdded.containsKey(name)) {
                    listener.getLogger().println("Only using first definition of library " + name);
                    continue;
                }
                String version = libraryVersions.get(name);
                if (version == null) {
                    version = cfg.getDefaultVersion();
                    if (version == null) {
                        throw new AbortException("No version specified for library " + name);
                    }
                } else if (!cfg.isAllowVersionOverride()) {
                    throw new AbortException("Version override not permitted for library " + name);
                }
                librariesAdded.put(name, version);
                sources.put(name, cfg.getScm());
                trusted.put(name, kindTrusted);
            }
        }
        // Record libraries we plan to load. We need LibrariesAction there first so variables can be interpolated.
        build.addAction(new LibrariesAction(librariesAdded));
        // Now actually try to check out the libraries.
        List<Addition> additions = new ArrayList<>();
        // Adapted from CpsScmFlowDefinition:
        FilePath checkoutRoot;
        Node node = Jenkins.getActiveInstance();
        if (build.getParent() instanceof TopLevelItem) {
            FilePath baseWorkspace = node.getWorkspaceFor((TopLevelItem) build.getParent());
            if (baseWorkspace == null) {
                throw new IOException(node.getDisplayName() + " may be offline");
            }
            checkoutRoot = baseWorkspace.withSuffix(getFilePathSuffix() + "libs");
        } else { // should not happen, but just in case:
            checkoutRoot = new FilePath(execution.getOwner().getRootDir()).child("libs");
        }
        Computer computer = node.toComputer();
        if (computer == null) {
            throw new IOException(node.getDisplayName() + " may be offline");
        }
        for (Map.Entry<String,String> entry : librariesAdded.entrySet()) {
            String name = entry.getKey();
            String version = entry.getValue();
            listener.getLogger().println("Loading library " + name + "@" + version);
            // Perform an SCM checkout and JAR up the relevant files.
            SCMSource source = sources.get(name);
            SCMRevision revision = source.fetch(version, listener);
            if (revision == null) {
                throw new AbortException("No version " + version + " found for library " + name);
            }
            SCMStep delegate = new GenericSCMStep(source.build(revision.getHead(), revision));
            delegate.setPoll(!revision.isDeterministic()); // TODO is this desirable?
            delegate.setChangelog(true); // TODO is this desirable?
            FilePath dir = checkoutRoot.child(name);
            try (WorkspaceList.Lease lease = computer.getWorkspaceList().acquire(dir)) {
                delegate.checkout(build, dir, listener, node.createLauncher(listener));
                // Cannot add WorkspaceActionImpl to private CpsFlowExecution.flowStartNodeActions; do we care?
                // Pack up src.jar and/or vars.jar and/or resources.jar with relevant files from the checkout:
                File libDir = new File(execution.getOwner().getRootDir(), "libs/" + name);
                if (!libDir.mkdirs()) {
                    throw new AbortException("Failed to create " + libDir);
                }
                boolean addingSomething = false;
                FilePath srcDir = dir.child("src");
                if (srcDir.isDirectory()) {
                    addingSomething = true;
                    File srcJar = new File(libDir, "src.jar");
                    try (OutputStream os = new FileOutputStream(srcJar)) {
                        srcDir.zip(os, "**/*.groovy");
                    }
                    additions.add(new Addition(srcJar.toURI().toURL(), trusted.get(name)));
                }
                FilePath varsDir = dir.child("vars");
                if (varsDir.isDirectory()) {
                    addingSomething = true;
                    File varsJar = new File(libDir, "vars.jar");
                    try (OutputStream os = new FileOutputStream(varsJar)) {
                        varsDir.zip(os, "*.groovy,*.txt");
                    }
                    additions.add(new Addition(varsJar.toURI().toURL(), trusted.get(name)));
                }
                FilePath resourcesDir = dir.child("resources");
                if (resourcesDir.isDirectory()) {
                    addingSomething = true;
                    File resourcesJar = new File(libDir, "resources.jar");
                    try (OutputStream os = new FileOutputStream(resourcesJar)) {
                        resourcesDir.zip(os);
                    }
                }
                if (!addingSomething) {
                    throw new AbortException("Library " + name + " expected to contain at least one of src, vars, or resources directories");
                }
            }
        }
        return additions;
    }

    // TODO 1.652 has tempDir API but there is no API to make other variants
    private String getFilePathSuffix() {
        return System.getProperty(WorkspaceList.class.getName(), "@");
    }

}
