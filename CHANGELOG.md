## Changelog

### 2.14 (Jul 11, 2019)

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

### 2.13 (Feb 1, 2019)

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

### 2.12 (Oct 2, 2018)

-   Fix: [JENKINS-40109](https://issues.jenkins-ci.org/browse/JENKINS-40109) -
    Make compilation errors in shared libraries serializable so that the
    actual compilation error is reported instead of
    a `NotSerializableException` in some cases.
-   Improvement: Implement support for SCM retry count.

### 2.11 (Sep 8, 2018)

-   [JENKINS-53485](https://issues.jenkins-ci.org/browse/JENKINS-53485) -
    Fix a file leak introduced in version 2.10 of this plugin affecting
    all uses of the `libraryResource` step.

### 2.10 (Aug 21, 2018)

-   **Important: As of this release, the plugin requires Java 8 and
    Jenkins 2.60.3 or newer.**
-   [JENKINS-52313](https://issues.jenkins-ci.org/browse/JENKINS-52313) -
    Add an optional encoding argument to the `libraryResource`
    step. `Base64` is a supported encoding, and will cause the resource
    to be loaded as a Base64-encoded string, which is useful for copying
    binary resources such as images when combined with Pipeline: Basic
    Steps 2.8.1 or higher.

### 2.9 (Sept 13, 2017)

-   [JENKINS-41497](https://issues.jenkins-ci.org/browse/JENKINS-41497) -
    allow excluding shared libraries from changelogs (and therefore from
    SCM polling as well) via global configuration option
    and/or `@Library(value="some-lib@master", changelog=false)`.

### 2.8 (Apr 24, 2017)

-   Fixing some bugs affecting Windows-based masters (agent platform
    irrelevant):
    -   improper handling of CRNL in `*.txt` global variable help files
    -   incorrect display of class names in **Replay** when using class
        libraries
    -   failure of class library access from `library` step depending on
        filesystem canonicalization

### 2.7 (Mar 03, 2017)

-   [JENKINS-39450](https://issues.jenkins-ci.org/browse/JENKINS-39450)
    Added a `library` step as a dynamic alternative to `@Library` used
    since 2.3.

### 2.6 (Feb 10, 2016)

-   [JENKINS-40408](https://issues.jenkins-ci.org/browse/JENKINS-40408)
    Race condition introduced in 2.5.

### 2.5 (Nov 21, 2016)

-   Related to
    [JENKINS-38517](https://issues.jenkins-ci.org/browse/JENKINS-38517),
    checking out distinct libraries each into their own local
    workspaces, and improving parallelism in the case of concurrent
    builds.

### 2.4 (Oct 05, 2016)

-   [JENKINS-38550](https://issues.jenkins-ci.org/browse/JENKINS-38550)
    The **Modern SCM** option should not be shown unless some matching
    plugin is actually installed.
-   [JENKINS-38712](https://issues.jenkins-ci.org/browse/JENKINS-38712)
    Library configuration screens used deep horizontal indentation.
-   [JENKINS-38048](https://issues.jenkins-ci.org/browse/JENKINS-38048)
    Obsolete query parameter caused a warning in the JavaScript console.

### 2.3 (Sep 07, 2016)

-   [JENKINS-31155](https://issues.jenkins-ci.org/browse/JENKINS-31155)
    New system of external shared libraries.
-   [JENKINS-26192](https://issues.jenkins-ci.org/browse/JENKINS-26192)
    Supporting Grape (the `@Grab` annotation) from global shared
    libraries (internal or external).

### 2.2 (Aug 09, 2016)

-   [JENKINS-34650](https://issues.jenkins-ci.org/browse/JENKINS-34650)
    Global library code now runs without the Groovy sandbox, so may
    provide safe encapsulations of privileged operations such as Jenkins
    API accesses. (Pushes to the library always required
    Overall/RunScripts anyway.)
-   [JENKINS-34008](https://issues.jenkins-ci.org/browse/JENKINS-34008)
    API allowing plugins to be notified of changes to the library.

### 2.1 (Jun 30, 2016)

-   [JENKINS-34517](https://issues.jenkins-ci.org/browse/JENKINS-34517)
    Use of global variables from the shared library would result in
    errors when resuming a build.

### 2.0 (Apr 05, 2016)

-   First release under per-plugin versioning scheme. See [1.x
    changelog](https://github.com/jenkinsci/workflow-plugin/blob/82e7defa37c05c5f004f1ba01c93df61ea7868a5/CHANGES.md)
    for earlier releases.