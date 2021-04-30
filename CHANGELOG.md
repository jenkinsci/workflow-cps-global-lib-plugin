# Changelog

## 2.19

Release date: 2021-04-30

- Developer: Expose LibrariesAction and LibraryRecord to Jenkins' REST API ([PR 107](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/107))

## 2.18

Release date: 2021-02-19

- Fix: Prevent `ConcurrentModificationException` from being thrown when serializing `LibrariesAction.libraries` ([JENKINS-41037](https://issues.jenkins.io/browse/JENKINS-41037))
- Internal: Fix PCT failures related to Git plugin 4.6.0 ([PR 104](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/104)) 

## 2.17

Release date: 2020-07-20

- Internal: Remove dependency on `commons-lang3` to avoid version conflicts with `jenkins-test-harness` in downstream projects. ([PR 94](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/94))
- Internal: Update minimum required Jenkins version and plugin dependencies. ([PR 95](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/95))
- Internal: Fix tests affected by [JENKINS-60406](https://issues.jenkins-ci.org/browse/JENKINS-60406) when running against Jenkins 2.222.x or newer. ([PR 96](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/96))

## 2.16

Release date: 2020-03-13

- Fix: Exclude the contents of `src/test/` in shared libraries from being used by Pipelines, as these files were likely only intended to be used in tests for the libraries rather than by Pipelines, and depending on the contents of `src/test/`, it may be unsafe for those files to be exposed to Pipelines. To restore the previous behavior that allowed access to files in src/test/, pass `-Dorg.jenkinsci.plugins.workflow.libs.SCMSourceRetriever.INCLUDE_SRC_TEST_IN_LIBRARIES=true` to the java command used to start Jenkins. ([PR 91](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/91))
- Fix: Do not bundle JARs from Jenkins Apache HttpComponents Client 4.x API Plugin in this plugin ([PR 75](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/75))
- Fix: Trim leading and trailing whitespace when configuration the name or version of a shared library ([JENKINS-59527](https://issues.jenkins-ci.org/browse/JENKINS-59527))
- Improvement: Clarify that if the "Include @Library changes in job recent changes" option is checked, changes to the library will trigger builds of Pipelines that use the library ([PR 61](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/61))
- Internal: Migrate wiki content to GitHub ([PR 89](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/89))
- Internal: Add tests related to the criteria used to decide if an SCM is modern or legacy ([JENKINS-58964](https://issues.jenkins-ci.org/browse/JENKINS-58964))
- Internal: Update tests to handle behavior changes caused by SECURITY-1713 ([PR 90](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/90))
- Internal: Update parent POM ([PR 88](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/88))
- Internal: Enable the sandbox consistently in tests ([PR 79](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/79))
- Internal: Remove duplicated code ([PR 74](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/74))

## 2.15

Release date: 2019-07-31

- [Fix security issue](https://jenkins.io/security/advisory/2019-07-31/#SECURITY-1422)

## 2.14

Release date: 2019-07-11
-   [JENKINS-43802](https://issues.jenkins-ci.org/browse/JENKINS-43802) -
    Make folder-scoped credentials work correctly with shared libraries.
-   [JENKINS-44892](https://issues.jenkins-ci.org/browse/JENKINS-44892) -
    Do not add a UUID parameter when constructing the `library` step on
    the Pipeline Syntax page.
-   Fix: ([PR
    63](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/63)) -
    Support for SCM retry count did not retry the checkout attempt for
    certain kinds of errors when it should have. 
-   Internal: ([PR
    66](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/66)) -
    Do not add nullability annotations to primitive types.
-   Internal: ([PR
    67](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/67), [PR
    70](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/70), [PR
    73](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/73))
    - Dependency and test updates, new integration tests for issues
    fixed in upstream plugins.

## 2.13

Release date: 2019-02-01
-   Fix: ([PR
    59](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/59)) -
    Support for SCM retry count added in 2.12 did not apply to some SCM
    operations.
-   Internal: ([PR
    57](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/57))
    - Avoid use of deprecated APIs.
-   Internal: ([PR
    44](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/44),
    [PR
    56](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/pull/56)) -
    Add additional tests and update tests to run correctly on Windows

## 2.12

Release date: 2018-10-02
-   Fix: [JENKINS-40109](https://issues.jenkins-ci.org/browse/JENKINS-40109) -
    Make compilation errors in shared libraries serializable so that the
    actual compilation error is reported instead of
    a `NotSerializableException` in some cases.
-   Improvement: Implement support for SCM retry count.

## 2.11 

Release date: 2018-09-08
-   [JENKINS-53485](https://issues.jenkins-ci.org/browse/JENKINS-53485) -
    Fix a file leak introduced in version 2.10 of this plugin affecting
    all uses of the `libraryResource` step.

## 2.10

Release date: 2018-08-21
-   **Important: As of this release, the plugin requires Java 8 and
    Jenkins 2.60.3 or newer.**
-   [JENKINS-52313](https://issues.jenkins-ci.org/browse/JENKINS-52313) -
    Add an optional encoding argument to the `libraryResource`
    step. `Base64` is a supported encoding, and will cause the resource
    to be loaded as a Base64-encoded string, which is useful for copying
    binary resources such as images when combined with Pipeline: Basic
    Steps 2.8.1 or higher.

## 2.9 

Release date: 2017-09-13
-   [JENKINS-41497](https://issues.jenkins-ci.org/browse/JENKINS-41497) -
    allow excluding shared libraries from changelogs (and therefore from
    SCM polling as well) via global configuration option
    and/or `@Library(value="some-lib@master", changelog=false)`.

## 2.8 

Release date: 2017-04-24
-   Fixing some bugs affecting Windows-based masters (agent platform
    irrelevant):
    -   improper handling of CRNL in `*.txt` global variable help files
    -   incorrect display of class names in **Replay** when using class
        libraries
    -   failure of class library access from `library` step depending on
        filesystem canonicalization

## 2.7 

Release date: 2017-03-03
-   [JENKINS-39450](https://issues.jenkins-ci.org/browse/JENKINS-39450)
    Added a `library` step as a dynamic alternative to `@Library` used
    since 2.3.

## 2.6 

Release date: 2016-02-10
-   [JENKINS-40408](https://issues.jenkins-ci.org/browse/JENKINS-40408)
    Race condition introduced in 2.5.

## 2.5 

Release date: 2016-11-21
-   Related to
    [JENKINS-38517](https://issues.jenkins-ci.org/browse/JENKINS-38517),
    checking out distinct libraries each into their own local
    workspaces, and improving parallelism in the case of concurrent
    builds.

## 2.4 

Release date: 2016-10-05
-   [JENKINS-38550](https://issues.jenkins-ci.org/browse/JENKINS-38550)
    The **Modern SCM** option should not be shown unless some matching
    plugin is actually installed.
-   [JENKINS-38712](https://issues.jenkins-ci.org/browse/JENKINS-38712)
    Library configuration screens used deep horizontal indentation.
-   [JENKINS-38048](https://issues.jenkins-ci.org/browse/JENKINS-38048)
    Obsolete query parameter caused a warning in the JavaScript console.

## 2.3 

Release date: 2016-09-07
-   [JENKINS-31155](https://issues.jenkins-ci.org/browse/JENKINS-31155)
    New system of external shared libraries.
-   [JENKINS-26192](https://issues.jenkins-ci.org/browse/JENKINS-26192)
    Supporting Grape (the `@Grab` annotation) from global shared
    libraries (internal or external).

## 2.2 

Release date: 2016-08-09
-   [JENKINS-34650](https://issues.jenkins-ci.org/browse/JENKINS-34650)
    Global library code now runs without the Groovy sandbox, so may
    provide safe encapsulations of privileged operations such as Jenkins
    API accesses. (Pushes to the library always required
    Overall/RunScripts anyway.)
-   [JENKINS-34008](https://issues.jenkins-ci.org/browse/JENKINS-34008)
    API allowing plugins to be notified of changes to the library.

## 2.1 

Release date: 2016-06-30
-   [JENKINS-34517](https://issues.jenkins-ci.org/browse/JENKINS-34517)
    Use of global variables from the shared library would result in
    errors when resuming a build.

## 2.0 

Release date: 2016-04-058
-   First release under per-plugin versioning scheme. See [1.x
    changelog](https://github.com/jenkinsci/workflow-plugin/blob/82e7defa37c05c5f004f1ba01c93df61ea7868a5/CHANGES.md)
    for earlier releases.
