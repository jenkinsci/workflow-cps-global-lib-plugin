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

import groovy.lang.MetaClass;
import hudson.FilePath;
import hudson.model.Job;
import hudson.model.Result;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.slaves.WorkspaceList;
import hudson.scm.SubversionSCM;
import hudson.scm.ChangeLogSet;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.scm.impl.subversion.SubversionSCMSource;
import jenkins.scm.impl.subversion.SubversionSampleRepoRule;
import org.codehaus.groovy.reflection.ClassInfo;
import org.codehaus.groovy.transform.ASTTransformationVisitor;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;
import org.jenkinsci.plugins.workflow.cps.global.GrapeTest;
import org.jenkinsci.plugins.workflow.cps.global.UserDefinedGlobalVariable;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayAction;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MemoryAssert;
import org.jvnet.hudson.test.TestExtension;

public class LibraryAdderTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();
    @Rule public SubversionSampleRepoRule sampleSvnRepo = new SubversionSampleRepoRule();

    @Test public void smokes() throws Exception {
        sampleRepo.init();
        String lib = "package pkg; class Lib {static String CONST = 'constant'}";
        sampleRepo.write("src/pkg/Lib.groovy", lib);
        sampleRepo.git("add", "src");
        sampleRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(new LibraryConfiguration("stuff", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)))));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        String script = "@Library('stuff@master') import static pkg.Lib.*; echo(/using ${CONST}/)";
        p.setDefinition(new CpsFlowDefinition(script, true));
        r.assertLogContains("using constant", r.buildAndAssertSuccess(p));
        sampleRepo.git("tag", "1.0");
        sampleRepo.write("src/pkg/Lib.groovy", lib.replace("constant", "modified"));
        sampleRepo.git("commit", "--all", "--message=modified");
        r.assertLogContains("using modified", r.buildAndAssertSuccess(p));
        p.setDefinition(new CpsFlowDefinition(script.replace("master", "1.0"), true));
        r.assertLogContains("using constant", r.buildAndAssertSuccess(p));
    }

    @Test public void usingInterpolation() throws Exception {
        sampleRepo.init();
        sampleRepo.write("src/pkg/Lib.groovy", "package pkg; class Lib {static String CONST = 'initial'}");
        sampleRepo.git("add", "src");
        sampleRepo.git("commit", "--message=init");
        sampleRepo.git("tag", "initial");
        sampleRepo.write("src/pkg/Lib.groovy", "package pkg; class Lib {static String CONST = 'modified'}");
        sampleRepo.git("commit", "--all", "--message=modified");
        LibraryConfiguration stuff = new LibraryConfiguration("stuff",
            new SCMRetriever(
                    new GitSCM(Collections.singletonList(new UserRemoteConfig(sampleRepo.fileUrl(), null, null, null)),
                            Collections.singletonList(new BranchSpec("${library.stuff.version}")),
                            false, Collections.<SubmoduleConfig>emptyList(), null, null, Collections.<GitSCMExtension>emptyList())));
        stuff.setDefaultVersion("master");
        stuff.setImplicit(true);
        GlobalLibraries.get().setLibraries(Collections.singletonList(stuff));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('stuff@master') import pkg.Lib; echo(/using ${Lib.CONST}/)", true));
        r.assertLogContains("using modified", r.buildAndAssertSuccess(p));
        p.setDefinition(new CpsFlowDefinition("@Library('stuff@initial') import pkg.Lib; echo(/using ${Lib.CONST}/)", true));
        r.assertLogContains("using initial", r.buildAndAssertSuccess(p));
        p.setDefinition(new CpsFlowDefinition("@Library('stuff') import pkg.Lib; echo(/using ${Lib.CONST}/)", true));
        r.assertLogContains("using modified", r.buildAndAssertSuccess(p));
        p.setDefinition(new CpsFlowDefinition("echo(/using ${pkg.Lib.CONST}/)", true));
        r.assertLogContains("using modified", r.buildAndAssertSuccess(p));
    }

    @Test public void interpolationSvn() throws Exception {
        sampleSvnRepo.init();
        sampleSvnRepo.write("src/pkg/Lib.groovy", "package pkg; class Lib {static String CONST = 'initial'}");
        sampleSvnRepo.svnkit("add", sampleSvnRepo.wc() + "/src");
        sampleSvnRepo.svnkit("commit", "--message=init", sampleSvnRepo.wc());
        sampleSvnRepo.svnkit("copy", "--message=tagged", sampleSvnRepo.trunkUrl(), sampleSvnRepo.tagsUrl() + "/initial");
        sampleSvnRepo.write("src/pkg/Lib.groovy", "package pkg; class Lib {static String CONST = 'modified'}");
        sampleSvnRepo.svnkit("commit", "--message=modified", sampleSvnRepo.wc());
        LibraryConfiguration stuff = new LibraryConfiguration("stuff", new SCMRetriever(new SubversionSCM(sampleSvnRepo.prjUrl() + "/${library.stuff.version}")));
        stuff.setDefaultVersion("trunk");
        stuff.setImplicit(true);
        GlobalLibraries.get().setLibraries(Collections.singletonList(stuff));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('stuff@trunk') import pkg.Lib; echo(/using ${Lib.CONST}/)", true));
        r.assertLogContains("using modified", r.buildAndAssertSuccess(p));
        p.setDefinition(new CpsFlowDefinition("@Library('stuff@tags/initial') import pkg.Lib; echo(/using ${Lib.CONST}/)", true));
        r.assertLogContains("using initial", r.buildAndAssertSuccess(p));
        p.setDefinition(new CpsFlowDefinition("@Library('stuff') import pkg.Lib; echo(/using ${Lib.CONST}/)", true));
        r.assertLogContains("using modified", r.buildAndAssertSuccess(p));
        p.setDefinition(new CpsFlowDefinition("echo(/using ${pkg.Lib.CONST}/)", true));
        r.assertLogContains("using modified", r.buildAndAssertSuccess(p));
    }

    @Test public void properSvn() throws Exception {
        sampleSvnRepo.init();
        sampleSvnRepo.write("src/pkg/Lib.groovy", "package pkg; class Lib {static String CONST = 'initial'}");
        sampleSvnRepo.svnkit("add", sampleSvnRepo.wc() + "/src");
        sampleSvnRepo.svnkit("commit", "--message=init", sampleSvnRepo.wc());
        long tag = sampleSvnRepo.revision();
        sampleSvnRepo.svnkit("copy", "--message=tagged", sampleSvnRepo.trunkUrl(), sampleSvnRepo.tagsUrl() + "/initial");
        sampleSvnRepo.write("src/pkg/Lib.groovy", "package pkg; class Lib {static String CONST = 'modified'}");
        sampleSvnRepo.svnkit("commit", "--message=modified", sampleSvnRepo.wc());
        LibraryConfiguration stuff = new LibraryConfiguration("stuff", new SCMSourceRetriever(new SubversionSCMSource(null, sampleSvnRepo.prjUrl())));
        stuff.setDefaultVersion("trunk");
        stuff.setImplicit(true);
        GlobalLibraries.get().setLibraries(Collections.singletonList(stuff));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('stuff@trunk') import pkg.Lib; echo(/using ${Lib.CONST}/)", true));
        r.assertLogContains("using modified", r.buildAndAssertSuccess(p));
        p.setDefinition(new CpsFlowDefinition("@Library('stuff@tags/initial') import pkg.Lib; echo(/using ${Lib.CONST}/)", true));
        r.assertLogContains("using initial", r.buildAndAssertSuccess(p));
        p.setDefinition(new CpsFlowDefinition("@Library('stuff') import pkg.Lib; echo(/using ${Lib.CONST}/)", true));
        r.assertLogContains("using modified", r.buildAndAssertSuccess(p));
        p.setDefinition(new CpsFlowDefinition("echo(/using ${pkg.Lib.CONST}/)", true));
        r.assertLogContains("using modified", r.buildAndAssertSuccess(p));
        // Note that LibraryAdder.parse uses indexOf not lastIndexOf, so we can have an @ inside a revision
        // (the converse is that we may not have an @ inside a library name):
        p.setDefinition(new CpsFlowDefinition("@Library('stuff@trunk@" + tag + "') import pkg.Lib; echo(/using ${Lib.CONST}/)", true));
        r.assertLogContains("using initial", r.buildAndAssertSuccess(p));
    }

    @Issue("JENKINS-41497")
    @Test public void dontIncludeChangesetsOverriden() throws Exception {
        sampleRepo.init();
        sampleRepo.write("vars/myecho.groovy", "def call() {echo 'something special'}");
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        LibraryConfiguration lc = new LibraryConfiguration("dont_include_changes", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)));
        lc.setIncludeInChangesets(false);
        GlobalLibraries.get().setLibraries(Collections.singletonList(lc));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('dont_include_changes@master@changesets') import myecho; myecho()", true));
        FilePath base = r.jenkins.getWorkspaceFor(p).withSuffix("@libs").child("dont_include_changes");
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
        }
    }

    @Issue("JENKINS-41497")
    @Test public void includeChangesetsOverridden() throws Exception {
        sampleRepo.init();
        sampleRepo.write("vars/myecho.groovy", "def call() {echo 'something special'}");
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        LibraryConfiguration lc = new LibraryConfiguration("include_changes", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)));
        lc.setIncludeInChangesets(true);
        GlobalLibraries.get().setLibraries(Collections.singletonList(lc));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('include_changes@master@nochangesets') import myecho; myecho()", true));
        FilePath base = r.jenkins.getWorkspaceFor(p).withSuffix("@libs").child("include_changes");
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
        }
    }

    @Issue("JENKINS-41497")
    @Test public void onlyOverrideChangesets() throws Exception {
        sampleRepo.init();
        sampleRepo.write("vars/myecho.groovy", "def call() {echo 'something special'}");
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        LibraryConfiguration lc = new LibraryConfiguration("dont_include_changes", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)));
        lc.setIncludeInChangesets(false);
        lc.setDefaultVersion("master");
        GlobalLibraries.get().setLibraries(Collections.singletonList(lc));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('dont_include_changes@null@changesets') import myecho; myecho()", true));
        FilePath base = r.jenkins.getWorkspaceFor(p).withSuffix("@libs").child("dont_include_changes");
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
        }
    }


    @Test public void globalVariable() throws Exception {
        sampleRepo.init();
        sampleRepo.write("vars/myecho.groovy", "def call() {echo 'something special'}");
        sampleRepo.write("vars/myecho.txt", "Says something very special!");
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(
            new LibraryConfiguration("echo-utils",
                new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)))));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('echo-utils@master') import myecho; myecho()", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertLogContains("something special", b);
        GlobalVariable var = GlobalVariable.byName("myecho", b);
        assertNotNull(var);
        assertEquals("Says something very special!", ((UserDefinedGlobalVariable) var).getHelpHtml());
    }

    @Test public void dynamicLibraries() throws Exception {
        sampleRepo.init();
        sampleRepo.write("src/pkg/Lib.groovy", "package pkg; class Lib {static String CONST = 'constant'}");
        sampleRepo.git("add", "src");
        sampleRepo.git("commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        DynamicResolver.remote = sampleRepo.toString();
        p.setDefinition(new CpsFlowDefinition("@Library('dynamic') import pkg.Lib; echo(/using ${Lib.CONST}/)", true));
        r.assertLogContains("using constant", r.buildAndAssertSuccess(p));
    }
    @TestExtension("dynamicLibraries") public static class DynamicResolver extends LibraryResolver {
        @Override public boolean isTrusted() {
            return false;
        }
        static String remote;
        @Override public Collection<LibraryConfiguration> forJob(Job<?,?> job, Map<String,String> libraryVersions) {
            if (libraryVersions.containsKey("dynamic")) {
                LibraryConfiguration cfg = new LibraryConfiguration("dynamic", new SCMSourceRetriever(new GitSCMSource(null, remote, "", "*", "", true)));
                cfg.setDefaultVersion("master");
                return Collections.singleton(cfg);
            } else {
                return Collections.emptySet();
            }
        }
    }

    @Test public void undefinedLibraries() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('nonexistent') _", true));
        r.assertLogContains(Messages.LibraryDecorator_could_not_find_any_definition_of_librari(Collections.singletonList("nonexistent")), r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
    }

    /** @see GrapeTest */
    @Test public void grape() throws Exception {
        sampleRepo.init();
        sampleRepo.write("src/semver/Version.groovy",
            "package semver\n" +
            "@Grab('com.vdurmont:semver4j:2.0.1') import com.vdurmont.semver4j.Semver\n" + // https://github.com/vdurmont/semver4j#using-gradle
            "public class Version implements Serializable {\n" +
            "  private final String v\n" +
            "  public Version(String v) {this.v = v}\n" +
            // @NonCPS since third-party class is not Serializable
            "  @NonCPS public boolean isGreaterThan(String version) {\n" +
            "    new Semver(v).isGreaterThan(version)\n" +
            "  }\n" +
            "}");
        sampleRepo.git("add", "src");
        sampleRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(new LibraryConfiguration("semver", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)))));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
            "@Library('semver@master') import semver.Version\n" +
            "echo(/1.2.0 > 1.0.0? ${new Version('1.2.0').isGreaterThan('1.0.0')}/)\n" +
            "echo(/1.0.0 > 1.2.0? ${new Version('1.0.0').isGreaterThan('1.2.0')}/)", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertLogContains("1.2.0 > 1.0.0? true", b);
        r.assertLogContains("1.0.0 > 1.2.0? false", b);
    }

    @Test public void noReplayTrustedLibraries() throws Exception {
        sampleRepo.init();
        String originalMessage = "must not be edited";
        String originalScript = "def call() {echo '" + originalMessage + "'}";
        sampleRepo.write("vars/trusted.groovy", originalScript);
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(new LibraryConfiguration("trusted", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)))));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('trusted@master') import trusted; trusted()", true));
        WorkflowRun b1 = r.buildAndAssertSuccess(p);
        r.assertLogContains(originalMessage, b1);
        ReplayAction ra = b1.getAction(ReplayAction.class);
        assertEquals(Collections.emptyMap(), ra.getOriginalLoadedScripts());
        WorkflowRun b2 = (WorkflowRun) ra.run(ra.getOriginalScript(), Collections.singletonMap("trusted", originalScript.replace(originalMessage, "should not allowed"))).get();
        r.assertBuildStatusSuccess(b2); // currently do not throw an error, since the GUI does not offer it anyway
        r.assertLogContains(originalMessage, b2);
    }

    private static final List<WeakReference<ClassLoader>> LOADERS = new ArrayList<>();
    public static void register(Object o) {
        ClassLoader loader = o.getClass().getClassLoader();
        System.err.println("registering " + o + " from " + loader);
        LOADERS.add(new WeakReference<>(loader));
    }
    @Test public void loaderReleased() throws Exception {
        sampleRepo.init();
        sampleRepo.write("vars/leak.groovy", "def call() {" + LibraryAdderTest.class.getName() + ".register(this)}");
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(new LibraryConfiguration("leak", new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)))));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('leak@master') _; " + LibraryAdderTest.class.getName() + ".register(this); leak()", false));
        r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertFalse(LOADERS.isEmpty());
        try { // For Jenkins/Groovy 1. Cf. CpsFlowExecutionTest.loaderReleased.
            Field f = ASTTransformationVisitor.class.getDeclaredField("compUnit");
            f.setAccessible(true);
            f.set(null, null);
        } catch (NoSuchFieldException e) {}
        { // ditto
            MetaClass metaClass = ClassInfo.getClassInfo(LibraryAdderTest.class).getMetaClass();
            Method clearInvocationCaches = metaClass.getClass().getDeclaredMethod("clearInvocationCaches");
            clearInvocationCaches.setAccessible(true);
            clearInvocationCaches.invoke(metaClass);
        }
        for (WeakReference<ClassLoader> loaderRef : LOADERS) {
            MemoryAssert.assertGC(loaderRef, false);
        }
    }

    @Issue({"JENKINS-38021", "JENKINS-31484"})
    @Test public void gettersAndSetters() throws Exception {
        sampleRepo.init();
        sampleRepo.write("vars/config.groovy", "class config implements Serializable {private String foo; public String getFoo() {return(/loaded ${this.foo}/)}; public void setFoo(String value) {this.foo = value.toUpperCase()}}");
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        GlobalLibraries.get().setLibraries(Collections.singletonList(
            new LibraryConfiguration("config",
                new SCMSourceRetriever(new GitSCMSource(null, sampleRepo.toString(), "", "*", "", true)))));
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('config@master') _; timeout(1) {config.foo = 'bar'; echo(/set to $config.foo/)}", false));
        r.assertLogContains("set to loaded BAR", r.buildAndAssertSuccess(p));
        p.setDefinition(new CpsFlowDefinition("@Library('config@master') _; config.setFoo('bar'); echo(/set to ${config.getFoo()}/)", true));
        r.assertLogContains("set to loaded BAR", r.buildAndAssertSuccess(p));
        p.setDefinition(new CpsFlowDefinition("@Library('config@master') _; echo(/set to $config.foo/)", true));
        r.assertLogContains("set to loaded null", r.buildAndAssertSuccess(p));
    }

}
