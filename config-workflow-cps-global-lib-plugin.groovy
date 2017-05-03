import jenkins.model.*;
import jenkins.plugins.git.*;
import org.jenkinsci.plugins.workflow.libs.*;

def inst = Jenkins.getInstance()
def descriptor = inst.getDescriptorByType(org.jenkinsci.plugins.workflow.libs.GlobalLibraries)

scm = new GitSCMSource(
  'name-of-library',                            // String id
  'git@github.com:owner/jenkins-libraries.git', // String remote
  'github-ssh-key',                             // String credentialsId
  'origin',                                     // String remoteName
  '+refs/heads/*:refs/remotes/origin/*',        // String rawRefSpecs
  '*',                                          // String includes
  '',                                           // String excludes
  false                                         // booleean ignoreOnPushNotifications
)

retriever = new SCMSourceRetriever(scm)

libConfig = new LibraryConfiguration("jenkins-libraries", retriever)
libConfig.setDefaultVersion('master')
libConfig.setImplicit(true)
libConfig.setAllowVersionOverride(true)

libraries = []
libraries << libConfig

descriptor.setLibraries(libraries)
