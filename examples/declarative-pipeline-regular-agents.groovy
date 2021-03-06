pipeline {
    options { 
        buildDiscarder(logRotator(numToKeepStr: '5'))
        //discard old builds to reduce disk usage
    }
    agent {
        kubernetes {
        label 'regular-pod'
        yaml '''
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: maven
      image: maven:3.6.3-adoptopenjdk-11
      command:
      - cat
      tty: true
    - name: aws-cli
      image: amazon/aws-cli:latest
      command:
      - cat
      tty: true
  nodeSelector:
    partition: regular-agents
  tolerations:
    - key: partition
      operator: Equal
      value: regular-agents
      effect: NoSchedule
'''
        }
    }
    stages {
        stage('Checkout SCM') {
            steps {
                //this step runs on the default jnlp agent, which has git installed:
                //docker run --rm -i -t cloudbees/cloudbees-core-agent git
                git branch: 'main', url: 'https://github.com/spring-projects/spring-petclinic.git'
            }
        }
        stage('Build and Test with Maven') { //in "regular agents" pool
            steps {
                //this step runs in a different container using the raw yaml above,
                //and the workspace is shared with the default jnlp agent
                container('maven') {
                    sh 'mvn package'
                    //note that the master branch of an active project
                    //may not always build successfully..
                    archiveArtifacts 'target/*.jar'
                    //download the artifact from the UI and run it:
                    //'java -jar <artifactName>.jar' then visit http://localhost:8080
                }
            }
        }
        stage ('Deploy') {
            steps {
                container('aws-cli') {
                    sh 'aws --version'
                }
            }
        }
    }
}
