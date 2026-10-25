#!/usr/bin/env groovy
/*
 * Send a per-status Telegram notification about a CI run.
 * CI-agnostic: all inputs come from environment variables only (never
 * argv), so the bot token and chat id never appear in process listings
 * or logged command lines. Run with:
 *   java -cp groovy-5.0.6.jar;groovy-xml-5.0.6.jar groovy.ui.GroovyMain notify_telegram.groovy
 *
 * Exit codes:
 *   0 - message sent, or secrets missing (warning only, not a failure)
 *   1 - secrets present but sending failed (bad STATUS, HTTP error,
 *       ok=false, timeout) - the caller is expected to treat this as
 *       non-fatal so it never affects the overall run status.
 */
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

String env(String name, String dflt = '') {
    return System.getenv(name) ?: dflt
}

// Telegram legacy Markdown only treats these four characters as special.
String esc(String value) {
    return value.replaceAll(/([_*`\[])/, '\\\\$1')
}

String statusLabel(String status) {
    def labels = [
        'success'     : '✅ Success',
        'tests-failed': '❌ Tests failed',
        'build-failed': '🛑 Build failed before tests',
        'cancelled'   : '⚠️ Cancelled',
    ]
    if (!labels.containsKey(status)) {
        throw new IllegalArgumentException("unknown STATUS: '${status}'")
    }
    return labels[status]
}

String buildMessage() {
    String status = env('STATUS')
    String label = statusLabel(status)

    def lines = [
        "*${label}*",
        '',
        "*Project:* ${esc(env('PROJECT'))}",
        "*CI:* ${esc(env('CI_NAME', 'CI'))}",
        "*Branch:* ${esc(env('BRANCH'))}",
        "*Commit:* ${esc(env('COMMIT'))}",
        '',
    ]

    if (status in ['success', 'tests-failed']) {
        if (env('STATS_OK') == 'true') {
            lines << '*Tests:*'
            lines << "✅ Passed: `${esc(env('PASSED', '0'))}`"
            lines << "❌ Failed: `${esc(env('FAILED', '0'))}`"
            lines << "⏭ Skipped: `${esc(env('SKIPPED', '0'))}`"
        } else {
            lines << 'Tests: stats unavailable (JUnit XML missing or invalid)'
        }
    } else if (status == 'build-failed') {
        lines << 'Tests did not run (build/compile failed)'
    } else if (status == 'cancelled') {
        lines << 'Run was cancelled before completion'
    }

    lines << ''
    lines << "Run: ${esc(env('RUN_URL'))}"
    String reportUrl = env('REPORT_URL')
    if (reportUrl) {
        lines << "Report: ${esc(reportUrl)}"
    }

    return lines.join('\n')
}

String toJsonString(String s) {
    String escaped = s.replace('\\', '\\\\')
                       .replace('"', '\\"')
                       .replace('\n', '\\n')
                       .replace('\r', '')
    return "\"${escaped}\""
}

boolean send(String token, String chatId, String text) {
    int timeoutSeconds = 10
    String payload = /{"chat_id":"${chatId.replace('\\', '\\\\').replace('"', '\\"')}","text":${toJsonString(text)},"parse_mode":"Markdown","disable_web_page_preview":true}/
    def client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(timeoutSeconds))
        .build()
    // Overridable only for fixture tests against a local mock server; production always uses the real Telegram API.
    String apiBase = env('TELEGRAM_API_BASE', 'https://api.telegram.org')
    def request = HttpRequest.newBuilder(new URI("${apiBase}/bot${token}/sendMessage"))
        .timeout(Duration.ofSeconds(timeoutSeconds))
        .header('Content-Type', 'application/json')
        .POST(HttpRequest.BodyPublishers.ofString(payload))
        .build()

    try {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString())
        String body = response.body()
        if (response.statusCode() != 200) {
            println "::error::Telegram API returned unexpected HTTP status ${response.statusCode()}: ${body}"
            return false
        }
        if (!(body =~ /"ok"\s*:\s*true/)) {
            println "::error::Telegram API reported ok=false: ${body}"
            return false
        }
        println 'Telegram notification sent'
        return true
    } catch (java.net.http.HttpTimeoutException exc) {
        println "::error::Telegram API request timed out: ${exc.message}"
        return false
    } catch (Exception exc) {
        println "::error::Telegram API request failed: ${exc.message}"
        return false
    }
}

try {
    String text = buildMessage()
    String token = env('TELEGRAM_BOT_TOKEN')
    String chatId = env('TELEGRAM_CHAT_ID')
    if (!token || !chatId) {
        println '::warning::TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID is not set; skipping Telegram notification'
        System.exit(0)
    }
    System.exit(send(token, chatId, text) ? 0 : 1)
} catch (IllegalArgumentException exc) {
    println "::error::${exc.message}"
    System.exit(1)
}
