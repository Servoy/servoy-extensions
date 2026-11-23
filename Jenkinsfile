pipeline {
    agent any
    
    options {
        quietPeriod(120)
        // Log-rotator instellingen overgenomen uit de oude XML (40 dagen bewaren, max 70 builds)
        buildDiscarder(logRotator(daysToKeepStr: '40', numToKeepStr: '70'))

        // cancel all previous builds don't run at the same time'
        disableConcurrentBuilds(abortPrevious: true)
    }
    
   triggers {
        GenericTrigger(
            genericVariables: [
                [key: 'ref', value: '$.ref']
            ],
            token: 'servoy-extensions',
            regexpFilterText: '$ref',
            regexpFilterExpression: "^refs/heads/${env.BRANCH}\$"
        )
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
        stage('Clear Queued Builds') {
            steps {
                script {
                    // Annuleer builds die in de queue wachten op de quietPeriod timer voor EXPANCT dit specifieke pad (bijv. "lts_2026/servoy-eclipse")
                    def currentJob = env.JOB_NAME
                    def queue = jenkins.model.Jenkins.get().queue
                    
                    queue.items.each { item ->
                        // ownerTask.fullName works for boht WorkflowJob or  PlaceholderTask objects
                        def queuedJobName = item.task.ownerTask?.fullName
                        if (queuedJobName == currentJob) {
                            echo "Removing pending queued build for ${currentJob} (Queue ID #${item.id})..."
                            queue.cancel(item)
                        }
                    }
                }
            }
        }

        stage('Build Extensions') {
            steps {
                configFileProvider([
                    configFile(fileId: 'master_mvn_repo', variable: 'MAVEN_SETTINGS'),
                    configFile(fileId: 'maven_toolchain', variable: 'TOOLCHAIN')
                ]) {
                    // Build com.servoy.extensions. The unit tests run in the test phase against the
                    // plain (unsigned) target/classes, and the jacoco agent + report (bound to verify)
                    // produce target/site/jacoco/jacoco.xml in the same build.
                    sh 'mvn -B -s "$MAVEN_SETTINGS" -t "$TOOLCHAIN" -f pom.xml $goals'
                }
            }
            post {
                always {
                    // JaCoCo coverage of the unit tests. sourceCodeRetention EVERY_BUILD keeps the
                    // checked-out sources per build so the Coverage view can show covered/missed lines.
                    recordCoverage(
                        tools: [[parser: 'JACOCO', pattern: '**/target/site/jacoco/jacoco.xml']],
                        id: 'jacoco-java',
                        name: 'JaCoCo Coverage - Java',
                        // the sources are not under the default src/main/java; point the coverage
                        // plugin at this module's actual source folders so the line view works
                        sourceDirectories: [[path: 'com.servoy.extensions/src'], [path: 'com.servoy.extensions/test']],
                        sourceCodeRetention: 'EVERY_BUILD'
                    )
                    // Archive only the jacoco.xml report (per class/method/line/branch counters, what
                    // recordCoverage reads and the published number reflects). The binary jacoco.exec
                    // and the HTML site are deliberately NOT archived: everything readable is already
                    // in jacoco.xml.
                    archiveArtifacts artifacts: '**/target/site/jacoco/jacoco.xml', allowEmptyArchive: true, fingerprint: false
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
            office365ConnectorSend webhookUrl: TEAMS_WEBHOOK, status: 'Failed', adaptiveCards: true
        }
        
        unstable {
            office365ConnectorSend webhookUrl: TEAMS_WEBHOOK, status: 'Unstable', adaptiveCards: true
        }
        
        fixed {
            office365ConnectorSend webhookUrl: TEAMS_WEBHOOK, status: 'Back to Normal', adaptiveCards: true
        }
    }
}