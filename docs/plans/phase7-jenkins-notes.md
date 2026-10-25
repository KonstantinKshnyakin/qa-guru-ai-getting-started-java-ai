# Фаза 7 — Jenkins: детали реализации, проблемы и проверки

Подробности к статусу в [ui-test-project.md](ui-test-project.md#фаза-7--jenkins). Выполнено 2026-10-25 (короткий путь, Вариант B). Jenkinsfile на ветке `dz-oct-12-7`, репозиторий `KonstantinKshnyakin/qa-guru-ai-getting-started-java-ai`, job `id-kshnyakinkg-ai-first` на `https://jenkins.qa.guru`. Коммиты: `7d9f0b9`, `ccc3c84`, `33c02ea`, `65d3e78`, `66ed9aa`, `4238917`, `aff4092`, `5b6bb40`, `b51b637`, `263a781`, `372c998`.

Реальный `workflow_dispatch` в GitHub Actions с новым (Selenoid + Groovy) workflow — выполнен: [run 40195526762](https://github.com/KonstantinKshnyakin/qa-guru-ai-getting-started-java-ai/actions/runs/40195526762), job'ы `ui-tests` и `notify` — success, `deploy-report` — skipped (ожидаемо, не default branch). Первая попытка ([run 40195304082](https://github.com/KonstantinKshnyakin/qa-guru-ai-getting-started-java-ai/actions/runs/40195304082)) упала на `Run UI tests` с `AuthenticationFilter`/`SessionNotCreatedException` — выяснилось, что workflow ссылался на secrets `SELENOID_USER`/`SELENOID_PASS`, а реально заведённые в репозитории secrets называются `QA_GURU_SELENOID_USER`/`QA_GURU_SELENOID_PASS` (та же конвенция, что в Jenkins); остальные 18 шагов джоба `ui-tests` (checkout, Java, Gradle, Allure CLI, Groovy runtime, компиляция, сбор статистики, артефакты, отчёт) прошли ещё в той попытке — упал только сам прогон тестов из-за неверного имени secret. Исправлено (`372c998`), повторный запуск — success.

## Отклонения от исходного плана и почему

- **Браузер — через Selenoid (`selenoid.qa.guru`), а не локальный Chrome на агенте.** В плане фазы было явно решено не настраивать remote WebDriver. По факту: агент не имел Chrome/ChromeDriver и ни одной из ~24 нужных системных библиотек; скачивание Chrome for Testing + `apt-get install` реально заработали и были проверены, но пользователь по ходу реализации решил отказаться от `apt-get` на агенте и перейти на Selenoid. `build.gradle`/`TestBase.java` получили новые свойства `remote`/`browserVersion` (без Selenide-специфичных `selenide.*` имён — все дефолты централизованы в Java-коде, `build.gradle` только прокидывает заданное).
- **Скрипты статистики/уведомлений — Groovy (`ci/groovy/`), а не Python из фазы 6.** Причина — Jenkins CPS sandbox блокирует голые `XmlSlurper`/`HttpClient` без прав администратора (подтверждено `RejectedAccessException`), поэтому пришлось запускать Groovy как внешний процесс (`java -cp <jar> groovy.ui.GroovyMain`), а раз так — решили не тащить ещё и `python3` через apt. Старые `.github/scripts/*.py` удалены, GitHub Actions тоже переведён на `ci/groovy/*.groovy` (те же файлы для обоих CI).
- **Секреты — через `password`-параметры Jenkinsfile, а не Jenkins Credentials.** У аккаунта нет `Credentials/View` ни на одном уровне (подробности ниже) — обходной путь, не то же самое, что полноценное хранилище credentials.
- **Allure — только CLI 2.46.1, хотя на Jenkins установлен и сам Allure-плагин** (видны степы `allure`/`allure3`). Решили не использовать плагин — подробности ниже.

## Проблемы при реализации

### 1. Недоступность Jenkins Credentials (самая существенная находка)

Проверены все три уровня, реальными действиями, не предположением:

- **System/Global** — `/credentials/api/json` на корне возвращает `"stores":{}}`; `Manage Jenkins` недоступен роли `/students`.
- **Personal (User)** — credentials реально создаются и видны в UI (`TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`, `QA_GURU_SELENOID_USER`, `QA_GURU_SELENOID_PASS`), но `withCredentials` в Pipeline их **не находит**: `ERROR: Could not find credentials entry with ID 'QA_GURU_SELENOID_USER'`. Это задокументированное поведение самого Jenkins (User-scoped credentials не резолвятся в Pipeline ни при каком триггере), а не баг конфигурации.
- **Folder** — создали folder `id-kshnyakinkg-ai-first-folder` в надежде на folder-scoped store; API показал пустой store без дескриптора. Реальная проверка пользователем в UI дала точную причину: `kocmoc007 is missing the Credentials/View permission`. Folder впоследствии удалён.

Решение: `password`-параметры Jenkinsfile (`SELENOID_USER`, `SELENOID_PASS`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`) — Jenkins маскирует их в логе консоли так же, как Credentials, но значения вводятся вручную (или через API) при каждом запуске, а не хранятся централизованно. Зафиксировано как ограничение, не как решённая задача.

### 2. Allure2 CLI vs Allure-плагин на Jenkins (Allure3)

План фиксировал Allure Report 2 CLI 2.46.1 без Node.js. На Jenkins оказался установлен сам Allure Jenkins plugin — в списке доступных Pipeline-степов видны и `allure`, и `allure3`. Предлагали один раз подключить `allure(...)` степ для просмотра отчёта прямо в Jenkins UI — отклонено: нет `Manage Jenkins`, чтобы посмотреть реальную версию/настройки плагина и его совместимость с форматом результатов Report 2 CLI, которым мы и так пользуемся в GitHub Actions. Оставили только CLI 2.46.1 с тем же pinned SHA-256, что в GitHub Actions; отчёт доступен только как скачиваемый артефакт `build/allure-report`, не как кликабельная ссылка в Jenkins.

### 3. Shell-портируемость: `sh`-степы выполняются под dash, не bash

Три реальных бага, каждый найден по фактическому логу, не предположением:

- `[[ ! "$X" =~ regex ]]` → `/bin/sh: [[: not found`. Заменено на POSIX `case`.
- `compgen -G "pattern"` → `compgen: not found`; из-за этого шаг генерации Allure-отчёта **ложно падал даже когда результаты реально были** (build #14 — тесты реально прошли/упали штатно, но стадия отчёта ошибочно репортила "No Allure test results"). Заменено на `ls pattern > /dev/null 2>&1`.
- `findFiles(glob: ...)` — степ плагина Pipeline Utility Steps, которого на этом Jenkins нет (`NoSuchMethodError` со списком реально доступных степов). Убрали, положились на собственную обработку ошибки `junit()` при отсутствии результатов.

### 4. Сеть агента блокирует Telegram не полностью и не стабильно

`api.telegram.org` резолвится по DNS нормально, но TCP-соединение по большинству IP таймаутится. Проверены 5 IP, предложенных пользователем: `149.154.167.220` при первой проверке отвечал (HTTP 302), остальные 4 — нет. Реализован `TELEGRAM_RESOLVE_IPS` (используется только в Jenkinsfile, GitHub Actions не задействован) — `notify_telegram.groovy` при наличии этой переменной пробует каждый IP через `curl --resolve` (с конфиг-файлом, чтобы токен не светился в `ps`/аргументах процесса), иначе — обычный `HttpClient`.

**Важно:** при повторной проверке тот же `149.154.167.220` с того же агента **не ответил** (timeout). Это нестабильное, не гарантированное обходное решение, а не починенная проблема — зафиксировано как known limitation. Сбой notify изолирован и не влияет на статус сборки ни при каком исходе.

### 5. FAILURE/UNSTABLE — реальные баги в первой версии, найденные по факту

- Забыт `returnStatus: true` на `./gradlew test` — упавший assertion сразу зафейлил бы весь pipeline как FAILURE, `junit()` не смог бы понизить это до UNSTABLE (Jenkins не умеет улучшать результат). Исправлено до первого реального прогона.
- После упрощения (объединение стадий, удаление отдельной "Compile tests") — заново проверено на реальной ветке с синтаксической ошибкой: ошибка компиляции всё ещё корректно даёт FAILURE (через путь "нет JUnit XML"), а не UNSTABLE.
- Все 4 обязательных сценария (success, тесты упали → UNSTABLE, ошибка компиляции → FAILURE, нет результатов → FAILURE) прогнаны на реальном Jenkins, причём сценарий ошибки компиляции и общий success-путь перепроверены повторно после упрощения кода.

### 6. GitHub Actions: неверные имена secrets при переносе workflow на Selenoid

При миграции `.github/workflows/ui-tests.yml` на Selenoid workflow сослался на `secrets.SELENOID_USER`/`secrets.SELENOID_PASS`, а в репозитории реально заведены `QA_GURU_SELENOID_USER`/`QA_GURU_SELENOID_PASS` (та же конвенция имён, что в Jenkins). GitHub Actions не ругается на обращение к несуществующему secret — просто подставляет пустую строку, из-за чего `-Dremote=https://:@selenoid.qa.guru/wd/hub` ушёл с пустыми credentials и упал с той же `AuthenticationFilter`/`SessionNotCreatedException`, что и при заведомо неверном пароле в ручном тесте. Все остальные 18 шагов джоба `ui-tests` (checkout, Java, Gradle, Allure CLI, Groovy runtime, компиляция, сбор статистики, артефакты, отчёт) в той же попытке прошли успешно. Исправлено (`372c998`), повторный `workflow_dispatch` — success.

## Согласованные решения (этап A)

- Доступ к Jenkins: API/CLI-доступ как у `kocmoc007` (роль `/students`); создание/настройка job, folder, build — через API; Credentials — только вручную пользователем в UI, и то ограниченно (см. выше).
- Агент: label `java-jdk21` (любой из `java-jdk21-jenkins-agent-1..5`), Debian 13, Java 21.0.12 Temurin предустановлена; `python3`, `docker`, Chrome — отсутствуют.
- Параметры Jenkinsfile: `BROWSER` (choice, только `chrome`), `BROWSER_VERSION` (choice `156.0`/`155.0`/`148.0`, дефолт `156.0` — единственная реально проверенная версия на Selenoid), `BROWSER_SIZE` (string, дефолт `1920x1080`, без строгой валидации формата), плюс 4 `password`-параметра для секретов.
- Чистота агента: `buildDiscarder(logRotator(numToKeepStr: '3'))` — последние 3 build'а; `post { cleanup { deleteDir() } }` — workspace полностью стирается после каждого прогона (скачанные Groovy/Allure, результаты — ничего не остаётся). Системные apt-пакеты (были нужны только для локального Chrome, который впоследствии убрали) на этом принципе не основаны — их установка не потребовалась в финальной версии.

## Реализация и проверка (этап B–C)

- Jenkinsfile: 3 стадии (`Prepare tools`, `Run UI tests`, `Generate Allure report`) + именованный пост-шаг `Archive and notify` вместо generic `Declarative: Post Actions`. Те же команды, что локально и в GitHub Actions (`./gradlew test ...`, `allure generate ...`).
- Публикация: `junit()` степ на `build/test-results/test/*.xml`; `archiveArtifacts` на `allure-results`, `test-results`, `reports/tests/test`, `allure-report` (`allowEmptyArchive: true`).
- Реальные прогоны на `java-jdk21-jenkins-agent-1`/`-2` (все через API, с результатами, сверенными по консольному логу):
  - **SUCCESS** — build #22 (первая версия), #25, #30, #31 (после всех упрощений): компиляция, 3 теста через Selenoid (Chrome 156.0), Allure-отчёт, архивация — все зелёные.
  - **UNSTABLE** (пустые Selenoid-креды → все 3 теста падают) — build #15, #26.
  - **FAILURE, ошибка компиляции** — build #17 (исходная версия), build #28 (после удаления отдельной стадии Compile tests, на новой ветке `ci-check/jenkins-compile-fail-2`).
  - **FAILURE, нет результатов** (`doFirst` в test-таске падает раньше первого теста) — build #19.
  - Негативный контроль Selenoid: заведомо неверный пароль → `SessionNotCreatedException`/`AuthenticationFilter` — подтверждает, что успешные прогоны реально идут через Selenoid, а не тихий fallback.
- `ci/groovy/junit_stats.groovy` — проверен на фикстурах (success/failure/error/skipped/nested-testsuite/multi-file/missing-dir/broken-XML, режим `--ci-output`), локально с Java 21 и Java 25.
- `ci/groovy/notify_telegram.groovy` — проверен на локальном mock HTTP-сервере (4 статуса, `ok=false`, HTTP 401, connection-refused/timeout, экранирование спецсимволов) и дважды реальной отправкой (одна — непреднамеренная, см. ниже; вторая, с путём `TELEGRAM_RESOLVE_IPS`, не прошла из-за сетевой нестабильности, см. раздел 4).
- Временные ветки `ci-check/jenkins-compile-fail`, `ci-check/jenkins-no-results`, `ci-check/jenkins-compile-fail-2` — созданы, использованы, удалены (локально и на GitHub) после проверки.
- GitHub Actions (новый workflow на Selenoid + Groovy): реальный `workflow_dispatch` на `dz-oct-12-7`, [run 40195526762](https://github.com/KonstantinKshnyakin/qa-guru-ai-getting-started-java-ai/actions/runs/40195526762) — `ui-tests` и `notify` success, `deploy-report` skipped (не default branch). Первая попытка падала из-за неверных имён secrets — см. "Проблемы при реализации".

## Ретроспектива: где агент ошибся и как это обнаружилось

- **Дважды напечатал реальные секреты в чат** при диагностике: один раз полез смотреть `env | grep -i telegram`, не ожидая, что `TELEGRAM_BOT_TOKEN`/`TELEGRAM_CHAT_ID` уже есть в окружении (не от меня — persistent-переменная вне сессии); второй раз — проверяя `unset` в следующем вызове Bash (переменные оказались инжектируемыми заново в каждый новый процесс, `unset` не помогал). Из-за первого случая один тестовый прогон, задуманный как "без секретов", реально отправил сообщение в боевой Telegram-чат — поймано по собственному выводу команды, не пользователем. Исправлено правилом: больше не читать и не печатать эти переменные, явно подставлять пустые/мок-значения в фикстурных тестах.
- **Предположил, что `-Dselenide.browserBinary` и подобные свойства читаются Selenide автоматически по конвенции `selenide.<имяПоля>`** — оказалось верно, но вместо того чтобы доверять старому выводу плана, перепроверил реальным поиском строк в скомпилированном `selenide-7.18.2.jar` (строки `selenide`/`browserBinary`/`remote` раздельно, собираются через reflection) — поймал бы более раннюю ошибку, если бы сразу доверился документации без проверки.
- **Три shell-бага (`[[`, `compgen`, `findFiles`) были бы пойманы раньше**, если бы Jenkinsfile с самого начала тестировался на реальном агенте, а не писался по аналогии с bash-синтаксисом GitHub Actions runner'ов (Ubuntu, где `sh` часто линкован на bash-совместимый `dash`, но `[[`/`compgen` всё равно недоступны и там — эта аналогия была ошибочной с самого начала, не только для Jenkins). Поймано реальными прогонами, не ревью кода.
- **`id-kshnyakinkg-ai-first-folder` создан без проверки, что Credentials вообще будут доступны** — стоило сначала спросить пользователя проверить права в UI, а не тратить raunds на угадывание через API (которое давало только частичную картину — пустой `stores:{}` без явной причины).
- **В GitHub Actions workflow сослался на secrets `SELENOID_USER`/`SELENOID_PASS`, не сверившись с именами, которые пользователь реально завёл** (`QA_GURU_SELENOID_USER`/`QA_GURU_SELENOID_PASS`) — чистая невнимательность при переносе из Jenkinsfile (там были `password`-параметры с короткими именами). Поймано только реальным прогоном `workflow_dispatch` (пустые secrets подставились молча, без ошибки GitHub — упало только на самой авторизации Selenoid), а не осмотром кода перед запуском.

## Ограничения и непроверенное

- **Telegram с Jenkins — нестабильно по сети**, не гарантированная функция; не считать "починенной".
- Agent'ы `java-jdk21-jenkins-agent-3/4/5` и `java-jdk21-jenkins-warm-agent-1` не проверялись индивидуально (прогоны попадали на `-1`/`-2`) — для Selenoid-архитектуры это не критично (нет больше локальных зависимостей от конкретного агента), но не проверено явно.
- `BROWSER_VERSION=156.0` выбран как единственная реально проверенная версия Selenoid; `155.0`/`148.0` в выпадающем списке присутствуют, но не прогонялись.
- Точная причина сетевой блокировки Telegram/нестабильности `149.154.167.220` не установлена (файрвол курса, временное правило и т.д.) — не наша зона ответственности и не диагностировалась глубже прямых `curl`-проверок.
