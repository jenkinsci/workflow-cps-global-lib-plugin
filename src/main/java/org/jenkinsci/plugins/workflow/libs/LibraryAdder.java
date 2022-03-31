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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;
import org.jenkinsci.plugins.workflow.cps.GlobalVariableSet;
import org.jenkinsci.plugins.workflow.cps.global.UserDefinedGlobalVariable;
import org.jenkinsci.plugins.workflow.cps.replay.OriginalLoadedScripts;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayAction;
import org.jenkinsci.plugins.workflow.flow.FlowCopier;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;

/**
 * Given {@link LibraryResolver}, actually adds to the Groovy classpath.
 */
@Extension public class LibraryAdder extends ClasspathAdder {

    private static final Logger LOGGER = Logger.getLogger(LibraryAdder.class.getName());
    
    static Map<String, ReentrantReadWriteLock> cacheRetrieveLock = new HashMap<>();

    @Override public List<Addition> add(CpsFlowExecution execution, List<String> libraries, HashMap<String, Boolean> changelogs) throws Exception {
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
        Map<String,Boolean> libraryChangelogs = new HashMap<>();
        Map<String,String> librariesUnparsed = new HashMap<>();
        for (String library : libraries) {
            String[] parsed = parse(library);
            libraryVersions.put(parsed[0], parsed[1]);
            libraryChangelogs.put(parsed[0], changelogs.get(library));
            librariesUnparsed.put(parsed[0], library);
        }
        List<Addition> additions = new ArrayList<>();
        LibrariesAction action = build.getAction(LibrariesAction.class);
        if (action != null) {
            // Resuming a build, so just look up what we loaded before.
            for (LibraryRecord record : action.getLibraries()) {
                FilePath libDir = new FilePath(execution.getOwner().getRootDir()).child("libs/" + record.getDirectoryName());
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
                Boolean changelog = cfg.defaultedChangelogs(libraryChangelogs.remove(name));
                String source = kind.getClass().getName();
                if (cfg instanceof LibraryResolver.ResolvedLibraryConfiguration) {
                    source = ((LibraryResolver.ResolvedLibraryConfiguration) cfg).getSource();
                }
                librariesAdded.put(name, new LibraryRecord(name, version, kindTrusted, changelog, cfg.getCachingConfiguration(), source));
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
            for (URL u : retrieve(record, retrievers.get(record.name), listener, build, execution)) {
                additions.add(new Addition(u, record.trusted));
            }
        }
        return additions;
    }

    static @NonNull String[] parse(@NonNull String identifier) {
       int at = identifier.indexOf('@');
        if (at == -1) {
            return new String[] {identifier, null}; // pick up defaultVersion
        } else {
            return new String[] {identifier.substring(0, at), identifier.substring(at + 1)};
        }
    }

    /*
     * Return codes:
     *   0: cachedir exists and is valid
     *   1: cachedir does not exist
     *   2: cachedir exists but is expired
     */
    private static int cacheStatus(LibraryCachingConfiguration cachingConfiguration, final FilePath versionCacheDir)
          throws IOException, InterruptedException
    {
        if (cachingConfiguration.isRefreshEnabled()) {
            final long cachingMilliseconds = cachingConfiguration.getRefreshTimeMilliseconds();

            if(versionCacheDir.exists()) {
        	if ((versionCacheDir.lastModified() + cachingMilliseconds) > System.currentTimeMillis()) {
        	    return 0;
        	} else {
        	    return 2;
        	}
            } else {
        	return 1;
            }
        } else {
            if (versionCacheDir.exists()) {
        	return 0;
            } else {
        	return 1;
            }
            
        }
    }
    
    /** Retrieve library files. */
    static List<URL> retrieve(@NonNull LibraryRecord record, @NonNull LibraryRetriever retriever, @NonNull TaskListener listener, @NonNull Run<?,?> run, @NonNull CpsFlowExecution execution) throws Exception {
        String name = record.name;
        String version = record.version;
        boolean changelog = record.changelog;
        LibraryCachingConfiguration cachingConfiguration = record.cachingConfiguration;
        FilePath libDir = new FilePath(execution.getOwner().getRootDir()).child("libs/" + record.getDirectoryName());
        Boolean shouldCache = cachingConfiguration != null;
        final FilePath versionCacheDir = new FilePath(LibraryCachingConfiguration.getGlobalLibrariesCacheDir(), record.getDirectoryName());
        ReentrantReadWriteLock retrieveLock = cacheRetrieveLock.get(record.getDirectoryName());
        final FilePath lastReadFile = new FilePath(versionCacheDir, LibraryCachingConfiguration.LAST_READ_FILE);
        

        if(shouldCache && cachingConfiguration.isExcluded(version)) {
            listener.getLogger().println("Library " + name + "@" + version + " is excluded from caching.");
            shouldCache = false;
        }
        
        
        if(shouldCache) {
            if (retrieveLock == null) {
        	retrieveLock = new ReentrantReadWriteLock(true);
        	cacheRetrieveLock.put(record.getDirectoryName(), retrieveLock);
            }

            retrieveLock.readLock().lock();
            try {
                if (cacheStatus(cachingConfiguration, versionCacheDir) > 0) {
                    retrieveLock.readLock().unlock();
                    retrieveLock.writeLock().lock();
                    try {
                	boolean retrieve = false;
                	switch (cacheStatus(cachingConfiguration, versionCacheDir)) {
            	    	    default:
                	    case 0: 
                		listener.getLogger().println("Library " + name + "@" + version + " is cached. Copying from home."); 
                                break;
                	    case 1:
                		retrieve = true;
                		break;
                	    case 2:
                		long cachingMinutes = cachingConfiguration.getRefreshTimeMinutes();
                		listener.getLogger().println("Library " + name + "@" + version + " is due for a refresh after " + cachingMinutes + " minutes, clearing.");
                                if (versionCacheDir.exists()) {
                                    versionCacheDir.deleteRecursive();
                                }
                                retrieve = true;
                		break;
                	}
                		    
                        if (retrieve) {
                            listener.getLogger().println("Caching library " + name + "@" + version);                            
                            versionCacheDir.mkdirs();
                            retriever.retrieve(name, version, changelog, versionCacheDir, run, listener);
                        }
                        retrieveLock.readLock().lock();
                    } finally {
                        retrieveLock.writeLock().unlock(); 
                    }
                } else {
                    listener.getLogger().println("Library " + name + "@" + version + " is cached. Copying from home.");  
                }
  
                lastReadFile.touch(System.currentTimeMillis());
                versionCacheDir.copyRecursiveTo(libDir);
            } finally {
              retrieveLock.readLock().unlock();
            }
        } else {
            retriever.retrieve(name, version, changelog, libDir, run, listener);
        }
        // Write the user-provided name to a file as a debugging aid.
        libDir.withSuffix("-name.txt").write(name, "UTF-8");

        // Replace any classes requested for replay:
        if (!record.trusted) {
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
                record.variables.add(var.getBaseName());
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
    static @NonNull Map<String,String> findResources(@NonNull CpsFlowExecution execution, @NonNull String name, @CheckForNull String encoding) throws IOException, InterruptedException {
        Map<String,String> resources = new TreeMap<>();
        Queue.Executable executable = execution.getOwner().getExecutable();
        if (executable instanceof Run) {
            Run<?,?> run = (Run) executable;
            LibrariesAction action = run.getAction(LibrariesAction.class);
            if (action != null) {
                FilePath libs = new FilePath(run.getRootDir()).child("libs");
                for (LibraryRecord library : action.getLibraries()) {
                    FilePath libResources = libs.child(library.getDirectoryName() + "/resources/");
                    FilePath f = libResources.child(name);
                    if (!new File(f.getRemote()).getCanonicalFile().toPath().startsWith(new File(libResources.getRemote()).getCanonicalPath())) {
                        throw new AbortException(name + " references a file that is not contained within the library: " + library.name);
                    } else if (f.exists()) {
                        resources.put(library.name, readResource(f, encoding));
                    }
                }
            }
        }
        return resources;
    }

    private static String readResource(FilePath file, @CheckForNull String encoding) throws IOException, InterruptedException {
        try (InputStream in = file.read()) {
            if ("Base64".equals(encoding)) {
                return Base64.getEncoder().encodeToString(IOUtils.toByteArray(in));
            } else {
                return IOUtils.toString(in, encoding); // The platform default is used if encoding is null.
            }
        }
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
                    vars.add(new UserDefinedGlobalVariable(variable, new File(run.getRootDir(), "libs/" + library.getDirectoryName() + "/vars/" + variable + ".txt")));
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
                                continue; // TODO JENKINS-41157 allow replay of trusted libraries if you have ADMINISTER
                            }
                            for (String rootName : new String[] {"src", "vars"}) {
                                FilePath root = libs.child(library.getDirectoryName() + "/" + rootName);
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
