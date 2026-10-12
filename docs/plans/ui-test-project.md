# План развития учебного проекта UI-автотестов

## Назначение

Постепенно добавить TestBase, независимые сценарии, Page Object, Allure-отчёты (по решению фазы 4 — генератор Allure Report 2), GitHub Actions, Telegram и Jenkins, сохраняя существующий Java-стек.

В этом документе хранятся согласованные решения, статусы фаз, результаты проверок, ограничения и открытые вопросы. Общие правила работы агента находятся отдельно в [AGENTS.md](../../AGENTS.md). После каждой фазы обновлять этот план; не отмечать выполнение без подтверждённых результатов.

## Согласованные решения

- Java 21 через Gradle Java toolchain; Gradle Wrapper 9.6.0, bin-дистрибутив.
- Selenide 7.18.2, JUnit Jupiter 6.1.3; исходно разрешённый Selenium — 4.49.0. Версии зависимостей без согласования не менять.
- Defaults: baseUrl=https://github.com, browser=chrome, browserSize=1920x1080, headless=false.
- Переопределение через -DbaseUrl, -Dbrowser, -DbrowserSize, -Dheadless с явной передачей этих свойств из Gradle в JVM тестов. Дополнительно (фаза 5) Gradle передаёт `selenide.browserBinary` и `webdriver.chrome.driver` только если они заданы; без них локальное поведение не меняется.
- В CI явно передавать headless=true.
- Page Object: fluent API; проверки в should…-методах страниц; private final SelenideElement-поля; имена методов описывают пользовательские действия.
- Отдельные компоненты выделять только при реальном повторении блоков. Не добавлять BasePage, фабрики и сложную конфигурационную систему без необходимости.
- Структура Page Object: packages `tests` (SearchTests, TestBase) и `pages` (SearchPage, SearchResultsPage). open("/search") живёт в SearchPage.open() и остаётся относительным к baseUrl; assertions — в should…-методах страниц (shouldHaveResult, shouldBeEmpty) с исходными локаторами и порядком проверок.
- Отчёты: генератор — Allure Report 2 CLI 2.46.1 (локально через Homebrew; в CI — официальный tgz с Maven Central с закреплённым SHA-256); Node.js и npm-зависимости в проект не вводятся (решение пользователя на этапе A фазы 4 вместо изначального Report 3).
- JVM-адаптеры: Allure Java 3.0.0 — `allure-jupiter` (JUnit 5 и 6) + `allure-selenide` (Selenide 7.x), версии выравниваются через `allure-bom`; формат результатов совместим с генератором Report 2.
- Шаги в отчёте: `@Step` на методах страниц (требует `-javaagent:aspectjweaver` на JVM тестов, версия 1.9.25) + технические шаги Selenide от `AllureSelenide` (вкладываются в пользовательские). Вложения — Screenshot и Page source только при падении (дефолты `AllureSelenide`); listener регистрируется в `@BeforeAll` TestBase — это выполняется на каждый тестовый класс, а не один раз на JVM; повторный вызов `addListener("allure", …)` заменяет listener с тем же именем.
- Результаты Allure — `build/allure-results` (`src/test/resources/allure.properties`), отчёт — `build/allure-report`; оба каталога внутри `build/` и не попадают в git.

## Текущее состояние

Последняя сверка файлов и git: 2026-10-12.

- Фаза 0 завершена; исходный запуск проверен в этой переписке.
- Фаза 1 завершена по отчёту пользователя. Коммит e55ae45ae599cdfa83cfac608dbe96b42d0a3916 и соответствующая конфигурация подтверждены чтением репозитория; браузерные проверки фазы 1 при сохранении плана не повторялись.
- Реализация и проверки тестов фазы 2 подтверждены исходниками, коммитом 3cefd221898c108ba538f5daee1ea4c30e76d615 и предоставленным отчётом фазы 3: все три теста выполнены отдельно, с defaults и в headless. Сведения о первоначальном согласовании сценариев и риск данных второго негативного сценария остаются открытыми.
- Фаза 3 завершена по отчёту пользователя, коммит 1312c8da32be42e5351af1334cf0bf035c65ab54 подтверждён в истории: локаторы и взаимодействия перенесены в pages.SearchPage и pages.SearchResultsPage, тесты и TestBase перемещены в package tests. Структура и сохранение assertions подтверждены чтением файлов; браузерные проверки при обновлении плана не повторялись.
- Фаза 4 завершена в этой сессии: Allure Java 3.0.0 (адаптеры) + Allure Report 2.46.1 CLI (генератор); проверки и ограничения — в разделе фазы 4.
- Фаза 5 в работе: workflow и локальные проверки — в разделе фазы 5; статус реального CI указан там же.
- Фазы 6–8 не начаты.

## Фаза 0 — Baseline

Статус: завершена с зафиксированными ограничениями.

- [x] Изучить сборку, тесты и настройки, проверить git.
- [x] Проверить wrapper: исходно отсутствовал.
- [x] Определить фактические версии и требования Java: Selenide и JUnit требуют Java 17+, локально использовалась Java 21.
- [x] Запустить исходный тест без изменения кода: BUILD SUCCESSFUL, задача test выполнена с --rerun-tasks.
- [x] Согласовать параметры, Java, wrapper и стиль Page Object.

Проверенная команда из корня репозитория:

```bash
gradle test --tests SearchTests.successfulSearchTest --rerun-tasks --console=plain
```

Ограничения: один успешный запуск не доказывает стабильность внешнего GitHub; запрос qa.guru зависит от изменяемых данных. Чтение JUnit XML в build/ блокировалось правилами IDE, даже после запроса доступа.

## Фаза 1 — TestBase

Статус: завершена по отчёту пользователя.

Коммит: e55ae45ae599cdfa83cfac608dbe96b42d0a3916 — Make UI tests reproducible and isolate browser sessions.

- [x] Добавить штатный Gradle Wrapper 9.6.0 с официальным checksum и executable bit для gradlew.
- [x] Зафиксировать Java 21 toolchain.
- [x] Добавить TestBase: настройка Selenide в @BeforeEach, closeWebDriver в @AfterEach.
- [x] Передавать только четыре согласованных свойства в JVM тестов.
- [x] Подключить TestBase к SearchTests и заменить абсолютный URL на /search.
- [x] Проверить defaults, переопределение свойств и использование baseUrl внутри JVM тестов.
- [x] Проверить отсутствие запуска браузера при настройке, изоляцию сессий и cleanup после успеха и контролируемого падения.
- [x] Удалить временную диагностику, восстановить исходный assertion, повторить успешные запуски и создать отдельный коммит без push.

По отчёту оба финальных запуска завершились BUILD SUCCESSFUL:

```bash
./gradlew test --tests SearchTests.successfulSearchTest --rerun-tasks --console=plain
./gradlew test --tests SearchTests.successfulSearchTest -DbaseUrl=https://www.github.com -Dbrowser=chrome -DbrowserSize=1366x768 -Dheadless=true --rerun-tasks --console=plain
```

Ограничения: macOS ограничила высоту видимого окна до 1050 при настройке 1920x1080; Selenium предупреждал о CDP Chrome 155 и поддерживаемой версии 153. Windows, другие браузеры и CI не проверялись. XML не читался; диагностика получена из stdout.

## Фаза 2 — Tests

Статус: реализация и выполнение тестов подтверждены; первоначальное согласование сценариев и оценка устойчивости данных остаются открытыми.

Коммит: 3cefd221898c108ba538f5daee1ea4c30e76d615 — Add negative GitHub search tests for deterministic empty results.

Источник результатов запуска — предоставленный пользователем отчёт фазы 3. При обновлении плана тесты не перезапускались. Отсутствие сведений о согласовании в этой сессии не означает, что согласование не проводилось.

### Этап A — исследование и согласование, без изменения кода

- [ ] Зафиксировать первоначальное подтверждение пользователем двух дополнительных сценариев.
- [x] Проверить доступность сценариев в реальном UI: по отчёту фазы 3 все три теста выполнены и прошли после восстановления доступности GitHub.
- [ ] Зафиксировать обоснование целей, предусловий, данных, assertions, локаторов и рисков обоих дополнительных сценариев.
- [ ] Подтвердить отсутствие зависимости от неконтролируемых данных для второго негативного сценария.

В реализованных тестах используются noResultsForImpossibleStarsRangeTest (stars:<0) и noResultsForHugeStarsCountTest (stars:>999999999). Проверяются тексты empty state и отсутствие results-list, а не случайный порядок выдачи. Огромный порог stars сам по себе не доказывает логическую невозможность совпадений; этот риск сохраняется. Поисковые данные и assertions не изменять в рамках обновления плана или подключения Allure.

### Этап B — реализация и проверка

- [x] Добавить два независимых теста в SearchTests; сведения о первоначальном согласовании отмечены отдельно выше.
- [x] Сохранить исходный сценарий: qa.guru и проверку текста QA.GURU.
- [x] Использовать TestBase, относительный /search, ожидания Selenide и содержательные assertions.
- [x] Проверить каждый новый тест отдельно, весь набор с defaults и в headless — по отчёту фазы 3.
- [x] Установить причину наблюдённого падения: GitHub HTTP 429, по отчёту фазы 3; после снятия ограничения набор прошёл без изменения кода.
- [x] Удалить временную диагностику и создать отдельный коммит реализации; в текущих tracked Java-файлах временной диагностики нет.

Итог по отчёту фазы 3: tests=3, failures=0, skipped=0 в успешном полном запуске. HTTP 429 и зависимость от внешних данных остаются ограничениями, а не устранёнными дефектами.

## Фаза 3 — Page Object

Статус: завершена по предоставленному пользователем отчёту.

Коммит: 1312c8da32be42e5351af1334cf0bf035c65ab54 — Extract GitHub search page objects from tests.

- [x] Подтвердить применимость согласованного стиля к фактическим сценариям.
- [x] Перенести взаимодействия и локаторы в страницы, проверки — в should…-методы.
- [x] Проверить необходимость компонентов: повторяющихся блоков нет, компоненты и BasePage не добавлены.
- [x] Сохранить сценарии и поведение; проверить весь набор и закоммитить изменения.

Итоговая структура: `tests.SearchTests`, `tests.TestBase`, `pages.SearchPage` (open + searchFor), `pages.SearchResultsPage` (shouldHaveResult, shouldBeEmpty). Локаторы, тестовые данные, assertions и порядок проверок сохранены без изменений; это подтверждено сравнением исходников с коммитом фазы 2. Полный переход на пакеты tests и pages подтверждён пользователем в отчёте этапа A.

По отчёту фазы 3 выполнены следующие проверки:

- Каждый тест отдельно: `./gradlew test --tests "tests.SearchTests.<method>"` для трёх методов — BUILD SUCCESSFUL, tests=1, failures=0 в каждом запуске.
- Полный набор: `./gradlew test --rerun-tasks --console=plain` — BUILD SUCCESSFUL, 3/3.
- Headless: `./gradlew test -Dheadless=true -DbrowserSize=1366x768 --rerun-tasks --console=plain` — первый запуск упал на третьем тесте с GitHub HTTP 429; после восстановления доступности повторный полный запуск прошёл, tests=3, failures=0, skipped=0.
- Причина 429 установлена по странице ошибки и подтверждена curl с того же IP; успешный запуск после снятия лимита выполнен без изменения кода. Это не автоматический ретрай и не устранение внешнего ограничения.
- В отчёте фазы 3 проверка git diff --check прошла, временная диагностика отсутствовала, после коммита дерево было чистым.

При этом обновлении плана подтверждены история и исходники, но UI-тесты, JUnit XML и сетевые ответы повторно не проверялись. Обнаруженные текущие незакоммиченные изменения форматирования page-классов не затронуты и в коммит плана не входят.

Ограничения: внешний GitHub может отвечать HTTP 429 при частых запросах; точный порог и длительность ограничения не установлены. Windows, другие браузеры и реальный CI не проверялись. Предупреждение CDP сохраняется.

## Фаза 4 — Allure Report 3

Статус: завершена с изменением решения — генератор Allure Report 2 вместо Report 3.

На этапе A пользователь отклонил Node.js-зависимость генератора Report 3 (npm `allure` 3.x) и выбрал Report 2 через Homebrew. Название фазы оставлено как в исходном плане; фактический генератор — Allure Report 2 CLI 2.46.1. JVM-адаптеры — Allure Java 3.0.0.

- [x] Разделить JVM-адаптеры Allure и CLI-генератор отчёта: адаптеры пишут `build/allure-results`, brew `allure` собирает HTML в `build/allure-report`.
- [x] Проверить совместимость с фактическими Java 21, JUnit 6 и Selenide: allure-java 3.0.0 требует Java 17+ и заявляет работу с JUnit 6 и 5; allure-selenide нацелен на Selenide 7.x; формат результатов Allure Java 3 читается Report 2 (официальный migration guide). Понижать Java, JUnit или Selenide не потребовалось.
- [x] Выбрать и зафиксировать проверенные версии: allure-bom/allure-jupiter/allure-selenide 3.0.0 (релиз 2026-09-11), aspectjweaver 1.9.25 (2025-11-04), CLI `allure` 2.46.1 (обновлён с 2.36.0 через brew по запросу пользователя).
- [x] Проверить актуальную документацию и встроенный --help: `allure --version` → 2.46.1; команды `generate` (`-o/--report-dir`, `--clean`, `--single-file`), `open` (`-h`, `-p`), `serve`; отдельного `--help` у субкоманд в 2.x нет.
- [x] Согласовать шаги и вложения: `@Step` на методах страниц + технические шаги Selenide (выбор пользователя), Screenshot + Page source при падении (дефолты AllureSelenide).
- [x] Сгенерировать и открыть отчёт, проверить диагностику контролируемого падения.
- [x] Восстановить успешное состояние, исключить generated artifacts из git и создать коммит.

Проверки этой сессии (2026-10-12):

- `./gradlew test --rerun-tasks --console=plain` — BUILD SUCCESSFUL, 3/3 passed; создано 3 `*-result.json` + контейнеры в `build/allure-results`.
- Дерево шагов в результатах: пользовательские `@Step` с подстановкой параметров («Search for "qa.guru"») и вложенные технические шаги Selenide (`$("open")`, `set value`, `should have`, `webdriver create/close`).
- `allure generate build/allure-results --clean -o build/allure-report`; `allure open build/allure-report -h 127.0.0.1 -p 45624` — сервер отвечает HTTP 200; в данных отчёта (`suites.json`, `test-cases/*.json`) все 3 теста passed с полным деревом шагов. UI показан пользователю через browser preview.
- Контролируемое падение: в `successfulSearchTest` ожидаемый текст временно заменён на `QA.GURU-NO-SUCH-TEXT`. Gradle вернул exit code 1 (BUILD FAILED); причина — намеренный assertion `Element should have text "QA.GURU-NO-SUCH-TEXT"`, не HTTP 429. На упавшем шаге записаны Screenshot (PNG) и Page source (HTML), файлы вложений существуют на диске; stack trace присутствует. После падения браузерных процессов не осталось; `webdriver close()` записан как passed.
- Изоляция прогонов: `build/allure-results` очищался перед прогоном падения и перед финальным прогоном; отчёт падения собран отдельно в `build/allure-report-failure`.
- Финальное состояние: assertion восстановлен, `./gradlew test --rerun-tasks` — 3/3 passed, exit code 0; отчёт пересобран в `build/allure-report`.

Ограничения: версия CLI не фиксируется в репозитории (brew-установка глобальна; на момент проверки — 2.46.1), lock-файла нет — осознанный компромисс отказа от Node.js. Отчёт был открыт пользователю через browser preview, но явной визуальной приёмки от пользователя не зафиксировано (исправлено при фазе 5); данные отчёта проверены программно (HTTP 200 и JSON), что визуальную приёмку не заменяет. Windows/Linux и реальный CI не проверялись; внешний GitHub может отвечать HTTP 429.

## Фаза 5 — GitHub Actions

Статус: workflow реализован и проверен локально; реальный запуск GitHub Actions — см. «Проверка в реальном GitHub Actions».

Файлы: [.github/workflows/ui-tests.yml](../../.github/workflows/ui-tests.yml), `build.gradle` (необязательная передача `selenide.browserBinary` и `webdriver.chrome.driver`). Коммит реализации: d473a0242d2952534fe9106f1398ebebe76522d3.

### Согласовано на этапе A (2026-10-12)

- [x] События: только `workflow_dispatch`; push, pull_request и расписание не используются.
- [x] Публикация: артефакты (retention 14 дней) и публичный GitHub Pages (source = GitHub Actions). Pages публикуется только для запусков из default branch при успешно сгенерированном отчёте, в том числе при упавших тестах.
- [x] Inputs: `browser` (choice, только `chrome`) и `browserSize` (`^[0-9]{3,4}x[0-9]{3,4}$`). baseUrl зафиксирован как https://github.com, выбор тестов не предусмотрен, headless=true всегда.
- [x] Браузер: Chrome for Testing 153.0.8010.52 + ChromeDriver той же версии (совпадает с поддержкой CDP в Selenium 4.49.0 — v151–153); пути передаются в тесты через Gradle-свойства.
- [x] Репозиторий для реального CI: отдельный публичный `qa-guru/getting-started-java-ai` (remote `ai`); `origin` (qa-guru/getting-started-java) не меняется.
- [x] GitHub secrets в фазе 5 не используются. Имена secrets/credentials, Telegram-уведомления (из каких CI, для каких исходов), расположение Jenkins и агента (ОС, label, инструменты) пока неизвестны — открытые вопросы фаз 6–7.

### Решения workflow

- Runner `ubuntu-24.04` (не `-latest`), `timeout-minutes: 30`.
- Actions закреплены по SHA (версии опубликованы не позже чем за 7 дней до выбора):
  - `actions/checkout` v7.0.1 `3d3c42e5aac5ba805825da76410c181273ba90b1`, `persist-credentials: false`;
  - `actions/setup-java` v6.0.1 `de7274f081f381c8f8158605e0321c36c376e2e6`, Temurin 21;
  - `gradle/actions/setup-gradle` v6.4.0 `3f5f9adaf7d9fecd50b5935e54106014257a94e6`;
  - `browser-actions/setup-chrome` v2.2.0 `48ad923757ca74d66703209fe939badbdf80f2f4`;
  - `actions/upload-artifact` v7.0.1 `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` (v7.0.2 моложе 7 дней на момент выбора);
  - `actions/upload-pages-artifact` v5.0.0 `fc324d3547104276b827a68afc52ff2a11cc49c9`;
  - `actions/deploy-pages` v5.0.1 `368f82528645a54fb793d4d04e342629a3f51346`.
- Allure CLI: `https://repo.maven.apache.org/maven2/io/qameta/allure/allure-commandline/2.46.1/allure-commandline-2.46.1.tgz`, SHA-256 `37428164f310c8d137cfdd2c3a25d049b233261ddab13e1bd4dfd7627b5cbf8d` — совпадает с digest ассета `allure-2.46.1.tgz` в GitHub Release allure-framework/allure2 и с `.sha1` Maven Central. Генератор использует `JAVA_HOME` из setup-java; workflow проверяет Java 21, совпадение версий Chrome/ChromeDriver и версию Allure.
- Permissions: `contents: read` на уровне workflow; `pages: write` и `id-token: write` только у job `deploy-report`. `pull_request_target` и secrets не используются.
- Concurrency: одна глобальная группа `ui-tests-github-search` без отмены текущего запуска — снижает риск HTTP 429. GitHub держит не больше одного ожидающего запуска в группе, более ранний ожидающий отменяется. Матрицы, параллельных тестов и ретраев нет.
- Inputs передаются только через `env` и процитированные аргументы, после проверки по allowlist/regex.
- Шаги: compile (`./gradlew testClasses`) отдельно от `./gradlew test`. После успешной компиляции, даже при падении тестов, загружаются `allure-results` и `junit-and-gradle-report` (`build/test-results/test`, `build/reports/tests/test`), затем генерируется отчёт. Без `*-result.json` генерация явно падает, пустой отчёт не создаётся. Загрузка сырых результатов идёт до генерации, поэтому ошибка генерации её не блокирует. Без `continue-on-error`, `|| true` и `tee`.
- Каждый запуск идёт на чистом runner; `build/` не кэшируется, чужие артефакты не скачиваются.

Ожидаемое поведение:

| Сценарий | Итог job | Артефакты |
|---|---|---|
| Тесты прошли | success | results, JUnit/Gradle HTML, report; Pages для default branch |
| Падение assertion | failure (exit code Gradle) | то же, с Screenshot и Page source в results |
| Ошибка компиляции | failure на шаге Compile tests | нет |
| Ошибка генерации | failure на шаге Generate | results и JUnit/Gradle HTML |
| Нет результатов | failure на шаге Generate, `::error::No Allure test results` | только то, что создано |

### Локальная проверка (macOS, 2026-10-12)

- `actionlint` 1.7.12 (release-бинарь с проверкой checksum, вне проекта) со встроенным shellcheck — 0 ошибок.
- Передача свойств через Gradle (временный init-скрипт в /tmp, `test --dry-run`): без свойств — только четыре исходных значения; с `-Dselenide.browserBinary="/opt/c t/chrome" -Dwebdriver.chrome.driver=…` оба значения доходят до `Test.systemProperties`, путь с пробелом сохраняется. Selenide 7.18.2 читает `selenide.browserBinary` (`SelenideConfig`), Selenium 4.49.0 — `webdriver.chrome.driver`.
- Скрипт установки CLI: скачивание с Maven Central, `allure --version` → 2.46.1, `JAVA_HOME` = Java 21. `sha256sum --check --strict` локально не проверен: на macOS BSD-утилита без `--strict`, GNU coreutils и Docker недоступны. Сам checksum сверен через `shasum -a 256`.
- Логика «нет результатов» на пустом каталоге выдаёт ошибку; `allure generate` на результатах фазы 4 → summary passed=3, total=3.
- UI-тесты локально не перезапускались (риск 429; Java-код не менялся).
- Linux-окружение локально не проверено: Docker daemon не запущен.

## Фаза 6 — Telegram

### Перед реализацией согласовать

- [ ] Нужны ли уведомления из GitHub Actions и Jenkins и для каких исходов.
- [ ] Имена secrets/credentials, поведение при отсутствии секретов и ошибке Telegram.
- [ ] Разрешение на реальную отправку сообщения.

### Реализация и проверка

- [ ] Использовать secrets окружения без хардкода и логирования.
- [ ] Брать passed/failed/skipped из проверенного формата JUnit XML; не опираться на непроверенные внутренние файлы Allure-отчёта.
- [ ] Отличать сбой сборки до запуска тестов от успешного запуска с нулём тестов.
- [ ] Включить проект, CI, branch/commit, статус тестов, статистику и ссылку на запуск.
- [ ] Проверять HTTP-ответ и результат Telegram API; не маскировать исходное падение ошибкой уведомления.
- [ ] Проверить согласованные случаи без реальной отправки, затем выполнить разрешённую отправку; создать коммит.

## Фаза 7 — Jenkins

### Перед реализацией уточнить

- [ ] Где находятся Jenkins и агент, доступ, ОС, label и доступные инструменты.
- [ ] Доступность Java, браузеров, драйверов и Allure Report 2 CLI 2.46.1 (Node.js проекту не нужен).
- [ ] Параметры запуска, имена Jenkins Credentials и Telegram-уведомления.
- [ ] Способ генерации и публикации Allure Report 2 (CLI с закреплённым checksum, как в GitHub Actions, или Allure Jenkins plugin).

### Реализация и проверка

- [ ] Добавить Jenkinsfile с теми же командами тестов и генерации отчёта, что локально и в GitHub Actions.
- [ ] Публиковать JUnit XML, сохранять результаты, вложения и отчёт при падениях.
- [ ] Проверить выбранный способ генерации Report 2 на реальном агенте; не предполагать наличие плагина или CLI.
- [ ] При отсутствии совместимости согласовать альтернативную генерацию и хранение отчёта.
- [ ] Не устанавливать плагины и не менять Jenkins глобально без разрешения.
- [ ] Разделить проверку Jenkinsfile и реальный запуск Jenkins; создать коммит.

## Фаза 8 — Review

- [ ] Проверить запуск из чистого checkout без удаления или изменения текущего WIP.
- [ ] Проверить параметры окружения и независимость тестов.
- [ ] Проверить отсутствие секретов и generated artifacts в git.
- [ ] Проверить статусы при падении тестов, ошибке сборки, отсутствии результатов и ошибке уведомления.
- [ ] Перечислить отдельно проверенное локально, в реальном GitHub Actions, в реальном Jenkins и оставшееся непроверенным.
- [ ] Обновить план и создать финальный коммит, если есть свои проверенные изменения.

## Базовые команды проверки

Команды выполняются из корня Git-репозитория. По отчёту фазы 3 полный набор из трёх тестов проверен с defaults и в headless после восстановления доступности GitHub. Эти результаты не гарантируют доступность внешнего сайта при следующем запуске.

```bash
./gradlew --version
./gradlew test --rerun-tasks --console=plain
./gradlew test -Dheadless=true -DbrowserSize=1366x768 --rerun-tasks --console=plain
allure --version
allure generate build/allure-results --clean -o build/allure-report
allure open build/allure-report
git diff --check
```

При изменении только этого файла достаточно проверить содержимое Markdown, актуальность статусов, whitespace и состав коммита; UI-тесты ради изменения плана запускать не требуется.

## Критерий завершения фазы

1. Выполнен именно согласованный объём.
2. Проверки действительно запущены и результаты зафиксированы.
3. Временная диагностика удалена, ограничения и блокеры перечислены.
4. Этот план обновлён без превращения предположений в подтверждённые результаты.
5. Свои изменения закоммичены; чужой WIP не затронут.
6. Работа остановлена до подтверждения следующей фазы.
