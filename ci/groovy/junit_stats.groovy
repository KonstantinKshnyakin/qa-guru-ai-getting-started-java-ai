#!/usr/bin/env groovy
/*
 * Aggregate JUnit XML test statistics from one or more result files.
 * CI-agnostic: reads only argv/files, writes only stdout and an optional
 * key=value output file. Run with:
 *   java -cp groovy-5.0.6.jar;groovy-xml-5.0.6.jar groovy.ui.GroovyMain junit_stats.groovy <results_dir> [--ci-output <path>]
 *
 * Direct invocation (fixture-based testing): exits 2 on "no files",
 * 3 on "invalid XML".
 * With --ci-output: always exits 0, writes ok=false to the output file
 * instead, so a stats-collection problem never fails the caller's job.
 */
import groovy.xml.XmlSlurper

def collectStats(String resultsDir) {
    def dir = new File(resultsDir)
    def xmlFiles = dir.exists() ? dir.listFiles({ f -> f.name.endsWith('.xml') } as FileFilter)?.sort { it.name } : null
    if (!xmlFiles) {
        return [ok: false, error: 'no_xml_files', dir: resultsDir]
    }

    int tests = 0, failures = 0, errors = 0, skipped = 0
    for (file in xmlFiles) {
        def root
        try {
            root = new XmlSlurper().parse(file)
        } catch (Exception exc) {
            return [ok: false, error: 'invalid_xml', file: file.path, detail: exc.message]
        }
        // '**' walks every descendant regardless of nesting depth, so
        // nested <testsuite> wrappers are never double-counted.
        root.'**'.findAll { it.name() == 'testcase' }.each { testcase ->
            tests += 1
            if (testcase.failure.size() > 0) {
                failures += 1
            } else if (testcase.error.size() > 0) {
                errors += 1
            } else if (testcase.skipped.size() > 0) {
                skipped += 1
            }
        }
    }

    int passed = tests - failures - errors - skipped
    return [
        ok      : true,
        tests   : tests,
        passed  : passed,
        failed  : failures + errors,
        failures: failures,
        errors  : errors,
        skipped : skipped,
    ]
}

def writeCiOutput(Map stats, String outputPath) {
    def lines = []
    lines << "ok=${stats.ok ? 'true' : 'false'}"
    if (stats.ok) {
        lines << "passed=${stats.passed}"
        lines << "failed=${stats.failed}"
        lines << "skipped=${stats.skipped}"
    } else {
        lines << "error=${stats.error ?: 'unknown'}"
    }
    new File(outputPath) << (lines.join('\n') + '\n')
}

def toJson(Map m) {
    def esc = { String s -> s.replace('\\', '\\\\').replace('"', '\\"') }
    def parts = m.collect { k, v ->
        def value = (v instanceof Boolean || v instanceof Number) ? v.toString() : "\"${esc(v.toString())}\""
        "\"${k}\":${value}"
    }
    return "{" + parts.join(',') + "}"
}

def cliArgs = args as List
if (cliArgs.size() < 1) {
    println toJson([ok: false, error: 'missing_dir_argument'])
    System.exit(2)
}

String resultsDir = cliArgs[0]
String ciOutputPath = null
int idx = cliArgs.indexOf('--ci-output')
if (idx >= 0 && cliArgs.size() > idx + 1) {
    ciOutputPath = cliArgs[idx + 1]
}

def stats = collectStats(resultsDir)
println toJson(stats)

if (ciOutputPath) {
    writeCiOutput(stats, ciOutputPath)
    System.exit(0)
}

if (!stats.ok) {
    System.exit(stats.error == 'invalid_xml' ? 3 : 2)
}
System.exit(0)
