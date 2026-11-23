// Pushes the coverage reports of this build to the combined coverage job (<line>/coverage, see Jenkinsfile.coverage
// in servoy-eclipse): copies them to $JENKINS_HOME/coverage-staging/<line>/<source>/ and starts that job.
// Copy of servoy-eclipse ci/coverage/push-coverage.groovy, keep it the same.
def pushCoverage(String source, String includes, String line = null) {
    // only builds whose tests ran to the end count; a failed build keeps the previous push
    if (currentBuild.currentResult != 'SUCCESS' && currentBuild.currentResult != 'UNSTABLE') {
        echo "pushCoverage: build is ${currentBuild.currentResult}, coverage of ${source} not pushed"
        return
    }
    if (!(source ==~ /[A-Za-z0-9][A-Za-z0-9._-]{0,79}/)) {
        error "pushCoverage: invalid source name '${source}'"
    }
    def branchLine = line ?: env.JOB_NAME.contains('/') ? env.JOB_NAME.substring(0, env.JOB_NAME.lastIndexOf('/')) : null
    if (!branchLine) {
        echo "pushCoverage: job ${env.JOB_NAME} is not in a branch folder and no line was given, coverage not pushed"
        return
    }
    // Only master feeds the combined report. The same Jenkinsfile also runs in release / lts_* folders after a
    // branch is cut; those builds must not push (there is no combined job for them).
    if (branchLine != 'master') {
        echo "pushCoverage: ${env.JOB_NAME} is on line '${branchLine}', only master pushes coverage"
        return
    }
    def files = findFiles(glob: includes.split(',').collect { it.trim() }.join(','))
    if (files.length == 0) {
        echo "pushCoverage: no files match '${includes}', coverage of ${source} not pushed"
        return
    }
    catchError(buildResult: null, stageResult: null, message: 'coverage push failed') {
        withEnv(["COVERAGE_SOURCE=${source}", "COVERAGE_LINE=${branchLine}"]) {
            writeFile file: '.coverage-push-files', text: files.collect { it.path }.join('\n') + '\n'
            sh '''
                set -eu
                staging="$JENKINS_HOME/coverage-staging/$COVERAGE_LINE"
                new="$staging/$COVERAGE_SOURCE.new.$$"
                rm -rf "$new"
                mkdir -p "$new"
                while IFS= read -r f; do
                    [ -n "$f" ] || continue
                    mkdir -p "$new/$(dirname "$f")"
                    cp "$f" "$new/$f"
                done < .coverage-push-files
                rm -f .coverage-push-files
                rm -rf "$staging/$COVERAGE_SOURCE.old"
                if [ -d "$staging/$COVERAGE_SOURCE" ]; then mv "$staging/$COVERAGE_SOURCE" "$staging/$COVERAGE_SOURCE.old"; fi
                mv "$new" "$staging/$COVERAGE_SOURCE"
                rm -rf "$staging/$COVERAGE_SOURCE.old"
                echo "pushed $(find "$staging/$COVERAGE_SOURCE" -type f | wc -l) coverage file(s) of $COVERAGE_SOURCE to $staging"
            '''
        }
        build job: "/${branchLine}/coverage", wait: false, propagate: false
    }
}


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
                    // Archive the raw coverage data so it can be inspected/downloaded per build:
                    // jacoco.xml = the report that recordCoverage reads, jacoco.exec = the raw
                    // execution data, site/jacoco = the browsable HTML report.
                    archiveArtifacts artifacts: '**/target/site/jacoco/**, **/target/jacoco.exec', allowEmptyArchive: true, fingerprint: false

                    // push the report to the combined coverage job (<line>/coverage)
                    script { pushCoverage('servoy-extensions', '**/target/site/jacoco/jacoco.xml') }
                }
            }
        }
    }
    
    post {
        always {
            // Testresultaten verzamelen (AggregatedTestResultPublisher)
            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
        }
        
        changed {
            script {
                def statusMap = [
                    'SUCCESS' : 'Back to Normal',
                    'FAILURE' : 'Failed',
                    'UNSTABLE': 'Unstable',
                    'ABORTED' : 'Aborted'
                ]
                
                def statusText = statusMap.get(currentBuild.currentResult, currentBuild.currentResult)
    
                office365ConnectorSend webhookUrl: TEAMS_WEBHOOK, status: statusText, adaptiveCards: true
            }
        }
    }
}