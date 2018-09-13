package org.jenkinsci.plugins.workflow.cps.global;

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.Run;
import hudson.plugins.git.GitSCM;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMRetriever;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;

@Issue("JENKINS-40109")
public class CompilationErrorExceptionTest extends Assert {
    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Test public void catchSyntaxError() throws Exception {
        sampleRepo.init();
        sampleRepo.write("vars/mymagic.groovy", "def call() { echo([a:'alpha' b:'beta']) } //syntax error: missing comma");
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(new LibraryConfiguration("badlib", new SCMSourceRetriever(new GitSCMSource(sampleRepo.toString())))));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('badlib@master') _; try { mymagic(); echo 'why are we here?' } catch(err) { echo 'forcing CPS serialization...'; sleep 1; echo 'did we die?' }", true));
        Run run =  r.buildAndAssertSuccess(p);
        r.assertLogNotContains("why are we here?", run);
        r.assertLogContains("did we die?", run);
    }
}
