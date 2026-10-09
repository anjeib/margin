# Как настроить yc (Яндекс Облако) — 5 минут

Всё в Терминале на Mac.

## 1. Установить

```
curl -sSL https://storage.yandexcloud.net/yandexcloud-yc/install.sh | bash
```

На вопрос «Modify profile to update your $PATH…?» ответить **Y**.
Затем **закрыть Терминал и открыть заново**.

## 2. Войти

```
yc init
```

1. Появится ссылка на `oauth.yandex.ru` — открыть её в браузере, войти в тот аккаунт Яндекса, где облако с каталогом **margin**.
2. Браузер покажет токен — скопировать и вставить в Терминал. **В чат токен не присылать** — это ключ ко всему облаку.
3. «Please choose cloud» — номер вашего облака.
4. «Please choose folder» — номер каталога **margin**.
5. «Do you want to configure a default Compute zone?» — **n**.

## 3. Проверить

```
yc config list
```

Должны быть строки `cloud-id` и `folder-id`.

Готово — написать в чат «yc готов». Дальше всё сделает Claude: создаст функцию-посредника, коды приглашения и проверит.
