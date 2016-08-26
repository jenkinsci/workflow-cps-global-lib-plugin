package org.jenkinsci.plugins.workflow.cps.global.loader;

import com.google.inject.Inject;
import java.io.File;
import java.net.URL;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RestartableJenkinsRule;

/**
 * Integration test of script parsing.
 *
 * @author Philipp Lang
 */
public class GitLoaderJenkinsTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Inject
    Jenkins jenkins;

    /**
     * Tests the extension of the classpath with individiual scripts.
     */
    @Test
    public void testExtendingClassPathWithGit() {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob job = jenkins.createProject(WorkflowJob.class, "myproject");

                // disable security and csrf to prevent statuscode 403
                story.j.jenkins.disableSecurity();
                story.j.jenkins.setCrumbIssuer(null);

                // prepare git repository with sample groovy scripts
                URL gitRepositoryUrl = new URL(story.j.getURL(), "workflowLibs.git");
                pushResourcesToGit(gitRepositoryUrl);

                // set sample pipeline script
                job.setDefinition(new CpsFlowDefinition(""
                        + "//#git " + gitRepositoryUrl.toExternalForm() + " master\n"
                        + "new org.jenkinsci.plugins.workflow.cps.global.loader.HelloClass().sayHello()\n", true));

                // get the build going
                WorkflowRun b = job.scheduleBuild2(0).getStartCondition().get();
                CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

                // wait until completion
                while (b.isBuilding()) {
                    e.waitForSuspension();
                }

                // assertion
                story.j.assertBuildStatusSuccess(b);
                story.j.assertLogContains("hello from HelloClass", b);
            }
        });
    }

    private void pushResourcesToGit(URL gitUrl) throws Throwable {
        File targetFolder = tmp.newFolder();
        Git git = Git.cloneRepository()
                .setURI(gitUrl.toExternalForm())
                .setDirectory(targetFolder)
                .setBranch("master")
                .call();

        FileUtils.copyDirectory(new File("src/test/resources"), targetFolder);

        git.add().addFilepattern(".").call();
        git.commit().setAll(true).setMessage("initial commit").call();
        git.push().call();
        git.close();
    }
}
