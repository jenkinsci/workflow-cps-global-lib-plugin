/*
 * The MIT License
 *
 * Copyright 2022 CloudBees, Inc.
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

import hudson.ExtensionList;
import hudson.FilePath;
import hudson.util.StreamTaskListener;
import java.io.File;
import java.time.ZonedDateTime;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.io.FileMatchers.anExistingDirectory;
import static org.hamcrest.io.FileMatchers.anExistingFile;

public class LibraryCachingCleanupTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();
    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Test
    public void smokes() throws Throwable {
        sampleRepo.init();
        sampleRepo.write("vars/foo.groovy", "def call() { echo 'foo' }");
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        LibraryConfiguration config = new LibraryConfiguration("library",
                new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)));
        config.setDefaultVersion("master");
        config.setImplicit(true);
        config.setCachingConfiguration(new LibraryCachingConfiguration(30, null));
        GlobalLibraries.get().getLibraries().add(config);
        // Run build and check that cache gets created.
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("foo()", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        LibrariesAction action = b.getAction(LibrariesAction.class);
        LibraryRecord record = action.getLibraries().get(0);
        FilePath cache = LibraryCachingConfiguration.getGlobalLibrariesCacheDir().child(record.getDirectoryName());
        assertThat(new File(cache.getRemote()), anExistingDirectory());
        // Run LibraryCachingCleanup and show that cache is not deleted.
        ExtensionList.lookupSingleton(LibraryCachingCleanup.class).execute(StreamTaskListener.fromStderr());
        assertThat(new File(cache.getRemote()), anExistingDirectory());
        assertThat(new File(cache.withSuffix("-name.txt").getRemote()), anExistingFile());
        // Run LibraryCachingCleanup after modifying LAST_READ_FILE to be an old date and and show that cache is deleted.
        long oldMillis = ZonedDateTime.now().minusDays(LibraryCachingCleanup.EXPIRE_AFTER_READ_DAYS + 1).toInstant().toEpochMilli();
        cache.child(LibraryCachingConfiguration.LAST_READ_FILE).touch(oldMillis);
        ExtensionList.lookupSingleton(LibraryCachingCleanup.class).execute(StreamTaskListener.fromStderr());
        assertThat(new File(cache.getRemote()), not(anExistingDirectory()));
        assertThat(new File(cache.withSuffix("-name.txt").getRemote()), not(anExistingDirectory()));
    }

    @Test
    public void preSecurity2586() throws Throwable {
        FilePath cache = LibraryCachingConfiguration.getGlobalLibrariesCacheDir().child("name").child("version");
        cache.mkdirs();
        cache.child(LibraryCachingConfiguration.LAST_READ_FILE).touch(System.currentTimeMillis());
        ExtensionList.lookupSingleton(LibraryCachingCleanup.class).execute(StreamTaskListener.fromStderr());
        assertThat(new File(cache.getRemote()), not(anExistingDirectory()));
        assertThat(new File(cache.getParent().getRemote()), not(anExistingDirectory()));
    }

}
