package org.jenkinsci.plugins.workflow.cps.global;

import hudson.model.Result;
import java.io.NotSerializableException;
import java.util.Collections;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.jenkinsci.plugins.workflow.cps.CpsCompilationErrorsException;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class CompilationErrorsExceptionTest {
    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Issue("JENKINS-40109")
    @Test public void errorInSrcStaticLibrary() throws Exception {
        sampleRepo.init();
        sampleRepo.write("src/test/Test.groovy", "package test; public class Test { bad syntax } ");
        sampleRepo.git("add", "src");
        sampleRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(
                new LibraryConfiguration("badlib", new SCMSourceRetriever(new GitSCMSource(sampleRepo.toString())))));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('badlib@master')\n" +
                "import test.Test;", true));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogNotContains(NotSerializableException.class.getName(), b);
        // We don't care about the type of exception thrown here because it happens outside of CPS execution.
        r.assertLogContains("unexpected token: bad", b);
    }

    @Issue("JENKINS-40109")
    @Test public void errorInSrcDynamicLibrary() throws Exception {
        sampleRepo.init();
        sampleRepo.write("src/test/Test.groovy", "package test; public class Test { bad syntax } ");
        sampleRepo.git("add", "src");
        sampleRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(
                new LibraryConfiguration("badlib", new SCMSourceRetriever(new GitSCMSource(sampleRepo.toString())))));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("def lib = library('badlib@master')\n" +
                "try {\n" +
                "  lib.test.Test.new()\n" +
                "} catch(err) {\n" +
                "  sleep(time: 1, unit: 'MILLISECONDS')\n" + // Force the exception to be persisted.
                "  throw err\n" +
                "}", true));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogNotContains(NotSerializableException.class.getName(), b);
        r.assertLogNotContains(MultipleCompilationErrorsException.class.getName(), b);
        r.assertLogContains(CpsCompilationErrorsException.class.getName(), b);
        r.assertLogContains("unexpected token: bad", b);
    }

    @Issue("JENKINS-40109")
    @Test public void errorInVars() throws Exception {
        sampleRepo.init();
        sampleRepo.write("vars/mymagic.groovy", "def call() { bad, syntax }");
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(
                new LibraryConfiguration("badlib", new SCMSourceRetriever(new GitSCMSource(sampleRepo.toString())))));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        // The `@Library` annotation and the `library` step have the same behavior for code in vars.
        p.setDefinition(new CpsFlowDefinition("@Library('badlib@master') _\n" +
                "try {\n" +
                "  mymagic()\n" +
                "} catch(err) {\n" +
                "  sleep(time: 1, unit: 'MILLISECONDS')\n" + // Force the exception to be persisted.
                "  throw err\n" +
                "}", true));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogNotContains(NotSerializableException.class.getName(), b);
        r.assertLogNotContains(MultipleCompilationErrorsException.class.getName(), b);
        r.assertLogContains(CpsCompilationErrorsException.class.getName(), b);
        r.assertLogContains("unexpected token: bad", b);
    }

}
