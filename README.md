# Margin — где что лежит

Ежедневник для Android (Kotlin + Compose), только русский интерфейс. Пишете или диктуете в одно поле, Margin сам раскладывает дела, обещания, темы и заметки, а время держит на полях.

**Сейчас:** версия **0.69** (тег `v0.69`), рабочая ветка **`main`**, рабочая папка — эта (`~/Desktop/Проекты/Margin`), GitHub — `anjeib/margin`. Других рабочих копий нет.

| Что | Где |
|---|---|
| Правила проекта, решения владельца, устройство кода | `CLAUDE.md` — начинать с раздела «Сейчас» |
| Код | `app/src/main/java/ru/anjeib/margin/` (`ui/` — экраны, `data/` — модель и хранилище, `ai/` — разбор, `voice/` — речь) |
| Текущий вид (дизайн «Полоса набора», с 0.65) | `design/current/` — снимки 0.69, светлая и тёмная тема |
| Текст «Помощи» и описание для RuStore | `docs/help.md`, `docs/rustore.md` |
| Посредник для облака (Cloud.ru) | `server/cloudru/` (ключ — `server/cloudru/env.local`, вне git) |
| Модель речи на эмуляторе | `server/emulator-speech-model.sh` |
| Все выпущенные APK | `releases/` (вне git); последний — `~/Desktop/Margin-<версия>.apk` |
| Движок и модель речи | `third_party/` (вне git) |
| Ключ подписи | `release.jks` + `keystore.properties` — не трогать, копия должна быть вне Mac |
| Устаревшее | `archive/` (см. `archive/README.md`), теги `archive/*`, `archive/local/` (вне git) |

Сборка: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleRelease` (подробно — `CLAUDE.md`, «Сборка» и «Версии и откат»). Выпуск — только по команде владельца.

Сайт приложения — отдельная папка `~/Desktop/Проекты/Margin-site`.
