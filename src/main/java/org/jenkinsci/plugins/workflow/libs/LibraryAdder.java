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
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
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
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;
import org.jenkinsci.plugins.workflow.cps.GlobalVariableSet;
import org.jenkinsci.plugins.workflow.cps.global.UserDefinedGlobalVariable;
import org.jenkinsci.plugins.workflow.cps.replay.OriginalLoadedScripts;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayAction;
import org.jenkinsci.plugins.workflow.flow.FlowCopier;

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
        Map<String,String> libraryChangesets = new HashMap<>();
        Map<String,String> librariesUnparsed = new HashMap<>();
        for (String library : libraries) {
            String[] parsed = parse(library);
            libraryVersions.put(parsed[0], parsed[1]);
            libraryChangesets.put(parsed[0], parsed[2]);
            librariesUnparsed.put(parsed[0], library);
        }
        List<Addition> additions = new ArrayList<>();
        LibrariesAction action = build.getAction(LibrariesAction.class);
        if (action != null) {
            // Resuming a build, so just look up what we loaded before.
            for (LibraryRecord record : action.getLibraries()) {
                FilePath libDir = new FilePath(execution.getOwner().getRootDir()).child("libs/" + record.name);
                for (String root : new String[] {"src", "vars"}) {
                    FilePath dir = libDir.child(root);
                    if (dir.isDirectory()) {
                        additions.add(new Addition(dir.toURI().toURL(), record.trusted));
                    }
                }
                String unparsed = librariesUnparsed.get(record.name);
                if (unparsed != null) {
                    libraries.remove(unparsed);
                }
            }
            return additions;
        }
        // Now we will see which libraries we want to load for this job.
        Map<String,LibraryRecord> librariesAdded = new LinkedHashMap<>();
        Map<String,LibraryRetriever> retrievers = new HashMap<>();
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
                String version = cfg.defaultedVersion(libraryVersions.remove(name));
                boolean changesets = cfg.defaultedChangesets(libraryChangesets.remove(name));
                librariesAdded.put(name, new LibraryRecord(name, version, kindTrusted, changesets));
                retrievers.put(name, cfg.getRetriever());
            }
        }
        for (String name : librariesAdded.keySet()) {
            String unparsed = librariesUnparsed.get(name);
            if (unparsed != null) {
                libraries.remove(unparsed);
            }
        }
        // Record libraries we plan to load. We need LibrariesAction there first so variables can be interpolated.
        build.addAction(new LibrariesAction(new ArrayList<>(librariesAdded.values())));
        // Now actually try to retrieve the libraries.
        for (LibraryRecord record : librariesAdded.values()) {
            listener.getLogger().println("Loading library " + record.name + "@" + record.version);
            for (URL u : retrieve(record.name, record.version, retrievers.get(record.name), record.trusted, record.changesets, listener, build, execution, record.variables)) {
                additions.add(new Addition(u, record.trusted));
            }
        }
        return additions;
    }

    static @Nonnull String[] parse(@Nonnull String identifier) {
        String name = null;
        String version = null;
        String changeset = null;

        //count the number of occurances of the separator to determine needs
        int counter = 0;
        for( int i=0; i<identifier.length(); i++ ) {
            if( identifier.charAt(i) == '@' ) {
                counter++;
            }
        }

        if (counter == 0) {
          // pick up defaultVersion and default changeset configuration
          name = identifier;
        }

        if (counter == 1) {
            int at = identifier.indexOf('@');
           // single @ means it's only a version so use that and use default changeset configuration
            name = identifier.substring(0, at);
            version = identifier.substring(at + 1);
        }

        if (counter == 2) {
          // this could have no changeset behaviour defined and include @ in the version, or it could have changeset
          int at = identifier.indexOf('@');
          name = identifier.substring(0, at);
          String remainder = identifier.substring(at + 1);
          if (!remainder.matches("^.*@(changesets|nochangesets|true|false|yes|no)$")) {
              version = remainder;
          } else {
              at = remainder.indexOf('@');
              version = remainder.substring(0, at);
              changeset = remainder.substring(at + 1);
          }
        }

        if (counter == 3) {
          // with 3 @ characters we have an svn style something@foo version so handle it
          int at = identifier.indexOf('@');
          name = identifier.substring(0, at);
          String remainder = identifier.substring(at + 1);
          at = remainder.lastIndexOf('@');
          changeset = remainder.substring(at + 1);
          version = remainder.substring(0, at);
        }

        // allow overriding changeset without also overriding version
        if (version != null) {
            if (version.equals("null")) {
                version = null;
          }
        }

        // normalise changeset to make life easier
        if (changeset != null) {
            if (changeset.matches("(changesets|true|yes)")) {
                changeset = "true";
            } else if (changeset.matches("(nochangesets|false|no)")) {
                changeset = "false";
            }
        }

        return new String[] {name, version, changeset};
    }

    /** Retrieve library files. */
    static List<URL> retrieve(@Nonnull String name, @Nonnull String version, @Nonnull LibraryRetriever retriever, boolean trusted, boolean changesets, @Nonnull TaskListener listener, @Nonnull Run<?,?> run, @Nonnull CpsFlowExecution execution, @Nonnull Set<String> variables) throws Exception {
        FilePath libDir = new FilePath(execution.getOwner().getRootDir()).child("libs/" + name);
        retriever.retrieve(name, version, changesets, libDir, run, listener);
        // Replace any classes requested for replay:
        if (!trusted) {
            for (String clazz : ReplayAction.replacementsIn(execution)) {
                for (String root : new String[] {"src", "vars"}) {
                    String rel = root + "/" + clazz.replace('.', '/') + ".groovy";
                    FilePath f = libDir.child(rel);
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
        if (urls.isEmpty()) {
            throw new AbortException("Library " + name + " expected to contain at least one of src or vars directories");
        }
        return urls;
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
                                continue; // TODO JENKINS-41157 allow replay of trusted libraries if you have RUN_SCRIPTS
                            }
                            for (String rootName : new String[] {"src", "vars"}) {
                                FilePath root = libs.child(library.name + "/" + rootName);
                                if (!root.isDirectory()) {
                                    continue;
                                }
                                for (FilePath groovy : root.list("**/*.groovy")) {
                                    String clazz = groovy.getRemote().replaceFirst("^\\Q" + root.getRemote() + "\\E[/\\\\](.+)[.]groovy", "$1").replace('/', '.').replace('\\', '.');
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

    @Extension public static class Copier extends FlowCopier.ByRun {

        @Override public void copy(Run<?,?> original, Run<?,?> copy, TaskListener listener) throws IOException, InterruptedException {
            LibrariesAction action = original.getAction(LibrariesAction.class);
            if (action != null) {
                copy.addAction(new LibrariesAction(action.getLibraries()));
                FilePath libs = new FilePath(original.getRootDir()).child("libs");
                if (libs.isDirectory()) {
                    libs.copyRecursiveTo(new FilePath(copy.getRootDir()).child("libs"));
                }
            }
        }

    }

}
