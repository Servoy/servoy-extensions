pipeline {
    agent any
    
    options {
        quietPeriod(120)
        // Log-rotator instellingen overgenomen uit de oude XML (40 dagen bewaren, max 70 builds)
        buildDiscarder(logRotator(daysToKeepStr: '40', numToKeepStr: '70'))
    }
    
    triggers {
        githubPush()
    }
    
    parameters {
        string(name: 'goals', defaultValue: 'install', trim: false)
    }
    
    environment {
        TEAMS_WEBHOOK = credentials('servoy-teams-webhook')
    }
    
    tools {
        jdk 'Java 21'
        maven 'Maven 3.9.16'
    }
    
    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        stage('Build Extensions') {
            steps {
                configFileProvider([
                    configFile(fileId: 'master_mvn_repo', variable: 'MAVEN_SETTINGS'),
                    configFile(fileId: 'maven_toolchain', variable: 'TOOLCHAIN')
                ]) {
                    // Via -f wijzen we direct naar de specifieke submap-POM uit de oude configuratie
                    sh 'mvn -B -s "$MAVEN_SETTINGS" -t "$TOOLCHAIN" -f com.servoy.extensions/pom.xml $goals'
                }
            }
        }
    }
    
    post {
        always {
            // Testresultaten verzamelen (AggregatedTestResultPublisher)
            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
        }
        
        success {
            // Trigger downstream project 'build' bij succes (verander naar 'build_pipe' indien gewenst)
            build job: 'build', wait: false
        }
        
        failure {
            office365ConnectorSend webhookUrl: TEAMS_WEBHOOK, status: 'Failed'
        }
        
        unstable {
            office365ConnectorSend webhookUrl: TEAMS_WEBHOOK, status: 'Unstable'
        }
        
        fixed {
            office365ConnectorSend webhookUrl: TEAMS_WEBHOOK, status: 'Back to Normal'
        }
    }
}