/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

import hudson.Functions;
import java.util.Collections;
import jenkins.branch.BranchSource;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.traits.BranchDiscoveryTrait;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject;
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProjectTest;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.Url;

public class SCMRetrieverTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Url("https://stackoverflow.com/a/49112612/12916")
    @Test public void selfTestLibraries() throws Exception {
        Assume.assumeFalse("File names are too long for Windows", Functions.isWindows()); // Related to changes in plugin-pom version 3.22.
        sampleRepo.init();
        sampleRepo.write("vars/greet.groovy", "def call(recipient) {echo(/hello to $recipient/)}");
        sampleRepo.write("src/pkg/Clazz.groovy", "package pkg; class Clazz {static String whereAmI() {'earth'}}");
        sampleRepo.write("Jenkinsfile", "semaphore BRANCH_NAME; def lib = library identifier: 'stuff@snapshot', retriever: legacySCM(scm); greet(lib.pkg.Clazz.whereAmI())");
        sampleRepo.git("add", "vars", "src", "Jenkinsfile");
        sampleRepo.git("commit", "--message=init");
        sampleRepo.git("checkout", "-b", "branch");
        sampleRepo.write("src/pkg/Clazz.groovy", "package pkg; class Clazz {static String whereAmI() {'the world'}}");
        sampleRepo.git("commit", "--all", "--message=branching");
        GlobalLibraries.get().setLibraries(Collections.singletonList(new LibraryConfiguration("stuff", new SCMSourceRetriever(new GitSCMSource("ignored")))));
        WorkflowMultiBranchProject mp = r.jenkins.createProject(WorkflowMultiBranchProject.class, "mp");
        GitSCMSource source = new GitSCMSource(sampleRepo.toString());
        source.getTraits().add(new BranchDiscoveryTrait());
        mp.getSourcesList().add(new BranchSource(source));
        WorkflowJob branch = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "branch");
        WorkflowRun b1;
        while ((b1 = branch.getBuildByNumber(1)) == null) {
            Thread.sleep(100);
        }
        SemaphoreStep.waitForStart("branch/1", b1);
        sampleRepo.write("src/pkg/Clazz.groovy", "package pkg; class Clazz {static String whereAmI() {'all the world'}}");
        sampleRepo.git("commit", "--all", "--message=editing");
        SemaphoreStep.success("branch/1", null);
        r.assertLogContains("hello to the world", r.waitForCompletion(b1));
        SemaphoreStep.success("branch/2", null);
        WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "branch"); // forces detection of second commit
        WorkflowRun b2;
        while ((b2 = branch.getBuildByNumber(2)) == null) {
            Thread.sleep(100);
        }
        r.assertLogContains("hello to all the world", r.waitForCompletion(b2));
        WorkflowJob master = WorkflowMultiBranchProjectTest.scheduleAndFindBranchProject(mp, "master");
        WorkflowRun m1;
        while ((m1 = master.getBuildByNumber(1)) == null) {
            Thread.sleep(100);
        }
        SemaphoreStep.success("master/1", null);
        r.assertLogContains("hello to earth", r.waitForCompletion(m1));
    }

}
