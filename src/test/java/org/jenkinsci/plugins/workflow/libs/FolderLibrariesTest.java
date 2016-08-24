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
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTextArea;
import com.google.common.collect.ImmutableMap;
import hudson.plugins.git.GitSCM;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.impl.SingleSCMSource;
import static org.hamcrest.CoreMatchers.*;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayAction;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class FolderLibrariesTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo1 = new GitSampleRepoRule();
    @Rule public GitSampleRepoRule sampleRepo2 = new GitSampleRepoRule();

    @Test public void configRoundtrip() throws Exception {
        Folder d = r.jenkins.createProject(Folder.class, "d");
        r.configRoundtrip(d);
        assertNull(d.getProperties().get(FolderLibraries.class));
        LibraryConfiguration foo = new LibraryConfiguration("foo", new GitSCMSource("foo", "https://nowhere.net/foo.git", "", "*", "", true));
        LibraryConfiguration bar = new LibraryConfiguration("bar", new SingleSCMSource("bar", "bar", new GitSCM("https://nowhere.net/bar.git")));
        bar.setDefaultVersion("master");
        bar.setImplicit(true);
        bar.setAllowVersionOverride(false);
        d.getProperties().add(new FolderLibraries(Arrays.asList(foo, bar)));
        r.configRoundtrip(d);
        FolderLibraries prop = d.getProperties().get(FolderLibraries.class);
        assertNotNull(prop);
        List<LibraryConfiguration> libs = prop.getLibraries();
        r.assertEqualDataBoundBeans(Arrays.asList(foo, bar), libs);
    }

    @Test public void registration() throws Exception {
        sampleRepo1.init();
        sampleRepo1.write("src/generic/Lib.groovy", "package generic; class Lib {static String CONST = 'generic'}");
        sampleRepo1.git("add", "src");
        sampleRepo1.git("commit", "--message=init");
        sampleRepo2.init();
        sampleRepo2.write("src/specific/Lib.groovy", "package specific; class Lib {static String CONST = 'specific'}");
        sampleRepo2.git("add", "src");
        sampleRepo2.git("commit", "--message=init");
        Folder d1 = r.jenkins.createProject(Folder.class, "d1");
        LibraryConfiguration generic = new LibraryConfiguration("generic", new SingleSCMSource("", "", new GitSCM(sampleRepo1.fileUrl())));
        generic.setDefaultVersion("<ignored>");
        d1.getProperties().add(new FolderLibraries(Collections.singletonList(generic)));
        Folder d2 = d1.createProject(Folder.class, "d2");
        LibraryConfiguration specific = new LibraryConfiguration("specific", new SingleSCMSource("", "", new GitSCM(sampleRepo2.fileUrl())));
        specific.setDefaultVersion("<ignored>");
        d2.getProperties().add(new FolderLibraries(Collections.singletonList(specific)));
        WorkflowJob p = d2.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('generic') import generic.Lib as Generic; @Library('specific') import specific.Lib as Specific; echo(/from the ${Generic.CONST} to the ${Specific.CONST}/)", true));
        r.assertLogContains("from the generic to the specific", r.buildAndAssertSuccess(p));
    }

    @Issue("JENKINS-32400") // one approach
    @Test public void loadVarForFolder() throws Exception {
        sampleRepo1.init();
        sampleRepo1.write("src/stuff/Lib.groovy", "package stuff; class Lib {static String CONST = 'stuff'}");
        sampleRepo1.write("vars/p.groovy", "def call() {echo(/found some ${stuff.Lib.CONST}/)}");
        sampleRepo1.write("vars/p.txt", "Handling of <p>.");
        sampleRepo1.git("add", "src", "vars");
        sampleRepo1.git("commit", "--message=init");
        sampleRepo2.init();
        Folder d = r.jenkins.createProject(Folder.class, "d");
        d.getProperties().add(new FolderLibraries(Collections.singletonList(new LibraryConfiguration("stuff", new GitSCMSource(null, sampleRepo1.toString(), "", "*", "", true)))));
        WorkflowJob p = d.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('stuff@master') _; p()", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertLogContains("found some stuff", b);
        assertNotNull(GlobalVariable.byName("p", b));
        JenkinsRule.WebClient wc = r.createWebClient();
        String html = wc.getPage(p, Snippetizer.ACTION_URL + "/globals").getWebResponse().getContentAsString();
        assertThat(html, containsString("Handling of &lt;p&gt;."));
        html = wc.goTo(Snippetizer.ACTION_URL + "/globals").getWebResponse().getContentAsString();
        assertThat(html, not(containsString("Handling of &lt;p&gt;.")));
    }

    @Test public void replay() throws Exception {
        sampleRepo1.init();
        String somethingCode = "package pkg; class Something {@NonCPS String toString() {'the first version'}}";
        sampleRepo1.write("src/pkg/Something.groovy", somethingCode);
        String varCode = "def call() {echo(/initially running ${new pkg.Something()}/)}";
        sampleRepo1.write("vars/var.groovy", varCode);
        sampleRepo1.git("add", "src", "vars");
        sampleRepo1.git("commit", "--message=init");
        Folder d = r.jenkins.createProject(Folder.class, "d");
        d.getProperties().add(new FolderLibraries(Collections.singletonList(new LibraryConfiguration("stuff", new GitSCMSource(null, sampleRepo1.toString(), "", "*", "", true)))));
        WorkflowJob p = d.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('stuff@master') _ = var()", true));
        WorkflowRun b1 = r.buildAndAssertSuccess(p);
        r.assertLogContains("initially running the first version", b1);
        ReplayAction ra = b1.getAction(ReplayAction.class);
        assertEquals(ImmutableMap.of("pkg.Something", somethingCode, "var", varCode), ra.getOriginalLoadedScripts());
        String varCode2 = varCode.replace("initially", "subsequently");
        WorkflowRun b2 = r.assertBuildStatusSuccess((WorkflowRun) ra.run(ra.getOriginalScript(), ImmutableMap.of("pkg.Something", somethingCode, "var", varCode2)).get());
        r.assertLogContains("subsequently running the first version", b2);
        ra = b2.getAction(ReplayAction.class);
        assertEquals(ImmutableMap.of("pkg.Something", somethingCode, "var", varCode2), ra.getOriginalLoadedScripts());
        String somethingCode2 = somethingCode.replace("first", "second");
        WorkflowRun b3;
        { // to bypass the UI: b3 = (WorkflowRun) ra.run(ra.getOriginalScript(), ImmutableMap.of("pkg.Something", somethingCode2, "var", varCode2)).get();
            HtmlPage page = r.createWebClient().getPage(b2, ra.getUrlName());
            HtmlForm form = page.getFormByName("config");
            HtmlTextArea text = form.getTextAreaByName("_.pkg_Something");
            assertEquals(somethingCode, text.getText());
            text.setText(somethingCode2);
            HtmlPage redirect = r.submit(form);
            assertEquals(p.getAbsoluteUrl(), redirect.getUrl().toString());
            r.waitUntilNoActivity();
            b3 = p.getBuildByNumber(3);
            assertNotNull(b3);
        }
        r.assertLogContains("subsequently running the second version", r.assertBuildStatusSuccess(b3));
    }

    // TODO test replay of `load`ed scripts as well as libraries

    // TODO test override of global or top folder scope

}
