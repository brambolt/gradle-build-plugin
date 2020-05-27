
node {

  stage('checkout') {
    checkout scm
  }

  stage('build') {

    withCredentials(
      [usernamePassword(
        credentialsId: ${env.JENKINS_MAVEN_CREDS},
        usernameVariable: 'ORG_GRADLE_PROJECT_mavenUser',
        passwordVariable: 'ORG_GRADLE_PROJECT_mavenToken'),
       usernamePassword(
        credentialsId: ${env.JENKINS_VCS_CREDS},
        usernameVariable: 'ORG_GRADLE_PROJECT_vcsUser',
        passwordVariable: 'ORG_GRADLE_PROJECT_vcsToken')]) {

      withEnv([ "GRADLE_OPTS=-Dgradle.user.home=${env.HOME}/.gradle" ]) {
        sh "echo ${GRADLE_OPTS}"
        sh "./gradlew clean artifactoryPublish -PbuildNumber=${BUILD_NUMBER} --info --stacktrace --refresh-dependencies"
      }
    }
  }
}

