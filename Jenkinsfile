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
                    // Build the parent reactor (main plugin + test module) so the tests also run in CI.
                    // The jacoco-prepare-agent in the test module writes the runtime coverage data to
                    // com.servoy.extensions/target/jacoco.exec during this run.
                    sh 'mvn -B -s "$MAVEN_SETTINGS" -t "$TOOLCHAIN" -f pom.xml $goals'
                }
            }
        }
        stage('Coverage Report') {
            // JaCoCo coverage of the unit tests. The test module (com.servoy.extensions.test) ran in
            // the build stage with the jacoco agent and wrote com.servoy.extensions/target/jacoco.exec;
            // jacoco:report can only analyse its own module's classes, so it is run in the production
            // module com.servoy.extensions against that exec file (same pattern as Servoy-Copilot's
            // host-bundle report). Produces target/site/jacoco/jacoco.xml, published below.
            steps {
                configFileProvider([
                    configFile(fileId: 'master_mvn_repo', variable: 'MAVEN_SETTINGS'),
                    configFile(fileId: 'maven_toolchain', variable: 'TOOLCHAIN')
                ]) {
                    catchError(buildResult: 'UNSTABLE', stageResult: 'FAILURE') {
                        sh 'mvn -B -s "$MAVEN_SETTINGS" -t "$TOOLCHAIN" -f pom.xml org.jacoco:jacoco-maven-plugin:report -pl com.servoy.extensions'
                    }
                }
            }
            post {
                always {
                    // Java (JaCoCo) coverage of the unit tests. sourceCodeRetention EVERY_BUILD keeps the
                    // checked-out sources per build so the Coverage view can show covered/missed lines.
                    recordCoverage(
                        tools: [[parser: 'JACOCO', pattern: '**/target/site/jacoco/jacoco.xml']],
                        id: 'jacoco-java',
                        name: 'JaCoCo Coverage - Java',
                        sourceCodeRetention: 'EVERY_BUILD'
                    )
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