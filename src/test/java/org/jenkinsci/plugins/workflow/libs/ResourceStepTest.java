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

import hudson.FilePath;
import hudson.Functions;
import hudson.model.Result;
import hudson.model.Run;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.apache.commons.codec.binary.Base64;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.junit.Assume.*;
import org.junit.Test;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class ResourceStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Test public void smokes() throws Exception {
        initFixedContentLibrary();
        
        GlobalLibraries.get().setLibraries(Collections.singletonList(
            new LibraryConfiguration("stuff", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)))));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('stuff@master') import pkg.Stuff; echo(/got ${Stuff.contents(this)}/)", true));
        r.assertLogContains("got fixed contents", r.buildAndAssertSuccess(p));
    }

    @Test public void caching() throws Exception {
        clearCache("stuff");
        initFixedContentLibrary();
        
        LibraryConfiguration libraryConfig =  new LibraryConfiguration("stuff", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)));
        libraryConfig.setCachingConfiguration(new LibraryCachingConfiguration(0, ""));
        GlobalLibraries.get().setLibraries(Collections.singletonList(libraryConfig));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");

        p.setDefinition(new CpsFlowDefinition("@Library('stuff@master') import pkg.Stuff; echo(/got ${Stuff.contents(this)}/)", true));
        WorkflowRun firstBuild = r.buildAndAssertSuccess(p);
        r.assertLogContains("got fixed contents", firstBuild);
        r.assertLogContains("Caching library stuff@master", firstBuild);
        r.assertLogContains("git", firstBuild); // git is called

        WorkflowRun secondBuild = r.buildAndAssertSuccess(p);
        r.assertLogContains("got fixed contents", secondBuild);
        r.assertLogContains("Library stuff@master is cached. Copying from home.", secondBuild);
        r.assertLogNotContains("git", secondBuild); // git is not called
    }

    @Test public void cachingExcludedLibrary() throws Exception {
        clearCache("stuff");
        initFixedContentLibrary();
        
        LibraryConfiguration libraryConfig =  new LibraryConfiguration("stuff", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)));
        libraryConfig.setCachingConfiguration(new LibraryCachingConfiguration(0, "test_unused other"));
        GlobalLibraries.get().setLibraries(Collections.singletonList(libraryConfig));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");

        p.setDefinition(new CpsFlowDefinition("@Library('stuff@master') import pkg.Stuff; echo(/got ${Stuff.contents(this)}/)", true));
        WorkflowRun firstBuild = r.buildAndAssertSuccess(p);
        r.assertLogContains("got fixed contents", firstBuild);
        r.assertLogContains("Caching library stuff@master", firstBuild);
        r.assertLogContains("git", firstBuild); // git is called

        p.setDefinition(new CpsFlowDefinition("@Library('stuff@other') import pkg.Stuff; echo(/got ${Stuff.contents(this)}/)", true));
        WorkflowRun secondBuild = r.buildAndAssertSuccess(p);
        r.assertLogContains("got fixed contents", secondBuild);
        r.assertLogContains("Library stuff@other is excluded from caching.", secondBuild);
        r.assertLogContains("git", secondBuild); // git is called
    }

    @Test public void cachingRefresh() throws Exception {
        clearCache("stuff");
        initFixedContentLibrary();
        
        LibraryConfiguration libraryConfig =  new LibraryConfiguration("stuff", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)));
        libraryConfig.setCachingConfiguration(new LibraryCachingConfiguration(60, "test_unused other"));
        GlobalLibraries.get().setLibraries(Collections.singletonList(libraryConfig));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");

        p.setDefinition(new CpsFlowDefinition("@Library('stuff@master') import pkg.Stuff; echo(/got ${Stuff.contents(this)}/)", true));
        WorkflowRun firstBuild = r.buildAndAssertSuccess(p);
        r.assertLogContains("got fixed contents", firstBuild);
        r.assertLogContains("Caching library stuff@master", firstBuild);
        r.assertLogContains("git", firstBuild); // git is called

        modifyCacheTimestamp("stuff", "master", System.currentTimeMillis() - 60000 * 55); // 55 minutes have passed, still cached
        WorkflowRun secondBuild = r.buildAndAssertSuccess(p);
        r.assertLogContains("got fixed contents", secondBuild);
        r.assertLogContains("Library stuff@master is cached. Copying from home.", secondBuild);
        r.assertLogNotContains("git", secondBuild); // git is not called

        modifyCacheTimestamp("stuff", "master", System.currentTimeMillis() - 60000 * 61); // 61 minutes have passed, due for a refresh
        WorkflowRun thirdBuild = r.buildAndAssertSuccess(p);
        r.assertLogContains("got fixed contents", thirdBuild);
        r.assertLogContains("Library stuff@master is due for a refresh after 60 minutes, clearing.", thirdBuild);
        r.assertLogContains("Caching library stuff@master", thirdBuild);
        r.assertLogContains("git", thirdBuild); // git is called
    }

    @Test public void missingResource() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("libraryResource 'whatever'", true));
        r.assertLogContains(Messages.ResourceStep_no_such_library_resource_could_be_found_("whatever"), r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
    }

    @Test public void duplicatedResources() throws Exception {
        sampleRepo.init();
        sampleRepo.write("src/pkg/Stuff.groovy", "package pkg; class Stuff {static def contents(script) {script.libraryResource 'pkg/file'}}");
        sampleRepo.write("resources/pkg/file", "initial contents");
        sampleRepo.git("add", "src", "resources");
        sampleRepo.git("commit", "--message=init");
        sampleRepo.git("tag", "v1");
        sampleRepo.write("resources/pkg/file", "subsequent contents");
        sampleRepo.git("commit", "--all", "--message=edited");
        LibraryConfiguration stuff1 = new LibraryConfiguration("stuff1", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)));
        stuff1.setDefaultVersion("v1");
        LibraryConfiguration stuff2 = new LibraryConfiguration("stuff2", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)));
        stuff2.setDefaultVersion("master");
        GlobalLibraries.get().setLibraries(Arrays.asList(stuff1, stuff2));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library(['stuff1', 'stuff2']) import pkg.Stuff; echo(/got ${Stuff.contents(this)}/)", true));
        r.assertLogContains(Messages.ResourceStep_library_resource_ambiguous_among_librari("pkg/file", "[stuff1, stuff2]"), r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
    }

    @Issue("JENKINS-52313")
    @Test public void specifyResourceEncoding() throws Exception {
        assumeFalse("TODO mojibake on windows-11-2.164.1", Functions.isWindows());
        sampleRepo.init();
        sampleRepo.write("src/pkg/Stuff.groovy", "package pkg; class Stuff {" +
                "static def utf8(script) {script.libraryResource(resource: 'pkg/utf8', encoding: 'ISO-8859-15')}\n" +
                "static def binary(script) {script.libraryResource(resource: 'pkg/binary', encoding: 'Base64')}}");
        Path resourcesDir = Paths.get(sampleRepo.getRoot().getPath(), "resources", "pkg");
        Files.createDirectories(resourcesDir);
        // '¤' encoded using ISO-8859-1 should turn into '€' when decoding using ISO-8859-15.
        Files.write(resourcesDir.resolve("utf8"), Arrays.asList("¤"), StandardCharsets.ISO_8859_1);
        byte[] binaryData = {0x48, 0x45, 0x4c, 0x4c, 0x4f, (byte) 0x80, (byte) 0xec, (byte) 0xf4, 0x00, 0x0d, 0x1b};
        Files.write(resourcesDir.resolve("binary"), binaryData);
        sampleRepo.git("add", "src", "resources");
        sampleRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(
            new LibraryConfiguration("stuff", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)))));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('stuff@master') import pkg.Stuff; echo(Stuff.utf8(this)); echo(Stuff.binary(this))", true));
        Run run = r.buildAndAssertSuccess(p);
        r.assertLogContains("€", run);
        r.assertLogContains(Base64.encodeBase64String(binaryData), run);
    }

    public void initFixedContentLibrary() throws Exception {
        sampleRepo.init();
        sampleRepo.write("src/pkg/Stuff.groovy", "package pkg; class Stuff {static def contents(script) {script.libraryResource 'pkg/file'}}");
        sampleRepo.write("resources/pkg/file", "fixed contents");
        sampleRepo.git("add", "src", "resources");
        sampleRepo.git("commit", "--message=init");
        sampleRepo.git("branch", "other");
    }

    public void clearCache(String name) throws Exception {
        FilePath cacheDir = new FilePath(LibraryCachingConfiguration.getGlobalLibrariesCacheDir(), name);
        if (cacheDir.exists()) {
            cacheDir.deleteRecursive();
        }
    }

    public void modifyCacheTimestamp(String name, String version, long timestamp) throws Exception {
        FilePath cacheDir = new FilePath(LibraryCachingConfiguration.getGlobalLibrariesCacheDir(), name);
        FilePath versionCacheDir = new FilePath(cacheDir, version);
        if (versionCacheDir.exists()) {
            versionCacheDir.touch(timestamp);
        }
    }

}
