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

package org.jenkinsci.plugins.workflow.cps.global;

import hudson.model.Result;
import java.io.File;
import javax.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Test;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

@Issue("JENKINS-26192")
public class GrapeTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();
    @Inject private WorkflowLibRepository repo;

    @Test public void useBinary() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                FileUtils.write(new File(repo.workspace, "src/pkg/Lists.groovy"),
                    "package pkg\n" +
                    "@Grab('commons-primitives:commons-primitives:1.0')\n" +
                    "import org.apache.commons.collections.primitives.ArrayIntList\n" +
                    "static def arrayInt(script) {\n" +
                    "  script.semaphore 'wait'\n" +
                    "  new ArrayIntList()\n" +
                    "}");
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("echo(/got ${pkg.Lists.arrayInt(this)}/)", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                SemaphoreStep.success("wait/1", null);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                story.j.assertLogContains("got []", b);
            }
        });
    }

    @Test public void var() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                FileUtils.write(new File(repo.workspace, "vars/one.groovy"),
                    "@Grab('commons-primitives:commons-primitives:1.0')\n" +
                    "import org.apache.commons.collections.primitives.ArrayIntList\n" +
                    "def call() {\n" +
                    "  def list = new ArrayIntList()\n" +
                    "  list.incrModCount()\n" +
                    "  list.modCount\n" +
                    "}");
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("echo(/${one()} + ${one()} = ${one() + one()}/)", true));
                story.j.assertLogContains("1 + 1 = 2", story.j.buildAndAssertSuccess(p));
            }
        });
    }

    // TODO test transitive dependencies; need to find something in Central which has a dependency not in this plugin’s test classpath and which could be used easily from a script

    @Test public void nonexistentLibrary() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                FileUtils.write(new File(repo.workspace, "src/pkg/X.groovy"),
                    "package pkg\n" +
                    "@Grab('net.nowhere:nonexistent:99.9')\n" +
                    "static def run() {}");
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("pkg.X.run()", true));
                story.j.assertLogContains("net.nowhere", story.j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
            }
        });
    }

    @Test public void nonexistentImport() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                FileUtils.write(new File(repo.workspace, "src/pkg/X.groovy"),
                    "package pkg\n" +
                    "@Grab('commons-primitives:commons-primitives:1.0')\n" +
                    "import net.nowhere.Nonexistent\n" +
                    "static def run() {new Nonexistent()}");
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("pkg.X.run()", true));
                story.j.assertLogContains("net.nowhere.Nonexistent", story.j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
            }
        });
    }

    // TODO test alternate Maven repositories

    @Ignore("TODO MissingMethodException: No signature of method: static com.google.common.base.CharMatcher.whitespace() is applicable for argument types: () values: []")
    @Test public void overrideCoreLibraries() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                FileUtils.write(new File(repo.workspace, "src/pkg/Strings.groovy"),
                    "package pkg\n" +
                    "@Grab('com.google.guava:guava:19.0')\n" + // 11.0.1 from core has only WHITESPACE constant
                    "import com.google.common.base.CharMatcher\n" +
                    "static def hasWhitespace(text) {\n" +
                    "  CharMatcher.whitespace().matchesAnyOf(text)\n" +
                    "}");
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("echo(/checking ${pkg.Strings.hasWhitespace('hello world')}/)", true));
                story.j.assertLogContains("checking true", story.j.buildAndAssertSuccess(p));
            }
        });
    }

    @Ignore("TODO fails on CI and inside a Docker container, though for different reasons: `download failed` vs. `/var/maven/.groovy/grapes/resolved-caller-all-caller-working61.xml (No such file or directory)`; and a test-scoped dep on docker-workflow:1.7 does not help")
    @Test public void useSource() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                FileUtils.write(new File(repo.workspace, "src/pkg/Dokker.groovy"),
                    "package pkg\n" +
                    "@Grapes([@Grab('org.jenkins-ci.plugins:docker-workflow:1.7'), @Grab('org.jenkins-ci.plugins:docker-commons:1.3.1')])\n" +
                    "import org.jenkinsci.plugins.docker.workflow.Docker\n" +
                    "static def stuff(script, body) {\n" +
                    "  new Docker(script).node {body()}\n" +
                    "}");
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("pkg.Dokker.stuff(this) {semaphore 'wait'; writeFile file: 'x', text: 'CPS-transformed'; echo(/ran ${readFile 'x'}/)}", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                SemaphoreStep.success("wait/1", null);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                story.j.assertLogContains("ran CPS-transformed", b);
            }
        });
    }

    @Test public void outsideLibrary() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "@Grab('commons-primitives:commons-primitives:1.0')\n" +
                    "import org.apache.commons.collections.primitives.ArrayIntList\n" +
                    "echo(/got ${new ArrayIntList()}/)", false));
                story.j.assertLogContains("got []", story.j.buildAndAssertSuccess(p));
            }
        });
    }

    @Test public void outsideLibrarySandbox() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "@Grab('commons-primitives:commons-primitives:1.0')\n" +
                    "import org.apache.commons.collections.primitives.ArrayIntList\n" +
                    "new ArrayIntList()", true));
                // Even assuming signature approvals, we do not want to allow Grape to be used from sandboxed scripts.
                ScriptApproval.get().approveSignature("new org.apache.commons.collections.primitives.ArrayIntList");
                story.j.assertLogContains("WorkflowScript: 1: unable to resolve class org.apache.commons.collections.primitives.ArrayIntList", story.j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
            }
        });
    }

}
