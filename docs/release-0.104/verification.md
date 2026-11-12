# Margin 0.104 — выпуск 12 ноября 2026

Релиз создан из проверенной тестовой 0.104-test. Пакет `ru.margin.app`, versionCode 104, versionName 0.104, без тестового суффикса. Обновлены помощь, подсказки, задания курса и три иллюстрации помощи.

Сборка и тесты: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testReleaseUnitTest :app:assembleRelease --offline`. 209 тестов: 201 успешно, 8 пропущены, ошибок нет. Сборка и release lint прошли. Финальный APK проверен apksigner; сертификат совпадает с релизом 0.103. Пустые OPENROUTER_KEY, YC_KEY, YC_FOLDER, ALPHA_CODE; DEBUG, CLOUD_AI, FORWARD_CHANNELS выключены.

В эмуляторе выполнено обновление 0.103 → 0.104 без очистки данных: приложение запускается, существующие записи видны. В меню подтверждён номер 0.104 (104). Помощь открыта и визуально проверена. Снимки: `01-update.png`, `02-help.png`.

APK: `releases/Margin-0.104.apk`, копия на рабочем столе `~/Desktop/Margin-0.104.apk`.
SHA-256: `808b12e8e3266c696719f9c82041d3556fd402fbd708febe2e0a2faa8eaede59`.

RuStore: версия 2064950186 загружена и отправлена на модерацию, последний проверенный статус AUTO_CHECK. Настроена автоматическая публикация (INSTANTLY) после одобрения. Подробный ответ API: `store/rustore-0.104-result.json`. Публикация ещё не подтверждена.
