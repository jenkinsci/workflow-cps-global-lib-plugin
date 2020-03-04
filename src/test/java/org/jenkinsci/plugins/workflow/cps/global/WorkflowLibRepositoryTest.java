package org.jenkinsci.plugins.workflow.cps.global;

import hudson.model.Result;
import java.io.File;
import java.util.Arrays;

import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import javax.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class WorkflowLibRepositoryTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Inject
    Jenkins jenkins;

    @Inject
    WorkflowLibRepository repo;

    @Inject
    UserDefinedGlobalVariableList uvl;

    /**
     * Have some global libs
     */
    @Test
    public void globalLib() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                File dir = new File(repo.workspace,"src/foo");
                dir.mkdirs();

                FileUtils.write(new File(dir, "Foo.groovy"),
                        "package foo;\n" +
                        "def answer() {\n" +
                        "  echo 'running the answer method'\n" +
                        "  semaphore 'watch'\n" +
                        "  return 42;\n" +
                        "}");

                dir = new File(repo.workspace,"src/main/groovy/bar");
                dir.mkdirs();

                FileUtils.write(new File(dir, "Bar.groovy"),
                        "package bar;\n" +
                        "def answer() {\n" +
                        "  echo 'running the bar answer method'\n" +
                        "  semaphore 'watchBar'\n" +
                        "  return 0;\n" +
                        "}");

                WorkflowJob p = jenkins.createProject(WorkflowJob.class, "p");

                p.setDefinition(new CpsFlowDefinition(
                        "o=new foo.Foo().answer()\n" +
                        "println 'o=' + o;",
                        true));

                // get the build going
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

                // wait until the executor gets assigned and the execution pauses
                SemaphoreStep.waitForStart("watch/1", b);
                e.waitForSuspension();
                assertTrue(JenkinsRule.getLog(b), b.isBuilding());
                story.j.assertLogContains("running the answer method", b);

                p = jenkins.createProject(WorkflowJob.class, "pBar");
                p.setDefinition(new CpsFlowDefinition(
                        "another=new bar.Bar().answer()\n" +
                        "println 'another=' + another;",
                        true));

                // get the build going
                b = p.scheduleBuild2(0).getStartCondition().get();
                e = (CpsFlowExecution) b.getExecutionPromise().get();
                // wait until the executor gets assigned and the execution pauses
                SemaphoreStep.waitForStart("watchBar/1", b);
                e.waitForSuspension();
                assertTrue(JenkinsRule.getLog(b), b.isBuilding());
                story.j.assertLogContains("running the bar answer method", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                CpsFlowExecution e = (CpsFlowExecution) b.getExecutionPromise().get();

                // resume from where it left off
                SemaphoreStep.success("watch/1", null);

                // wait until the completion
                while (b.isBuilding())
                    e.waitForSuspension();

                story.j.assertBuildStatusSuccess(b);
                story.j.assertLogContains("o=42", b);

                p = jenkins.getItemByFullName("pBar", WorkflowJob.class);
                b = p.getBuildByNumber(1);
                e = (CpsFlowExecution) b.getExecutionPromise().get();

                SemaphoreStep.success("watchBar/1", null);
                // wait until the completion
                while (b.isBuilding())
                    e.waitForSuspension();

                story.j.assertBuildStatusSuccess(b);
                story.j.assertLogContains("another=0", b);
            }
        });
    }

    /**
     * User can define global variables.
     */
    @Test
    public void userDefinedGlobalVariable() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                File vars = new File(repo.workspace, UserDefinedGlobalVariable.PREFIX);
                vars.mkdirs();
                FileUtils.writeStringToFile(new File(vars, "acmeVar.groovy"), StringUtils.join(Arrays.asList(
                        "def hello(name) {echo \"Hello ${name}\"}",
                        "def foo(x) { this.x = x+'-set'; }",
                        "def bar() { return x+'-get' }")
                        , "\n"));
                FileUtils.writeStringToFile(new File(vars, "acmeFunc.groovy"), StringUtils.join(Arrays.asList(
                        "def call(a,b) { echo \"call($a,$b)\" }")
                        , "\n"));
                FileUtils.writeStringToFile(new File(vars, "acmeBody.groovy"), StringUtils.join(Arrays.asList(
                        "def call(body) { ",
                        "  def config = [:]",
                        "  body.resolveStrategy = Closure.DELEGATE_FIRST",
                        "  body.delegate = config",
                        "  body()",
                        "  echo 'title was '+config.title",
                        "}")
                        , "\n"));
                FileUtils.writeStringToFile(new File(vars, "acmeClass.groovy"), StringUtils.join(Arrays.asList(
                        "@groovy.transform.Field int answer = 42")
                        , "\n"));

                // simulate the effect of push
                uvl.rebuild();

                WorkflowJob p = jenkins.createProject(WorkflowJob.class, "p");

                p.setDefinition(new CpsFlowDefinition(
                        "acmeVar.hello('Pipeline');" +
                        "acmeVar.foo('seed');" +
                        "echo '['+acmeVar.bar()+']';"+
                        "acmeFunc(1,2);"+
                        "acmeBody { title = 'yolo' };"+
                        "echo \"the answer is ${acmeClass.answer}\"",
                    true));

                // build this workflow
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));

                story.j.assertLogContains("Hello Pipeline", b);
                story.j.assertLogContains("[seed-set-get]", b);
                story.j.assertLogContains("call(1,2)", b);
                story.j.assertLogContains("title was yolo", b);
                story.j.assertLogContains("the answer is 42", b);
            }
        });
    }

    @Issue("JENKINS-34517")
    @Test public void restartGlobalVar() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                File vars = new File(repo.workspace, UserDefinedGlobalVariable.PREFIX);
                vars.mkdirs();
                FileUtils.writeStringToFile(new File(vars, "block.groovy"), "def call(body) {node {body()}}");
                uvl.rebuild();
                WorkflowJob p = jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("block {semaphore 'wait'}", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                SemaphoreStep.success("wait/1", null);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
            }
        });
    }

    /** Global libraries should run outside the sandbox, regardless of whether the caller is sandboxed. */
    @Issue("JENKINS-34650")
    @Test public void sandbox() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                File f = new File(jenkins.getRootDir(), "f");   // marker file to write from Pipeline Script

                FileUtils.write(new File(new File(repo.workspace, "src/pkg"), "Privileged.groovy"),
                    "package pkg\n" +
                    "class Privileged implements Serializable {\n" +
                    "  void write(String content) {\n" +
                    "    new File(jenkins.model.Jenkins.instance.rootDir, 'f').text = content\n" +
                    "  }\n" +
                    "  void callback(Closure body) {\n" +
                    "    body()\n" +
                    "  }\n" +
                    "}");
                FileUtils.write(new File(new File(repo.workspace, UserDefinedGlobalVariable.PREFIX), "record.groovy"),
                    "def call() {new pkg.Privileged().write(jenkins.model.Jenkins.instance.systemMessage)}");
                WorkflowJob p = jenkins.createProject(WorkflowJob.class, "p");

                p.setDefinition(new CpsFlowDefinition("new pkg.Privileged().write('direct-false')", false));
                story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                assertEquals("direct-false", FileUtils.readFileToString(f));

                jenkins.setSystemMessage("indirect-false");
                p.setDefinition(new CpsFlowDefinition("record()", false));
                story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                assertEquals("indirect-false", FileUtils.readFileToString(f));

                p.setDefinition(new CpsFlowDefinition("new pkg.Privileged().callback({jenkins.model.Jenkins.instance.systemMessage = 'callback-false'})", false));
                story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                assertEquals("callback-false", jenkins.getSystemMessage());

                p.setDefinition(new CpsFlowDefinition("new pkg.Privileged().write('direct-true')", true));
                story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                assertEquals("direct-true", FileUtils.readFileToString(f));

                jenkins.setSystemMessage("indirect-true");
                p.setDefinition(new CpsFlowDefinition("record()", true));
                story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                assertEquals("indirect-true", FileUtils.readFileToString(f));

                jenkins.setSystemMessage("untouched");
                p.setDefinition(new CpsFlowDefinition("new pkg.Privileged().callback({jenkins.model.Jenkins.instance.systemMessage = 'callback-true'})", true));
                story.j.assertLogContains("RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", story.j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get()));
                assertEquals("untouched", jenkins.getSystemMessage());
            }
        });
    }

}
