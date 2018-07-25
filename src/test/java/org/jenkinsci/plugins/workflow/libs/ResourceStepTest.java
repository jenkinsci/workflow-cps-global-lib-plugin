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

import hudson.model.Result;
import java.util.Arrays;
import java.util.Collections;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Test;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class ResourceStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Test public void smokes() throws Exception {
        sampleRepo.init();
        sampleRepo.write("src/pkg/Stuff.groovy", "package pkg; class Stuff {static def contents(script) {script.libraryResource 'pkg/file'}}");
        sampleRepo.write("resources/pkg/file", "fixed contents");
        sampleRepo.git("add", "src", "resources");
        sampleRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(
            new LibraryConfiguration("stuff", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)))));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('stuff@master') import pkg.Stuff; echo(/got ${Stuff.contents(this)}/)", true));
        r.assertLogContains("got fixed contents", r.buildAndAssertSuccess(p));
    }

    @Test public void missingResource() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("libraryResource 'whatever'", true));
        r.assertLogContains(Messages.ResourceStep_no_such_library_resource_could_be_found_("whatever"), r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
    }

    @Test public void missingResourceWithLibraryNme() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("libraryResource resource: 'whatever', libraryName: 'library'", true));
        r.assertLogContains(Messages.ResourceStep_no_such_library_resource_could_be_found_("whatever"), r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
    }


    @Test public void duplicatedResources() throws Exception {
        createSampleRepo(null);
        GlobalLibraries.get().setLibraries(Arrays.asList(createLibrary("stuff1", "v1"),
                createLibrary("stuff2", "master")));
        WorkflowJob p = createDuplicateLibraryJob();
        r.assertLogContains(Messages.ResourceStep_library_resource_ambiguous_among_librari("pkg/file", "[stuff1, stuff2]"), r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
    }

    @Test public void duplicatedResourcesWithLibraryName() throws Exception {
        createSampleRepo("stuff2");
        GlobalLibraries.get().setLibraries(Arrays.asList(createLibrary("stuff1", "v1"),
                createLibrary("stuff2", "master")));
        WorkflowJob p = createDuplicateLibraryJob();
        r.assertLogContains("subsequent contents", r.buildAndAssertSuccess(p));
    }

    private void createSampleRepo(String libraryName) throws Exception {
        String groovyScript = "package pkg; class Stuff {static def contents(script) {script.libraryResource resource: 'pkg/file'";
        if (libraryName != null) {
            groovyScript += ", libraryName: '" + libraryName + "'";
        }
        groovyScript += "}}";
        sampleRepo.init();
        sampleRepo.write("src/pkg/Stuff.groovy", groovyScript);
        sampleRepo.write("resources/pkg/file", "initial contents");
        sampleRepo.git("add", "src", "resources");
        sampleRepo.git("commit", "--message=init");
        sampleRepo.git("tag", "v1");
        sampleRepo.write("resources/pkg/file", "subsequent contents");
        sampleRepo.git("commit", "--all", "--message=edited");
    }

    private LibraryConfiguration createLibrary(String name, String version) {
        LibraryConfiguration library = new LibraryConfiguration(name, new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)));
        library.setDefaultVersion(version);
        return library;
    }

    private WorkflowJob createDuplicateLibraryJob() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library(['stuff1', 'stuff2']) import pkg.Stuff; echo(/got ${Stuff.contents(this)}/)", true));
        return p;
    }

}
