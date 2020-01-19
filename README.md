# Pipeline Shared Libraries

When you have multiple Pipeline jobs, you often want to share some parts of the Pipeline
scripts between them to keep Pipeline scripts [DRY](http://en.wikipedia.org/wiki/Don't_repeat_yourself).
A very common use case is that you have many projects that are built in the similar way.

This plugin adds that functionality by allowing you to create “shared library script” SCM repositories.
It can be used in two modes:
a legacy mode in which there is a single Git repository hosted by Jenkins itself, to which you may push changes;
and a more general mode in which you may define libraries hosted by any SCM in a location of your choice.

Provides capability to extend pipeline scripts using shared libraries.

This plugin adds that functionality by allowing you to create “shared
library script” SCM repositories. It can be used in two modes:

-   A legacy mode in which there is a single Git repository hosted by
    Jenkins itself, to which you may push changes;
-   A more general mode in which you may define libraries hosted by any
    SCM in a location of your choice.

Comprehensive user documentation can be found [in the Pipeline chapter
of the User
Handbook](https://jenkins.io/doc/book/pipeline/shared-libraries/).

A component of [Pipeline
Plugin](https://plugins.jenkins.io/workflow-aggregator).

[Source code
README.md](https://github.com/jenkinsci/workflow-cps-global-lib-plugin/blob/master/README.md)

### **Configure plugin via Groovy script**

Either automatically upon [Jenkins
post-initialization](https://wiki.jenkins.io/display/JENKINS/Post-initialization+script) or
through [Jenkins script
console](https://wiki.jenkins.io/display/JENKINS/Jenkins+Script+Console),
example:

``` groovy
#!groovy

// imports
import hudson.scm.SCM
import jenkins.model.Jenkins
import jenkins.plugins.git.GitSCMSource
import org.jenkinsci.plugins.workflow.libs.*
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever

// parameters
def globalLibrariesParameters = [
  branch:               "master",
  credentialId:         "global-shared-library-key",
  implicit:             false,
  name:                 "Your Global Shared Library name here",
  repository:           "git@bitbucket.org:your-company/your-repo.git"
]

// define global library
GitSCMSource gitSCMSource = new GitSCMSource(
  "global-shared-library",
  globalLibrariesParameters.repository,
  globalLibrariesParameters.credentialId,
  "*",
  "",
  false
)

// define retriever
SCMSourceRetriever sCMSourceRetriever = new SCMSourceRetriever(gitSCMSource)

// get Jenkins instance
Jenkins jenkins = Jenkins.getInstance()

// get Jenkins Global Libraries
def globalLibraries = jenkins.getDescriptor("org.jenkinsci.plugins.workflow.libs.GlobalLibraries")

// define new library configuration
LibraryConfiguration libraryConfiguration = new LibraryConfiguration(globalLibrariesParameters.name, sCMSourceRetriever)
libraryConfiguration.setDefaultVersion(globalLibrariesParameters.branch)
libraryConfiguration.setImplicit(globalLibrariesParameters.implicit)

// set new Jenkins Global Library
globalLibraries.get().setLibraries([libraryConfiguration])

// save current Jenkins state to disk
jenkins.save()
```