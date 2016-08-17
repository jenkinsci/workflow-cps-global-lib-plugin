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

import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import java.util.Collections;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.impl.SingleSCMSource;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class LibraryAdderTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Test public void smokes() throws Exception {
        sampleRepo.init();
        sampleRepo.write("src/pkg/Lib.groovy", "package pkg; class Lib {static String CONST = 'constant'}");
        sampleRepo.git("add", "src");
        sampleRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(
            new LibraryConfiguration("stuff",
                new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true))));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('stuff@master') import pkg.Lib; echo(/using ${Lib.CONST}/)", true));
        r.assertLogContains("using constant", r.buildAndAssertSuccess(p));
        sampleRepo.git("tag", "1.0");
        sampleRepo.write("src/pkg/Lib.groovy", "package pkg; class Lib {static String CONST = 'modified'}");
        sampleRepo.git("commit", "--all", "--message=modified");
        r.assertLogContains("using modified", r.buildAndAssertSuccess(p));
        p.setDefinition(new CpsFlowDefinition("@Library('stuff@1.0') import pkg.Lib; echo(/using ${Lib.CONST}/)", true));
        r.assertLogContains("using constant", r.buildAndAssertSuccess(p));
    }

    @Test public void usingInterpolation() throws Exception {
        sampleRepo.init();
        sampleRepo.write("src/pkg/Lib.groovy", "package pkg; class Lib {static String CONST = 'initial'}");
        sampleRepo.git("add", "src");
        sampleRepo.git("commit", "--message=init");
        sampleRepo.git("tag", "initial");
        sampleRepo.write("src/pkg/Lib.groovy", "package pkg; class Lib {static String CONST = 'modified'}");
        sampleRepo.git("commit", "--all", "--message=modified");
        LibraryConfiguration stuff = new LibraryConfiguration("stuff",
            new SingleSCMSource("", "",
                    new GitSCM(Collections.singletonList(new UserRemoteConfig(sampleRepo.fileUrl(), null, null, null)),
                            Collections.singletonList(new BranchSpec("${library.stuff.version}")),
                            false, Collections.<SubmoduleConfig>emptyList(), null, null, Collections.<GitSCMExtension>emptyList())));
        stuff.setDefaultVersion("master");
        stuff.setImplicit(true);
        GlobalLibraries.get().setLibraries(Collections.singletonList(stuff));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('stuff@master') import pkg.Lib; echo(/using ${Lib.CONST}/)", true));
        r.assertLogContains("using modified", r.buildAndAssertSuccess(p));
        p.setDefinition(new CpsFlowDefinition("@Library('stuff@initial') import pkg.Lib; echo(/using ${Lib.CONST}/)", true));
        r.assertLogContains("using initial", r.buildAndAssertSuccess(p));
        p.setDefinition(new CpsFlowDefinition("@Library('stuff') import pkg.Lib; echo(/using ${Lib.CONST}/)", true));
        r.assertLogContains("using modified", r.buildAndAssertSuccess(p));
        p.setDefinition(new CpsFlowDefinition("echo(/using ${pkg.Lib.CONST}/)", true));
        r.assertLogContains("using modified", r.buildAndAssertSuccess(p));
    }

}
