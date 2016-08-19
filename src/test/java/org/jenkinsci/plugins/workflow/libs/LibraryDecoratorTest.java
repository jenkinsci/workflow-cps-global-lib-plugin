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
import hudson.model.Result;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class LibraryDecoratorTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void singleLibrary() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        FileUtils.write(new File(p.getRootDir(), "libs/foo/pkg/Lib.groovy"), "package pkg; class Lib {static String CONST = 'constant'}");
        p.setDefinition(new CpsFlowDefinition("@Library('foo') import pkg.Lib; echo(/using ${Lib.CONST}/)", true));
        r.assertLogContains("using constant", r.buildAndAssertSuccess(p));
    }

    @Test public void severalLibraries() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        // stub syntax; @TestExtension GlobalVariable would allow s/new one()()/one()/
        p.setDefinition(new CpsFlowDefinition("@Library(['x', 'y']) import one; import two; echo(/loaded ${new one()()} and ${new two()()}/)", true));
        FileUtils.write(new File(p.getRootDir(), "libs/x/one.groovy"), "def call() {1}");
        FileUtils.write(new File(p.getRootDir(), "libs/y/two.groovy"), "def call() {2}");
        r.assertLogContains("loaded 1 and 2", r.buildAndAssertSuccess(p));
    }

    @TestExtension public static class TestAdder extends ClasspathAdder {
        @Override public List<Addition> add(CpsFlowExecution execution, List<String> libraries) throws Exception {
            List<Addition> additions = new ArrayList<>();
            for (String library : libraries) {
                additions.add(new Addition(new File(((WorkflowRun) execution.getOwner().getExecutable()).getParent().getRootDir(), "libs/" + library).toURI().toURL(), false));
            }
            return additions;
        }
    }

    @Test public void malformedAnnotation() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library(99) import /* irrelevant */ java.lang.Void", true));
        r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
    }

    @Test public void adderError() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('stuff') import /* irrelevant */ java.lang.Void", true));
        r.assertLogContains("failed to load [stuff]", r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
    }
    @TestExtension("adderError") public static class ErroneousAdder extends ClasspathAdder {
        @Override public List<Addition> add(CpsFlowExecution execution, List<String> libraries) throws Exception {
            throw new AbortException("failed to load " + libraries);
        }
    }
}
