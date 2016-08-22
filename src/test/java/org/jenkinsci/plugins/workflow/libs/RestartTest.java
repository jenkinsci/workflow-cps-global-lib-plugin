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

import com.cloudbees.hudson.plugins.folder.Folder;
import java.util.Collections;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayAction;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class RestartTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule rr = new RestartableJenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Test public void smokes() {
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                sampleRepo.init();
                sampleRepo.write("src/pkg/Slow.groovy", "package pkg; class Slow {static void wait(script) {script.semaphore 'wait-class'}}");
                sampleRepo.write("vars/slow.groovy", "def call() {semaphore 'wait-var'}");
                sampleRepo.git("add", "src", "vars");
                sampleRepo.git("commit", "--message=init");
                GlobalLibraries.get().setLibraries(Collections.singletonList(new LibraryConfiguration("stuff", new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true))));
                WorkflowJob p = rr.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("@Library('stuff@master') import pkg.Slow; echo 'at the beginning'; Slow.wait(this); echo 'in the middle'; slow(); echo 'at the end'", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait-class/1", b);
            }
        });
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = rr.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                SemaphoreStep.success("wait-class/1", null);
                SemaphoreStep.waitForStart("wait-var/1", b);
            }
        });
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = rr.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                SemaphoreStep.success("wait-var/1", null);
                rr.j.assertLogContains("at the end", rr.j.waitForCompletion(b));
                assertEquals(1, b.getActions(LibrariesAction.class).size());
            }
        });
    }

    @Test public void replay() {
        final String initialScript = "def call() {semaphore 'wait'; echo 'initial content'}";
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                sampleRepo.init();
                sampleRepo.write("vars/slow.groovy", initialScript);
                sampleRepo.git("add", "vars");
                sampleRepo.git("commit", "--message=init");
                Folder d = rr.j.jenkins.createProject(Folder.class, "d");
                d.getProperties().add(new FolderLibraries(Collections.singletonList(new LibraryConfiguration("stuff", new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)))));
                WorkflowJob p = d.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("@Library('stuff@master') _ = slow()", true));
                p.save(); // TODO should probably be implicit in setDefinition
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b1);
            }
        });
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = rr.j.jenkins.getItemByFullName("d/p", WorkflowJob.class);
                WorkflowRun b1 = p.getLastBuild();
                SemaphoreStep.success("wait/1", null);
                rr.j.assertLogContains("initial content", rr.j.waitForCompletion(b1));
                ReplayAction ra = b1.getAction(ReplayAction.class);
                WorkflowRun b2 = (WorkflowRun) ra.run(ra.getOriginalScript(), Collections.singletonMap("slow", initialScript.replace("initial", "subsequent"))).waitForStart();
                SemaphoreStep.waitForStart("wait/2", b2);
            }
        });
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = rr.j.jenkins.getItemByFullName("d/p", WorkflowJob.class);
                WorkflowRun b2 = p.getLastBuild();
                SemaphoreStep.success("wait/2", null);
                rr.j.assertLogContains("subsequent content", rr.j.waitForCompletion(b2));
            }
        });
    }

}
