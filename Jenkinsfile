pipeline {
    agent { label 'java-jdk21' }

    options {
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '3'))
    }

    parameters {
        choice(name: 'BROWSER', choices: ['chrome'], description: 'Browser (only chrome is available on Selenoid for this project)')
        choice(name: 'BROWSER_VERSION', choices: ['156.0', '155.0', '148.0'], description: 'Chrome version on Selenoid')
        string(name: 'BROWSER_SIZE', defaultValue: '1920x1080', description: 'Browser window size, WIDTHxHEIGHT')
        password(name: 'SELENOID_USER', defaultValue: '', description: 'Selenoid username (no Credentials/View permission on this Jenkins, so this is a param, not a Credential)')
        password(name: 'SELENOID_PASS', defaultValue: '', description: 'Selenoid password')
        password(name: 'TELEGRAM_BOT_TOKEN', defaultValue: '', description: 'Telegram bot token (optional, leave empty to skip notification)')
        password(name: 'TELEGRAM_CHAT_ID', defaultValue: '', description: 'Telegram chat id (optional)')
    }

    environment {
        SELENOID_URL      = 'selenoid.qa.guru/wd/hub'
        // Jenkins-only: this agent's network blocks most Telegram IPs by TCP; 149.154.167.220 confirmed reachable (not guaranteed over time).
        TELEGRAM_RESOLVE_IPS = '149.154.167.220,149.154.167.99,149.154.166.110,95.161.64.100,91.108.56.130'
        ALLURE_VERSION    = '2.46.1'
        ALLURE_SHA256     = '37428164f310c8d137cfdd2c3a25d049b233261ddab13e1bd4dfd7627b5cbf8d'
        GROOVY_VERSION    = '5.0.6'
        GROOVY_SHA256     = '32338cdd9f6d842a534ea086242bf874385ee5be6973dc3de72f7605bf600394'
        GROOVY_XML_SHA256 = 'dbac2f3fa91b6671dda3e01ae8612168ef6c5c65e5670c9f8b95e953f393e5c2'
        TOOLS_DIR         = '.ci-tools'
    }

    stages {
        stage('Prepare tools') {
            steps {
                script {
                    sh '''
                        set -e
                        case "$BROWSER_SIZE" in
                            [0-9][0-9][0-9]x[0-9][0-9][0-9] | [0-9][0-9][0-9]x[0-9][0-9][0-9][0-9] | \
                            [0-9][0-9][0-9][0-9]x[0-9][0-9][0-9] | [0-9][0-9][0-9][0-9]x[0-9][0-9][0-9][0-9]) ;;
                            *) echo "browserSize must match WIDTHxHEIGHT, e.g. 1920x1080" >&2; exit 1 ;;
                        esac

                        mkdir -p "$TOOLS_DIR/groovy"
                        if [ -f "$TOOLS_DIR/groovy/groovy.jar" ] \
                           && echo "$GROOVY_SHA256  $TOOLS_DIR/groovy/groovy.jar" | sha256sum --check --strict --status 2>/dev/null \
                           && [ -f "$TOOLS_DIR/groovy/groovy-xml.jar" ] \
                           && echo "$GROOVY_XML_SHA256  $TOOLS_DIR/groovy/groovy-xml.jar" | sha256sum --check --strict --status 2>/dev/null; then
                            echo "Groovy $GROOVY_VERSION already present and verified, reusing"
                        else
                            curl -fsSL -o "$TOOLS_DIR/groovy/groovy.jar" "https://repo.maven.apache.org/maven2/org/apache/groovy/groovy/$GROOVY_VERSION/groovy-$GROOVY_VERSION.jar"
                            echo "$GROOVY_SHA256  $TOOLS_DIR/groovy/groovy.jar" | sha256sum --check --strict
                            curl -fsSL -o "$TOOLS_DIR/groovy/groovy-xml.jar" "https://repo.maven.apache.org/maven2/org/apache/groovy/groovy-xml/$GROOVY_VERSION/groovy-xml-$GROOVY_VERSION.jar"
                            echo "$GROOVY_XML_SHA256  $TOOLS_DIR/groovy/groovy-xml.jar" | sha256sum --check --strict
                        fi

                        if [ -x "$TOOLS_DIR/allure-$ALLURE_VERSION/bin/allure" ] \
                           && [ "$($TOOLS_DIR/allure-$ALLURE_VERSION/bin/allure --version)" = "$ALLURE_VERSION" ]; then
                            echo "Allure CLI $ALLURE_VERSION already present, reusing"
                        else
                            rm -rf "$TOOLS_DIR/allure-$ALLURE_VERSION"
                            curl -fsSL -o "$TOOLS_DIR/allure.tgz" "https://repo.maven.apache.org/maven2/io/qameta/allure/allure-commandline/$ALLURE_VERSION/allure-commandline-$ALLURE_VERSION.tgz"
                            echo "$ALLURE_SHA256  $TOOLS_DIR/allure.tgz" | sha256sum --check --strict
                            tar -xzf "$TOOLS_DIR/allure.tgz" -C "$TOOLS_DIR"
                        fi
                        "$TOOLS_DIR/allure-$ALLURE_VERSION/bin/allure" --version
                    '''
                    env.GROOVY_CP = "${env.WORKSPACE}/${env.TOOLS_DIR}/groovy/groovy.jar:${env.WORKSPACE}/${env.TOOLS_DIR}/groovy/groovy-xml.jar"
                }
            }
        }

        stage('Compile tests') {
            steps {
                script {
                    int rc = sh(script: './gradlew testClasses --console=plain', returnStatus: true)
                    env.COMPILE_OK = (rc == 0) ? 'true' : 'false'
                    if (rc != 0) {
                        currentBuild.result = 'FAILURE'
                        error("Compilation failed with exit code ${rc}")
                    }
                }
            }
        }

        stage('Run UI tests') {
            when { expression { env.COMPILE_OK == 'true' } }
            steps {
                // returnStatus: junit() below decides SUCCESS/UNSTABLE/FAILURE, not gradle's exit code.
                script {
                    sh(returnStatus: true, script: '''
                        ./gradlew test --console=plain \
                          -DbaseUrl=https://github.com \
                          "-Dbrowser=$BROWSER" \
                          "-DbrowserVersion=$BROWSER_VERSION" \
                          "-DbrowserSize=$BROWSER_SIZE" \
                          -Dheadless=true \
                          "-Dremote=https://$SELENOID_USER:$SELENOID_PASS@$SELENOID_URL"
                    ''')
                }
            }
            post {
                always {
                    // findFiles (Pipeline Utility Steps) isn't installed here; junit()'s own
                    // "no matching files" error covers the no-results case.
                    script {
                        try {
                            def results = junit(testResults: 'build/test-results/test/*.xml')
                            env.STATS_OK = 'true'
                            env.STATS_PASSED = "${results.totalCount - results.failCount - results.skipCount}"
                            env.STATS_FAILED = "${results.failCount}"
                            env.STATS_SKIPPED = "${results.skipCount}"
                        } catch (Exception exc) {
                            env.STATS_OK = 'false'
                            currentBuild.result = 'FAILURE'
                            error("No JUnit test results in build/test-results/test; tests did not run (${exc.message})")
                        }
                    }
                }
            }
        }

        stage('Generate Allure report') {
            when { expression { env.COMPILE_OK == 'true' } }
            steps {
                sh '''
                    set -e
                    if ! ls build/allure-results/*-result.json > /dev/null 2>&1; then
                        echo "No Allure test results in build/allure-results; the report is not generated" >&2
                        exit 1
                    fi
                    "$TOOLS_DIR/allure-$ALLURE_VERSION/bin/allure" generate build/allure-results --clean -o build/allure-report
                '''
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'build/allure-results/**, build/test-results/test/**, build/reports/tests/test/**, build/allure-report/**',
                              allowEmptyArchive: true,
                              fingerprint: false

            script {
                String status
                if (env.COMPILE_OK != 'true') {
                    status = 'build-failed'
                } else if (currentBuild.currentResult == 'UNSTABLE') {
                    status = 'tests-failed'
                } else if (currentBuild.currentResult == 'ABORTED') {
                    status = 'cancelled'
                } else if (currentBuild.currentResult == 'SUCCESS') {
                    status = 'success'
                } else {
                    status = 'build-failed'
                }

                // A notify failure (network, bad response, missing GROOVY_CP) must never change the build result.
                try {
                    withEnv([
                        "STATUS=${status}",
                        "PROJECT=${env.JOB_NAME}",
                        "CI_NAME=Jenkins",
                        "BRANCH=${env.GIT_BRANCH ?: 'unknown'}",
                        "COMMIT=${env.GIT_COMMIT ?: 'unknown'}",
                        "RUN_URL=${env.BUILD_URL ?: ''}",
                        "REPORT_URL=",
                        "STATS_OK=${env.STATS_OK ?: 'false'}",
                        "PASSED=${env.STATS_PASSED ?: '0'}",
                        "FAILED=${env.STATS_FAILED ?: '0'}",
                        "SKIPPED=${env.STATS_SKIPPED ?: '0'}"
                    ]) {
                        sh(script: 'java -cp "$GROOVY_CP" groovy.ui.GroovyMain ci/groovy/notify_telegram.groovy', returnStatus: true)
                    }
                } catch (Exception exc) {
                    echo "Telegram notification step could not run (${exc.message}); does not affect the build result"
                }
            }
        }
        cleanup {
            deleteDir()
        }
    }
}
