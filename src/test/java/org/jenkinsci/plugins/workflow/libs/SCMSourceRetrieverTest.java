/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogSet;
import hudson.scm.SCM;
import hudson.slaves.WorkspaceList;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMHeadEvent;
import jenkins.scm.api.SCMHeadObserver;
import jenkins.scm.api.SCMRevision;
import jenkins.scm.api.SCMSource;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceDescriptor;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import static hudson.ExtensionList.lookupSingleton;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;
import org.jvnet.hudson.test.TestExtension;

public class SCMSourceRetrieverTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Issue("JENKINS-40408")
    @Test public void lease() throws Exception {
        sampleRepo.init();
        sampleRepo.write("vars/myecho.groovy", "def call() {echo 'something special'}");
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(
            new LibraryConfiguration("echoing",
                new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)))));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('echoing@master') import myecho; myecho()", true));
        FilePath base = r.jenkins.getWorkspaceFor(p).withSuffix("@libs").child("echoing");
        try (WorkspaceList.Lease lease = r.jenkins.toComputer().getWorkspaceList().acquire(base)) {
            WorkflowRun b = r.buildAndAssertSuccess(p);
            r.assertLogContains("something special", b);
            r.assertLogNotContains("Retrying after 10 seconds", b);
            assertFalse(base.child("vars").exists());
            assertTrue(base.withSuffix("@2").child("vars").exists());
        }
    }

    @Issue("JENKINS-41497")
    @Test public void includeChanges() throws Exception {
        sampleRepo.init();
        sampleRepo.write("vars/myecho.groovy", "def call() {echo 'something special'}");
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(
            new LibraryConfiguration("include_changes",
                new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)))));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('include_changes@master') import myecho; myecho()", true));
        FilePath base = r.jenkins.getWorkspaceFor(p).withSuffix("@libs").child("include_changes");
        try (WorkspaceList.Lease lease = r.jenkins.toComputer().getWorkspaceList().acquire(base)) {
            WorkflowRun a = r.buildAndAssertSuccess(p);
            r.assertLogContains("something special", a);
        }
        sampleRepo.write("vars/myecho.groovy", "def call() {echo 'something even more special'}");
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=shared_library_commit");
        try (WorkspaceList.Lease lease = r.jenkins.toComputer().getWorkspaceList().acquire(base)) {
            WorkflowRun b = r.buildAndAssertSuccess(p);
            List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
            assertEquals(1, changeSets.size());
            ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
            assertEquals(b, changeSet.getRun());
            assertEquals("git", changeSet.getKind());
            Iterator<? extends ChangeLogSet.Entry> iterator = changeSet.iterator();
            ChangeLogSet.Entry entry = iterator.next();
            assertEquals("shared_library_commit", entry.getMsg() );
            r.assertLogContains("something even more special", b);
            r.assertLogNotContains("Retrying after 10 seconds", b);
        }
    }

    @Issue("JENKINS-41497")
    @Test public void dontIncludeChanges() throws Exception {
        sampleRepo.init();
        sampleRepo.write("vars/myecho.groovy", "def call() {echo 'something special'}");
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        LibraryConfiguration lc = new LibraryConfiguration("dont_include_changes", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)));
        lc.setIncludeInChangesets(false);
        GlobalLibraries.get().setLibraries(Collections.singletonList(lc));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('dont_include_changes@master') import myecho; myecho()", true));
        FilePath base = r.jenkins.getWorkspaceFor(p).withSuffix("@libs").child("dont_include_changes");
        try (WorkspaceList.Lease lease = r.jenkins.toComputer().getWorkspaceList().acquire(base)) {
            WorkflowRun a = r.buildAndAssertSuccess(p);
        }
        sampleRepo.write("vars/myecho.groovy", "def call() {echo 'something even more special'}");
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=shared_library_commit");
        try (WorkspaceList.Lease lease = r.jenkins.toComputer().getWorkspaceList().acquire(base)) {
            WorkflowRun b = r.buildAndAssertSuccess(p);
            List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
            assertEquals(0, changeSets.size());
            r.assertLogNotContains("Retrying after 10 seconds", b);
        }
    }

    @Issue("JENKINS-38609")
    @Test public void rootSubPath() throws Exception {
        sampleRepo.init();
        sampleRepo.write("sub/path/vars/myecho.groovy", "def call() {echo 'something special'}");
        sampleRepo.git("add", "sub");
        sampleRepo.git("commit", "--message=init");
        SCMSourceRetriever scm = new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true));
        LibraryConfiguration lc = new LibraryConfiguration("root_sub_path", scm);
        lc.setIncludeInChangesets(false);
        scm.setLibBasePath("sub/path/");
        GlobalLibraries.get().setLibraries(Collections.singletonList(lc));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('root_sub_path@master') import myecho; myecho()", true));
        FilePath base = r.jenkins.getWorkspaceFor(p).withSuffix("@libs").child("root_sub_path");
        try (WorkspaceList.Lease lease = r.jenkins.toComputer().getWorkspaceList().acquire(base)) {
            WorkflowRun a = r.buildAndAssertSuccess(p);
            r.assertLogContains("something special", a);
        }
        sampleRepo.write("sub/path/vars/myecho.groovy", "def call() {echo 'something even more special'}");
        sampleRepo.git("add", "sub");
        sampleRepo.git("commit", "--message=shared_library_commit");
        try (WorkspaceList.Lease lease = r.jenkins.toComputer().getWorkspaceList().acquire(base)) {
            WorkflowRun b = r.buildAndAssertSuccess(p);
            r.assertLogContains("something even more special", b);
        }
    }

    @Issue("JENKINS-38609")
    @Test public void rootSubPathSecurity() throws Exception {
        sampleRepo.init();
        sampleRepo.write("sub/path/vars/myecho.groovy", "def call() {echo 'something special'}");
        sampleRepo.git("add", "sub");
        sampleRepo.git("commit", "--message=init");
        SCMSourceRetriever scm = new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true));
        LibraryConfiguration lc = new LibraryConfiguration("root_sub_path", scm);
        lc.setIncludeInChangesets(false);
        scm.setLibBasePath("sub/path/../path/");
        GlobalLibraries.get().setLibraries(Collections.singletonList(lc));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('root_sub_path@master') import myecho; myecho()", true));
        FilePath base = r.jenkins.getWorkspaceFor(p).withSuffix("@libs").child("root_sub_path");
        WorkflowRun a = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogContains("Double dots in library base path are forbidden", a);
    }

    @Issue("JENKINS-43802")
    @Test public void owner() throws Exception {
        GlobalLibraries.get().setLibraries(Collections.singletonList(
            new LibraryConfiguration("test", new SCMSourceRetriever(new NeedsOwnerSCMSource()))));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('test@abc123') import libVersion; echo(/loaded lib #${libVersion()}/)", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertLogContains("loaded lib #abc123", b);
        r.assertLogContains("Running in retrieve from p", b);
    }
    public static final class NeedsOwnerSCMSource extends SCMSource {
        @Override protected SCMRevision retrieve(String version, TaskListener listener, Item context) throws IOException, InterruptedException {
            if (context == null) {
                throw new AbortException("No context in retrieve!");
            } else {
                listener.getLogger().println("Running in retrieve from " + context.getFullName());
            }
            return new DummySCMRevision(version, new SCMHead("trunk"));
        }
        @Override public SCM build(SCMHead head, SCMRevision revision) {
            String version = ((DummySCMRevision) revision).version;
            return new SingleFileSCM("vars/libVersion.groovy", ("def call() {'" + version + "'}").getBytes());
        }
        private static final class DummySCMRevision extends SCMRevision {
            private final String version;
            DummySCMRevision(String version, SCMHead head) {
                super(head);
                this.version = version;
            }
            @Override public boolean equals(Object obj) {
                return obj instanceof DummySCMRevision && version.equals(((DummySCMRevision) obj).version);
            }
            @Override public int hashCode() {
                return version.hashCode();
            }
        }
        @Override protected void retrieve(SCMSourceCriteria criteria, SCMHeadObserver observer, SCMHeadEvent<?> event, TaskListener listener) throws IOException, InterruptedException {
            throw new IOException("not implemented");
        }
        @TestExtension("owner") public static final class DescriptorImpl extends SCMSourceDescriptor {}
    }

    @Test public void retry() throws Exception {
        WorkflowRun b = prepareRetryTests(new FailingSCMSource());
        r.assertLogContains("Failing 'checkout' on purpose!", b);
        r.assertLogContains("Retrying after 10 seconds", b);
    }

    @Test public void retryDuringFetch() throws Exception {
        WorkflowRun b = prepareRetryTests(new FailingSCMSourceDuringFetch());
        r.assertLogContains("Failing 'fetch' on purpose!", b);
        r.assertLogContains("Retrying after 10 seconds", b);
    }

    private WorkflowRun prepareRetryTests(SCMSource scmSource) throws Exception{
        final SCMSourceRetriever retriever = new SCMSourceRetriever(scmSource);
        final LibraryConfiguration libraryConfiguration = new LibraryConfiguration("retry", retriever);
        final List<LibraryConfiguration> libraries = Collections.singletonList(libraryConfiguration);
        GlobalLibraries.get().setLibraries(libraries);
        final WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        final String script = "@Library('retry@master') import myecho; myecho()";
        final CpsFlowDefinition def = new CpsFlowDefinition(script, true);
        p.setDefinition(def);
        r.jenkins.setScmCheckoutRetryCount(1);

        return r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
    }

    @Test
    public void modernAndLegacyImpls() {
        SCMSourceRetriever.DescriptorImpl modern = lookupSingleton(SCMSourceRetriever.DescriptorImpl.class);

        containsInAnyOrder(modern.getSCMDescriptors(), contains(instanceOf(FakeModernSCM.DescriptorImpl.class)));
        containsInAnyOrder(modern.getSCMDescriptors(), contains(instanceOf(FakeAlsoModernSCM.DescriptorImpl.class)));
        containsInAnyOrder(modern.getSCMDescriptors(), not(contains(instanceOf(BasicSCMSource.DescriptorImpl.class))));
    }
    // Implementation of latest and greatest API
    public static final class FakeModernSCM extends SCMSource {
        @Override protected void retrieve(SCMSourceCriteria c, @NonNull SCMHeadObserver o, SCMHeadEvent<?> e, @NonNull TaskListener l) {}
        @Override public @NonNull SCM build(@NonNull SCMHead head, SCMRevision revision) { return null; }
        @TestExtension("modernAndLegacyImpls") public static final class DescriptorImpl extends SCMSourceDescriptor {}

        @Override
        protected SCMRevision retrieve(@NonNull String thingName, @NonNull TaskListener listener, Item context) throws IOException, InterruptedException {
            return super.retrieve(thingName, listener, context);
        }
    }
    // Implementation of second latest and second greatest API
    public static final class FakeAlsoModernSCM extends SCMSource {
        @Override protected void retrieve(SCMSourceCriteria c, @NonNull SCMHeadObserver o, SCMHeadEvent<?> e, @NonNull TaskListener l) {}
        @Override public @NonNull SCM build(@NonNull SCMHead head, SCMRevision revision) { return null; }
        @TestExtension("modernAndLegacyImpls") public static final class DescriptorImpl extends SCMSourceDescriptor {}

        @Override
        protected SCMRevision retrieve(@NonNull String thingName, @NonNull TaskListener listener) throws IOException, InterruptedException {
            return super.retrieve(thingName, listener);
        }
    }
    // No modern stuff
    public static class BasicSCMSource extends SCMSource {
        @Override protected void retrieve(SCMSourceCriteria c, @NonNull SCMHeadObserver o, SCMHeadEvent<?> e, @NonNull TaskListener l) {}
        @Override public @NonNull SCM build(@NonNull SCMHead head, SCMRevision revision) { return null; }
        @TestExtension("modernAndLegacyImpls") public static final class DescriptorImpl extends SCMSourceDescriptor {}
    }
}
