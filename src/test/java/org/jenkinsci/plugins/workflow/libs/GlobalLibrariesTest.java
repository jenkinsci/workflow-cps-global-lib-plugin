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

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import hudson.model.Item;
import hudson.model.View;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.impl.subversion.SubversionSCMSource;
import static org.hamcrest.Matchers.*;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class GlobalLibrariesTest {

    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Test public void configRoundtrip() throws Exception {
        r.configRoundtrip();
        GlobalLibraries gl = GlobalLibraries.get();
        assertEquals(Collections.emptyList(), gl.getLibraries());
        LibraryConfiguration foo = new LibraryConfiguration("foo", new SCMSourceRetriever(new SubversionSCMSource("foo", "https://phony.jenkins.io/foo/")));
        LibraryConfiguration bar = new LibraryConfiguration("bar", new SCMSourceRetriever(new GitSCMSource(null, "https://phony.jenkins.io/bar.git", "", "origin", "+refs/heads/*:refs/remotes/origin/*", "*", "", true)));
        bar.setDefaultVersion("master");
        bar.setImplicit(true);
        bar.setAllowVersionOverride(false);
        gl.setLibraries(Arrays.asList(foo, bar));
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
            grant(Jenkins.ADMINISTER).everywhere().to("alice"). // includes RUN_SCRIPTS and all else
            grantWithoutImplication(Jenkins.ADMINISTER).everywhere().to("bob"). // but not RUN_SCRIPTS
            grant(Jenkins.READ, Item.READ, View.READ).everywhere().to("bob"));
        HtmlPage configurePage = r.createWebClient().login("alice").goTo("configure");
        assertThat(configurePage.getWebResponse().getContentAsString(), containsString("https://phony.jenkins.io/bar.git"));
        r.submit(configurePage.getFormByName("config")); // JenkinsRule.configRoundtrip expanded to include login
        List<LibraryConfiguration> libs = gl.getLibraries();
        r.assertEqualDataBoundBeans(Arrays.asList(foo, bar), libs);
        configurePage = r.createWebClient().login("bob").goTo("configure");
        assertThat(configurePage.getWebResponse().getContentAsString(), not(containsString("https://phony.jenkins.io/bar.git")));
        r.submit(configurePage.getFormByName("config"));
        libs = gl.getLibraries();
        r.assertEqualDataBoundBeans(Arrays.asList(foo, bar), libs);
    }

    @Issue("SECURITY-1422")
    @Test public void checkDefaultVersionRestricted() throws Exception {
        sampleRepo.init();
        sampleRepo.write("vars/myecho.groovy", "def call() {echo 'something special'}");
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        MockAuthorizationStrategy s = new MockAuthorizationStrategy()
            .grant(Jenkins.READ).everywhere().toEveryone()
            .grant(Jenkins.RUN_SCRIPTS).everywhere().to("admin");
        r.jenkins.setAuthorizationStrategy(s);
        LibraryConfiguration foo = new LibraryConfiguration("foo", new SCMSourceRetriever(new GitSCMSource(sampleRepo.toString())));
        GlobalLibraries.get().setLibraries(Arrays.asList(foo));
        JenkinsRule.WebClient wc = r.createWebClient();
        wc.setThrowExceptionOnFailingStatusCode(false);
        WebRequest req = new WebRequest(new URL(wc.getContextPath() + "/descriptorByName/" +
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

}
