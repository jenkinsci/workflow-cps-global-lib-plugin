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
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTextArea;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import com.google.common.collect.ImmutableMap;
import hudson.model.Item;
import hudson.model.Result;
import hudson.plugins.git.GitSCM;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.impl.subversion.SubversionSCMSource;
import static org.hamcrest.CoreMatchers.*;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;
import org.jenkinsci.plugins.workflow.cps.Snippetizer;
import org.jenkinsci.plugins.workflow.cps.global.GrapeTest;
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
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class FolderLibrariesTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo1 = new GitSampleRepoRule();
    @Rule public GitSampleRepoRule sampleRepo2 = new GitSampleRepoRule();

    @Test public void configRoundtrip() throws Exception {
        Folder d = r.jenkins.createProject(Folder.class, "d");
        r.configRoundtrip(d);
        assertNull(d.getProperties().get(FolderLibraries.class));
        LibraryConfiguration foo = new LibraryConfiguration("foo", new SCMSourceRetriever(new SubversionSCMSource("foo", "https://phony.jenkins.io/foo/")));
        LibraryConfiguration bar = new LibraryConfiguration("bar", new SCMRetriever(new GitSCM("https://phony.jenkins.io/bar.git")));
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
        LibraryConfiguration generic = new LibraryConfiguration("generic", new SCMRetriever(new GitSCM(sampleRepo1.fileUrl())));
        generic.setDefaultVersion("<ignored>");
        d1.getProperties().add(new FolderLibraries(Collections.singletonList(generic)));
        Folder d2 = d1.createProject(Folder.class, "d2");
        LibraryConfiguration specific = new LibraryConfiguration("specific", new SCMRetriever(new GitSCM(sampleRepo2.fileUrl())));
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
        d.getProperties().add(new FolderLibraries(Collections.singletonList(new LibraryConfiguration("stuff", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo1.toString(), "", "*", "", true))))));
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
        d.getProperties().add(new FolderLibraries(Collections.singletonList(new LibraryConfiguration("stuff", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo1.toString(), "", "*", "", true))))));
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

    /** @see GrapeTest#outsideLibrarySandbox */
    @Test public void noGrape() throws Exception {
        sampleRepo1.init();
        sampleRepo1.write("src/pkg/Wrapper.groovy",
            "package pkg\n" +
            "@Grab('commons-primitives:commons-primitives:1.0')\n" +
            "import org.apache.commons.collections.primitives.ArrayIntList\n" +
            "class Wrapper {static def list() {new ArrayIntList()}}");
        sampleRepo1.git("add", "src");
        sampleRepo1.git("commit", "--message=init");
        Folder d = r.jenkins.createProject(Folder.class, "d");
        d.getProperties().add(new FolderLibraries(Collections.singletonList(new LibraryConfiguration("grape", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo1.toString(), "", "*", "", true))))));
        WorkflowJob p = d.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('grape@master') import pkg.Wrapper; echo(/should not have been able to run ${pkg.Wrapper.list()}/)", true));
        ScriptApproval.get().approveSignature("new org.apache.commons.collections.primitives.ArrayIntList");
        r.assertLogContains("Wrapper.groovy: 2: unable to resolve class org.apache.commons.collections.primitives.ArrayIntList", r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
    }

    @Issue("JENKINS-43019")
    @Test public void classCastException() throws Exception {
        sampleRepo1.init();
        sampleRepo1.write("src/pkg/Obj.groovy", "package pkg; public class Obj implements Serializable {public Obj() {}}");
        sampleRepo1.write("vars/objs.groovy", "@groovy.transform.Field final pkg.Obj OBJ = new pkg.Obj()");
        sampleRepo1.git("add", "src", "vars");
        sampleRepo1.git("commit", "--message=init");
        sampleRepo2.init();
        ScriptApproval.get().approveSignature("method java.lang.Class getClassLoader");
        ScriptApproval.get().approveSignature("method java.lang.ClassLoader getParent");
        ScriptApproval.get().approveSignature("method java.lang.Class getProtectionDomain");
        ScriptApproval.get().approveSignature("method java.security.ProtectionDomain getCodeSource");
        ScriptApproval.get().approveSignature("method java.security.CodeSource getLocation");
        String script =
            "def descr(c) {/${c.classLoader} < ${c.classLoader.parent} @ ${c.protectionDomain.codeSource?.location}/}\n" +
            "echo(/this: ${descr(this.getClass())} Obj: ${descr(pkg.Obj)} objs: ${descr(objs.getClass())} objs.OBJ: ${descr(objs.OBJ.getClass())}/)\n" +
            "pkg.Obj obj = objs.OBJ\n" +
            "echo(/loaded $obj/)";
        { // Trusted lib (control):
            GlobalLibraries.get().setLibraries(Collections.singletonList(new LibraryConfiguration("global-objs", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo1.toString(), "", "*", "", true)))));
            WorkflowJob p = r.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("@Library('global-objs@master') _; " + script, true));
            r.assertLogContains("loaded pkg.Obj@", r.buildAndAssertSuccess(p));
        }
        { // Untrusted (test):
            Folder d = r.jenkins.createProject(Folder.class, "d");
            d.getProperties().add(new FolderLibraries(Collections.singletonList(new LibraryConfiguration("folder-objs", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo1.toString(), "", "*", "", true))))));
            WorkflowJob p = d.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("@Library('folder-objs@master') _; " + script, true));
            for (int i = 0; i < 50; i++) {
                r.assertLogContains("loaded pkg.Obj@", r.buildAndAssertSuccess(p));
            }
        }
    }

    @Issue("SECURITY-1422")
    @Test public void checkDefaultVersionRestricted() throws Exception {
        sampleRepo1.init();
        sampleRepo1.write("vars/myecho.groovy", "def call() {echo 'something special'}");
        sampleRepo1.git("add", "vars");
        sampleRepo1.git("commit", "--message=init");
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        MockAuthorizationStrategy s = new MockAuthorizationStrategy()
                .grant(Jenkins.READ).everywhere().toEveryone()
                .grant(Item.READ).everywhere().toEveryone()
                .grant(Item.CONFIGURE).everywhere().to("admin");
        r.jenkins.setAuthorizationStrategy(s);
        LibraryConfiguration foo = new LibraryConfiguration("foo", new SCMSourceRetriever(new GitSCMSource(sampleRepo1.toString())));
        Folder f = r.jenkins.createProject(Folder.class, "f");
        f.getProperties().add(new FolderLibraries(Arrays.asList(foo)));
        JenkinsRule.WebClient wc = r.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        WebRequest req = new WebRequest(new URL(wc.getContextPath() + f.getUrl() + "/descriptorByName/" +
                LibraryConfiguration.class.getName() + "/checkDefaultVersion"), HttpMethod.POST);
        req.setRequestParameters(Arrays.asList(
                new NameValuePair("name", "foo"),
                new NameValuePair("defaultVersion", "master"),
                new NameValuePair("value", "master"),
                new NameValuePair("implicit", "false"),
                new NameValuePair("allowVersionOverride", "true")));
        wc.addCrumb(req);
        wc.login("user", "user");
        assertThat(wc.getPage(req).getWebResponse().getContentAsString(),
                containsString("Cannot validate default version until after saving and reconfiguring"));
        wc.login("admin", "admin");
        assertThat(wc.getPage(req).getWebResponse().getContentAsString(),
                containsString("Currently maps to revision"));
    }

    // TODO test replay of `load`ed scripts as well as libraries

    // TODO test override of global or top folder scope

}
