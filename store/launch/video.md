# Вертикальный ролик 15–20 с (Reels, Shorts, VK Клипы)

**Формат:** 1080×1920, 9:16, 30 к/с, MP4 (H.264), около 18 с. Без звука смысл должен читаться по подписям — большинство смотрит без звука. Музыка — спокойная, из библиотеки площадки (у каждой своя, лицензия у неё же).

**Идея:** одна фраза становится делом, голосовое становится текстом. Никаких рук и лиц — только экран Margin на бумажном фоне и подпись сверху.

**Кадр:** запись экрана эмулятора (1080×2400) уменьшена до высоты 1560 px и стоит по центру ниже подписи; фон — цвет листа Margin `#F6F5F1`; подпись — Commissioner, чернилами, по центру над телефоном.

## Раскадровка

| Время | На экране | Подпись сверху |
|---|---|---|
| 0,0–1,5 с | Лента «Дела», сегодня: несколько дел с часами на полях. Ничего не происходит. | Скажите, что нужно |
| 1,5–6,0 с | В поле появляется фраза «Маша пришлёт договор до пятницы». Над полем — как Margin понял: ждём · пятница · Маша. Держим 2 с, чтобы успели прочитать. | Margin понимает фразу ещё до отправки |
| 6,0–9,0 с | Касание «отправить». Строка встаёт в ленту на пятницу, под ней «ждём от Маши». | В пятницу утром напомнит |
| 9,0–14,5 с | «Входящие»: пришло голосовое, строка «Распознаю · 40%», затем вместо неё слова голосового. | Голосовое — в текст, прямо на телефоне |
| 14,5–18,0 с | Финальная карточка (без телефона): иконка «M.», под ней «Margin», ниже «Ежедневник и дневник», ещё ниже «Бесплатно в RuStore». | — |

**Обложка** (первый кадр в ленте площадки): кадр 1,5–6,0 с с фразой в поле и подписью «Margin понимает фразу ещё до отправки».

**Текст под роликом:**
> Margin — ежедневник и дневник для Android. Скажите «Маша пришлёт договор до пятницы» — и в пятницу утром Margin напомнит. Русская речь распознаётся прямо на телефоне. Бесплатно, без регистрации. Ссылка — в профиле.

## Как снять на эмуляторе margin35

Так же, как снят `Margin-site/img/say.webm`: запись средствами эмулятора (`adb emu screenrecord`), фраза подаётся share-intent'ом в главное поле, отправка — касанием.

### 1. Подготовка

```bash
~/Library/Android/sdk/emulator/emulator -avd margin35 -dns-server 8.8.8.8,1.1.1.1
```

Если эмулятор лежит в `/opt/homebrew/share/android-commandlinetools/emulator/`, запускать оттуда.

```bash
adb shell cmd uimode night no
```

```bash
adb shell pm disable-user com.google.android.tts
```

Последняя команда — на время записи, иначе голосовая клавиатура перекрывает экран (так было при съёмке say.webm). После съёмки вернуть: `adb shell pm enable com.google.android.tts`.

Чистая строка состояния (часы 9:30, полная батарея, без уведомлений):

```bash
adb shell settings put global sysui_demo_allowed 1
```

```bash
adb shell am broadcast -a com.android.systemui.demo -e command enter
```

```bash
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930
```

```bash
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
```

```bash
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
```

**Данные:** на ленте нужны правдоподобные дела на сегодня и пятницу — снимать на тех же выдуманных данных, что `store/raw` (Олег, Аня, Лена, «Северный ветер»). Перед съёмкой убедиться, что в пятницу нет дела с Машей, иначе новая строка потеряется.

**Модель речи** для сцены с голосовым должна быть на эмуляторе:

```bash
~/Desktop/Проекты/Margin/server/emulator-speech-model.sh
```

**Голосовое для сцены 4** — выдуманное, своим голосом (QuickTime → «Новая аудиозапись»), секунд 10–15, например: «Привет, это Маша. Договор пришлю в пятницу, а смету — на следующей неделе». Перевести в формат голосовых Telegram и положить на эмулятор:

```bash
ffmpeg -i masha.m4a -c:a libopus -b:a 24k masha.ogg
```

```bash
adb push masha.ogg /sdcard/Download/
```

### 2. Запуск выпуска, а не отладочной сборки

На эмуляторе может стоять несколько сборок. Запускать именно выпуск и проверить номер 0.93 в меню ≡:

```bash
adb shell am start -n ru.margin.app/ru.anjeib.margin.MainActivity
```

### 3. Сцены 1–3: фраза → дело

Координаты кнопки «отправить» снять со скриншота (кнопка — морской круг справа от поля):

```bash
adb exec-out screencap -p > ~/Desktop/Проекты/Margin/store/launch/video/shot.png
```

Начать запись:

```bash
adb emu screenrecord start --time-limit 30 ~/Desktop/Проекты/Margin/store/launch/video/raw-phrase.webm
```

Секунды 2 ничего не делать (это сцена 1), затем подать фразу в поле:

```bash
adb shell "am start -n ru.margin.app/ru.anjeib.margin.MainActivity -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT 'Маша пришлёт договор до пятницы'"
```

Подождать 3–4 секунды (превью над полем), затем нажать «отправить» (X Y — со скриншота):

```bash
adb shell input tap X Y
```

Подождать 3 секунды, пока строка встанет в ленту, и остановить запись:

```bash
adb emu screenrecord stop
```

Если строка встала на пятницу ниже края экрана — перед съёмкой нажать пятницу в неделе шапки, чтобы лента стояла на ней, и только потом подавать фразу. Проверить на пробном дубле.

### 4. Сцена 4: голосовое во «Входящие»

Голосовое отправляется в Margin через «Файлы» → `masha.ogg` → «Поделиться» → Margin (команда `am start` с `content://media` не даёт Margin права на чтение — проверено в разработке). Сам процесс выбора в «Файлах» снимать не нужно:

1. Открыть в Margin «Входящие» (меню ≡ → «Входящие»).
2. Запустить запись: `adb emu screenrecord start --time-limit 30 ~/Desktop/Проекты/Margin/store/launch/video/raw-voice.webm`.
3. Поделиться `masha.ogg` из «Файлов» в Margin, вернуться во «Входящие» и дать расшифроваться.
4. Остановить: `adb emu screenrecord stop`.

При монтаже из этого дубля берётся только кусок во «Входящих» от «Распознаю…» до готового текста. Если расшифровка 10-секундного файла мелькает слишком быстро, взять голосовое подлиннее (30–40 с) — тогда успеет показаться процент.

### 5. Монтаж

Вырезать нужные куски (время начала и длительность подобрать по дублю):

```bash
cd ~/Desktop/Проекты/Margin/store/launch/video
```

```bash
ffmpeg -ss 0 -t 9 -i raw-phrase.webm -r 30 -an a.mp4
```

```bash
ffmpeg -ss 2 -t 5.5 -i raw-voice.webm -r 30 -an b.mp4
```

```bash
ffmpeg -i a.mp4 -i b.mp4 -filter_complex "[0:v][1:v]concat=n=2:v=1[v]" -map "[v]" phone.mp4
```

Если на записи виден вырез камеры эмулятора, закрыть его фильтром `delogo` (координаты — по кадру), как на say.webm: добавить `delogo=x=…:y=…:w=…:h=…` перед `scale` в команде ниже.

Собрать кадр 1080×1920: фон, телефон по центру, подписи по времени:

```bash
F=~/Desktop/Проекты/Margin/app/src/main/res/font/commissioner.ttf; ffmpeg -f lavfi -i color=c=0xF6F5F1:s=1080x1920:r=30:d=14.5 -i phone.mp4 -filter_complex "[1:v]scale=-2:1560,setsar=1[ph];[0:v][ph]overlay=(W-w)/2:300:shortest=1,drawtext=fontfile=$F:text='Скажите, что нужно':fontsize=56:fontcolor=0x1C1E1F:x=(w-text_w)/2:y=170:enable='between(t,0,1.5)',drawtext=fontfile=$F:text='Margin понимает фразу ещё до отправки':fontsize=50:fontcolor=0x1C1E1F:x=(w-text_w)/2:y=170:enable='between(t,1.5,6)',drawtext=fontfile=$F:text='В пятницу утром напомнит':fontsize=56:fontcolor=0x1C1E1F:x=(w-text_w)/2:y=170:enable='between(t,6,9)',drawtext=fontfile=$F:text='Голосовое — в текст, прямо на телефоне':fontsize=50:fontcolor=0x1C1E1F:x=(w-text_w)/2:y=170:enable='between(t,9,14.5)'" -c:v libx264 -pix_fmt yuv420p -crf 18 body.mp4
```

Финальную карточку (3,5 с) проще собрать из иконки:

```bash
C=~/Desktop/Проекты/Margin/app/src/main/res/font/cormorant.ttf; F=~/Desktop/Проекты/Margin/app/src/main/res/font/commissioner.ttf; ffmpeg -f lavfi -i color=c=0xF6F5F1:s=1080x1920:r=30:d=3.5 -i ~/Desktop/Проекты/Margin/store/icon-512.png -filter_complex "[1:v]scale=360:360[ic];[0:v][ic]overlay=(W-w)/2:560,drawtext=fontfile=$C:text='Margin':fontsize=110:fontcolor=0x1C1E1F:x=(w-text_w)/2:y=990,drawtext=fontfile=$F:text='Ежедневник и дневник':fontsize=50:fontcolor=0x6E716F:x=(w-text_w)/2:y=1150,drawtext=fontfile=$F:text='Бесплатно в RuStore':fontsize=50:fontcolor=0x1C6480:x=(w-text_w)/2:y=1260" -c:v libx264 -pix_fmt yuv420p -crf 18 end.mp4
```

```bash
ffmpeg -i body.mp4 -i end.mp4 -filter_complex "[0:v][1:v]concat=n=2:v=1[v]" -map "[v]" -c:v libx264 -pix_fmt yuv420p -crf 18 margin-reel.mp4
```

Музыку лучше добавлять уже в приложении площадки.

## Заметки для владельца

- **Голос или текст.** Фраза в сценах 1–3 подаётся в поле командой, как в say.webm, а не диктуется. Поэтому подпись первой сцены — «Скажите, что нужно», а не «Смотрите, как я говорю»: ролик не показывает микрофон, который не нажимали. Если хочется настоящую диктовку в кадре, её честнее снять с экрана своего телефона (запись экрана Android) — на эмуляторе микрофон хоста не проверяли.
- **Подпись «В пятницу утром напомнит»** верна для 0.93: утром в день срока приходит «Сегодня ждём от Маши: договор».
- **Сцена с голосовым** — та же оговорка, что в постах: в 0.91 пересылку убирали из маркетинга, на сайте она снова есть. Без неё ролик укладывается в 15 с: сцены 1–3 и финал.
- **Цвета подписей:** чернила `#1C1E1F`, графит `#6E716F`, петроль `#1C6480` и фон `#F6F5F1` — из светлой палитры приложения (`ui/Theme.kt`).
- Папки `video/` в `store/launch/` пока нет — создать перед съёмкой (`mkdir -p`).
