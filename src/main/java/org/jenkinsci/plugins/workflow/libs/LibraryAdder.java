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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;
import org.jenkinsci.plugins.workflow.cps.GlobalVariableSet;
import org.jenkinsci.plugins.workflow.cps.global.UserDefinedGlobalVariable;
import org.jenkinsci.plugins.workflow.cps.replay.OriginalLoadedScripts;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayAction;
import org.jenkinsci.plugins.workflow.steps.scm.GenericSCMStep;
import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;

/**
 * Given {@link LibraryConfiguration.LibrariesForJob}, actually adds to the Groovy classpath.
 */
@Extension public class LibraryAdder implements LibraryDecorator.Adder {

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
            int at = library.indexOf('@');
            if (at == -1) {
                libraryVersions.put(library, null); // pick up defaultVersion
            } else {
                libraryVersions.put(library.substring(0, at), library.substring(at + 1));
            }
        }
        // Now we will see which libraries we want to load for this job.
        Map<String,LibraryRecord> librariesAdded = new LinkedHashMap<>();
        Map<String,SCMSource> sources = new HashMap<>();
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
                librariesAdded.put(name, new LibraryRecord(name, version, new TreeSet<String>(), kindTrusted));
                sources.put(name, cfg.getScm());
            }
        }
        // Record libraries we plan to load. We need LibrariesAction there first so variables can be interpolated.
        build.addAction(new LibrariesAction(new ArrayList<>(librariesAdded.values())));
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
        for (LibraryRecord record : librariesAdded.values()) {
            String name = record.name;
            String version = record.version;
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
                File libDir = new File(execution.getOwner().getRootDir(), "libs/" + name);
                FileUtils.forceMkdir(libDir);
                // Replace any classes requested for replay:
                if (!record.trusted) {
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
                // Pack up src.jar and/or vars.jar and/or resources.jar with relevant files from the checkout:
                boolean addingSomething = false;
                FilePath srcDir = dir.child("src");
                if (srcDir.isDirectory()) {
                    addingSomething = true;
                    File srcJar = new File(libDir, "src.jar");
                    try (OutputStream os = new FileOutputStream(srcJar)) {
                        srcDir.zip(os, "**/*.groovy");
                    }
                    additions.add(new Addition(srcJar.toURI().toURL(), record.trusted));
                }
                FilePath varsDir = dir.child("vars");
                if (varsDir.isDirectory()) {
                    addingSomething = true;
                    File varsJar = new File(libDir, "vars.jar");
                    try (OutputStream os = new FileOutputStream(varsJar)) {
                        varsDir.zip(os, "*.groovy,*.txt");
                    }
                    additions.add(new Addition(varsJar.toURI().toURL(), record.trusted));
                    for (FilePath var : varsDir.list("*.groovy")) {
                        record.variables.add(var.getBaseName());
                    }
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
                    try {
                        vars.add(new UserDefinedGlobalVariable(variable, new URL("jar:" + new File(run.getRootDir(), "libs/" + library.name + "/vars.jar").toURI() + "!/" + variable + ".txt")));
                    } catch (MalformedURLException x) {
                        LOGGER.log(Level.WARNING, null, x);
                    }
                }
            }
            return vars;
        }

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
                        File libs = new File(run.getRootDir(), "libs");
                        for (LibraryRecord library : action.getLibraries()) {
                            if (library.trusted) {
                                continue; // TODO for simplicity we do not allow replay of trusted libraries, even if you have RUN_SCRIPTS
                            }
                            for (String jarName : new String[] {"src.jar", "vars.jar"}) {
                                File jar = new File(new File(libs, library.name), jarName);
                                if (jar.isFile()) {
                                    try (JarFile jf = new JarFile(jar, false)) {
                                        Enumeration<JarEntry> entries = jf.entries();
                                        while (entries.hasMoreElements()) {
                                            JarEntry entry = entries.nextElement();
                                            String name = entry.getName();
                                            if (name.endsWith(".groovy")) {
                                                scripts.put(name.substring(0, name.length() - ".groovy".length()).replace('/', '.'),
                                                            IOUtils.toString(jf.getInputStream(entry))); // TODO no idea what encoding the Groovy compiler uses
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, null, x);
            }
            return scripts;
        }

    }

}
