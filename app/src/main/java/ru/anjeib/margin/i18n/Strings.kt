package ru.anjeib.margin.i18n

import ru.anjeib.margin.data.pluralRu
import ru.anjeib.margin.data.shortLabel

enum class Lang { Ru, En }

/** All user-facing text. One instance per language; the UI never mixes them. */
interface Strings {
    // tabs & screens
    val today: String
    val thisWeek: String
    val all: String
    val tomorrow: String
    val yesterday: String
    val tasks: String
    val notes: String
    val settings: String
    fun fromDate(d: String): String
    val thisWeekSoft: String
    val nextWeekSoft: String
    val later: String
    val waiting: String
    fun waitingFor(who: String): String
    val create: String
    fun stepsCount(n: Int): String
    val chipEvening: String
    val promptEvening: String
    val notesCreated: String
    fun tasksCount(n: Int): String

    // drawer
    val smart: String
    val inbox: String
    val overdue: String
    val completed: String
    val projects: String
    val people: String
    val allTasks: String

    // composer
    val composerHint: String
    val noteHint: String
    val chipNext: String
    val chipPlanDay: String
    val thinking: String
    val voiceUnavailable: String

    // assistant feed
    val taskCreated: String
    fun tasksCreated(n: Int): String
    val taskUpdated: String
    fun subtasksAdded(n: Int): String
    val noteCreated: String
    val edit: String
    val undo: String
    val undone: String
    val remindFriday: String
    val dismiss: String
    val noKey: String
    val openSettings: String
    fun movedOverdue(n: Int): String
    val nothingOverdue: String

    // lists
    val emptyToday: String
    val emptyWeek: String
    val emptyAll: String
    val emptyHint: String
    val noTime: String
    val done: String
    val doneLower: String
    val swipeTomorrow: String
    val swipeNextWeek: String
    val markDone: String

    // brief
    val morningBrief: String
    val eveningBrief: String
    val nothingToday: String
    fun doneNext(date: String): String
    fun undatedCount(n: Int): String
    val subtaskOne: String
    val freeDay: String
    val insertBelow: String
    val captionWord: String
    val moveUp: String
    val moveDown: String
    val readingPhoto: String
    val textOnPhoto: String
    val textWord: String
    val photoWord: String
    val textAfter: String
    val moveToToday: String
    val skipOnce: String
    val droppedDone: String
    val madeNote: String
    val toNote: String
    val wontDo: String
    val droppedMark: String
    val historyWord: String
    fun change(kind: String, value: String?, by: String): String
    val listBlock: String
    val listItem: String
    val remindWord: String
    val removeWord: String
    fun fillHint(what: String): String
    val freeDayHint: String
    val askTime: String
    val askWhen: String
    fun ideaBecameTask(title: String): String
    val granted: String
    val youWrote: String
    fun nextOn(date: String): String
    fun was(what: String): String
    val makeBrief: String
    val refresh: String
    val startFocus: String
    val focusWindow: String

    // task sheet
    val subtasks: String
    val comment: String
    val commentHint: String
    val addSubtaskHint: String
    val dates: String
    val noDates: String
    val start: String
    val due: String
    val time: String
    val reminder: String
    val noReminder: String
    val project: String
    val noProject: String
    val responsible: String
    val nobody: String
    val delete: String
    val deleteTaskQ: String
    val cancel: String
    val save: String
    val clear: String
    val breakDown: String
    fun breakDownQ(title: String): String
    val breakDownYes: String
    val notNeeded: String
    val plusWeek: String
    val me: String
    val title: String
    val peopleHint: String

    // notes
    val searchNotes: String
    val newNote: String
    val noteTitleHint: String
    val noteBodyHint: String
    val emptyNotes: String
    val untitled: String

    // settings
    val aiSection: String
    val apiKey: String
    val apiKeyHint: String
    val connected: String
    val notConnected: String
    val check: String
    val mainModel: String
    val mainModelHint: String
    val fastModel: String
    val fastModelHint: String
    val spentMonth: String
    val appearance: String
    val themeLight: String
    val themeDark: String
    val themeAuto: String
    val language: String
    val langAuto: String
    val data: String
    val searchModels: String
    val loading: String
    val perMillion: String


    // v2
    fun until(day: String): String
    val inWorkThisWeek: String
    val thisMonth: String
    val waitingOthers: String
    val byDates: String
    val byProjects: String
    val showAll: String
    val mainThing: String
    val collapse: String
    val saved: String
    val savedToInbox: String
    val inboxNote: String
    val inboxHint: String
    val setupBriefQ: String
    val setupBriefOptions: List<String>
    val setupNameQ: String
    fun setupDone(name: String): String
    val settingsChanged: String
    val inboxEmpty: String
    val inboxEmptyHint: String
    val savedUnsorted: String
    fun queuedLine(n: Int): String
    val nothingFound: String
    val entryAdded: String
    val addOrChange: String
    fun more(n: Int): String
    fun doneCount(n: Int): String
    val records: String
    fun allRecords(n: Int): String
    val attachPhoto: String
    val attachCamera: String
    val attachFile: String
    val attachContact: String
    val transcribing: String
    val micPermission: String
    val voiceNote: String
    val video: String
    fun nPhotos(n: Int): String
    fun nFiles(n: Int): String
    fun nVoice(n: Int): String
    fun nContacts(n: Int): String
    val notesHint: String
    val appendHint: String
    val allFilter: String
    fun entriesCount(n: Int): String
    fun updated(d: String): String
    val peopleTitle: String
    val emptyPeople: String
    val rename: String
    val mergeWith: String
    val aboutHint: String
    val waitingFromThem: String
    val voiceSection: String
    val voiceAi: String
    val voiceAiHint: String
    val voiceModel: String
    val voiceSendNow: String

    // v7
    val freshSummary: String
    val weekSummary: String
    val toTaskPill: String
    val ideasOpen: String
    fun waitLine(what: String): String
    fun promiseLine(what: String): String
    val ideaMark: String
    val linksTitle: String
    val linkIdea: String
    val inTasks: String
    val fieldDue: String
    val fieldTime: String
    val fieldRepeat: String
    val fieldPeople: String
    val fieldPlace: String
    val fieldRole: String
    val fieldCompany: String
    val fieldPhone: String
    val fieldEmail: String
    val fieldAddress: String
    val personalTitle: String
    val personalHint: String
    val copied: String
    val placeHint: String
    val historyTitle: String
    val srcTaskEntry: String
    val srcChat: String
    val srcNote: String
    val noDue: String
    val tasksCaps: String
    val makeSummary: String
    fun fromNote(title: String, date: String): String
    fun fromChat(date: String): String
    fun promised(who: String): String
    fun summaryAt(t: String): String

    // v6
    val reopened: String
    val markedDone: String
    val allDoneToday: String
    val quickToday: String
    val quickTomorrow: String
    val quickNextWeek: String
    val quickNone: String
    val rangeLink: String
    val singleLink: String
    val savedTick: String
    val toSeparateNote: String
    val devSection: String
    val devUnlocked: String
    val onDevice: String
    val onDeviceHint: String
    val onDeviceModel: String
    fun movedTo(d: String): String
    fun briefAt(t: String): String
    fun version(v: String): String

    // v5
    val parsingMode: String
    val modeHybrid: String
    val modeEconomy: String
    val modePrecise: String
    val modeHybridHint: String
    val modeEconomyHint: String
    val modePreciseHint: String

    // v4
    val kindTask: String
    fun kindTasks(n: Int): String
    val kindChanged: String
    val kindNote: String
    val kindContact: String
    val readMore: String
    fun notesToday(titles: String): String

    // v3
    val a11yMenu: String
    val a11ySend: String
    val a11yMic: String
    val a11yAttach: String
    val begin: String
    val offline: String
    val offlineSaved: String
    val queueDoneTitle: String
    val waitingNet: String
    val whisperOfferOffline: String
    val snoozeHour: String
    val repeat: String
    val noRepeat: String
    val everyDay: String
    val weekdays: String
    val everyWeek: String
    val everyMonth: String
    val everyYear: String
    val overdueWaiting: String
    val search: String
    val searchHint: String
    val nothing: String
    val calendarSection: String
    val calendarRead: String
    val calendarHint: String
    val offlineSection: String
    val whisperState: String
    val whisperMissing: String
    val whisperBase: String
    val whisperSmall: String
    val whisperAlways: String
    val whisperAlwaysHint: String
    val deleteModel: String
    val cancelDownload: String
    val backupSection: String
    val googleBackupHint: String
    val googleBackup: String
    val googleBackupOff: String
    val skip: String
    val next: String
    val allow: String
    val ob1Title: String
    val ob1Text: String
    val ob2Title: String
    val ob2Text: String
    val ob3Title: String
    val ob3Text: String
    val ob4Title: String
    val ob4Text: String
    val notifications: String
    val calendar: String
    fun queueDoneText(n: Int): String
    fun remindPerson(name: String): String
    fun nudgeText(what: String): String
    fun whisperDownloading(p: Int): String
    fun whisperReady(name: String): String

    // errors
    val errNoKey: String
    val errWeird: String
    val errNet: String
    val errService: String
    val errEmpty: String
    val errBadKey: String
    val errNoMoney: String
    val errRate: String
    fun errGeneric(code: Int): String
    fun keyStatus(usage: Double?, limit: Double?): String

    // notifications
    val notifChannel: String
    val notifMorningTitle: String
    fun notifMorningText(n: Int, overdue: Int): String
    val notifEveningTitle: String
    fun notifEveningText(done: Int, left: Int): String
    val notifReminder: String

    // ai prompts
    val aiLanguage: String
    val promptNext: String
    val promptPlanDay: String
    fun promptBreakDown(what: String): String
}

object Ru : Strings {
    override val today = "Сегодня"
    override val thisWeek = "Неделя"
    override val all = "Все"
    override val tomorrow = "Завтра"
    override val yesterday = "Вчера"
    override val tasks = "Задачи"
    override val notes = "Заметки"
    override val settings = "Настройки"
    override fun fromDate(d: String) = "с ${d.lowercase()}"
    override val thisWeekSoft = "Эта неделя"
    override val nextWeekSoft = "След. неделя"
    override val later = "Позже"
    override val waiting = "Ожидание"
    override fun waitingFor(who: String) = "Ждём: $who"
    override val create = "Создать"
    override fun stepsCount(n: Int) = "$n " + pluralRu(n, "шаг", "шага", "шагов")
    override val chipEvening = "Итоги дня"
    override val promptEvening = "Сделай короткий вечерний обзор: что сделано сегодня, что не успел. Предложи, что перенести на завтра, и спроси про задачи, которые переносились 3 раза и больше, — оставить или удалить. Если на завтра задач больше, чем реально успеть, скажи об этом."
    override val notesCreated = "Сохранено в заметки"
    override fun tasksCount(n: Int) = "$n " + pluralRu(n, "задача", "задачи", "задач")

    override val smart = "СПИСКИ"
    override val inbox = "Без даты"
    override val overdue = "Просрочено"
    override val completed = "Выполнено"
    override val projects = "ПРОЕКТЫ"
    override val people = "ЛЮДИ"
    override val allTasks = "Все задачи"

    override val composerHint = "Напишите задачу или мысль…"
    override val noteHint = "Новая заметка…"
    override val chipNext = "Что дальше?"
    override val chipPlanDay = "План на день"
    override val thinking = "Думаю…"
    override val voiceUnavailable = "Голосовой ввод недоступен на этом телефоне"

    override val taskCreated = "Создана задача"
    override fun tasksCreated(n: Int) = "Создано " + n + " " + pluralRu(n, "задача", "задачи", "задач")
    override val taskUpdated = "Задача обновлена"
    override fun subtasksAdded(n: Int) = "Добавлено $n " + pluralRu(n, "подзадача", "подзадачи", "подзадач")
    override val noteCreated = "Создана заметка"
    override val edit = "Изменить"
    override val undo = "Отменить"
    override val undone = "Отменено"
    override val remindFriday = "Напомнить в пятницу"
    override val dismiss = "Скрыть"
    override val noKey = "Чтобы ИИ разбирал задачи, нужен ключ — добавьте его в настройках."
    override val openSettings = "Открыть настройки"
    override fun movedOverdue(n: Int) = "Перенесено на сегодня: $n"
    override val nothingOverdue = "Просроченных задач нет"

    override val emptyToday = "На сегодня ничего"
    override val emptyWeek = "Неделя свободна"
    override val emptyAll = "Задач пока нет"
    override val emptyHint = "Просто скажите или напишите, что нужно: создать задачу, заметку или что-то изменить. Например: «Завтра встреча с Мариной в 15» или «Посмотреть „Одиссею“ Нолана»."
    override val noTime = "Без времени"
    override val done = "Выполнено"
    override val doneLower = "выполнено"
    override val swipeTomorrow = "Завтра"
    override val swipeNextWeek = "+1 неделя"
    override val markDone = "Готово"

    override val morningBrief = "Главное сегодня"
    override val eveningBrief = "На завтра"
    override val nothingToday = "На сегодня дел нет"
    override fun doneNext(date: String) = "Готово · следующий раз $date"
    override fun undatedCount(n: Int) = "Ещё без срока: " + tasksCount(n)
    override val subtaskOne = "Подзадача"
    override val freeDay = "Свободный день"
    override val insertBelow = "Вставить ниже"
    override val captionWord = "Подпись"
    override val moveUp = "Выше"
    override val moveDown = "Ниже"
    override val readingPhoto = "Читаю текст на фото…"
    override val textOnPhoto = "Текст на фото"
    override val textWord = "Текст"
    override val photoWord = "Фото"
    override val textAfter = "Текст после блока"
    override val moveToToday = "Перенести на сегодня"
    override val skipOnce = "Пропустить один раз"
    override val droppedDone = "Не будете делать"
    override val madeNote = "Теперь это заметка"
    override val toNote = "Сделать заметкой"
    override val wontDo = "Не буду делать"
    override val droppedMark = "не делали"
    override val historyWord = "История"
    override fun change(kind: String, value: String?, by: String): String {
        val what = when (kind) {
            "created" -> when (value) { "chat" -> "создано из разговора"; "idea" -> "создано из идеи"; "repeat" -> "повтор"; else -> "создано" }
            "date" -> if (value == null) "срок убран" else "срок: " + (runCatching { java.time.LocalDate.parse(value).shortLabel().lowercase() }.getOrNull() ?: value)
            "time" -> if (value == null) "время убрано" else "время: $value"
            "repeat" -> if (value == null) "повтор убран" else "повтор изменён"
            "waiting" -> if (value == null) "больше не ждём" else "ждём: $value"
            "promised" -> if (value == null) "обещание снято" else "вы обещали: $value"
            "place" -> if (value == null) "место убрано" else "место: $value"
            "done" -> "готово"
            "dropped" -> "не будете делать"
            "reopened" -> "снова открыто"
            else -> kind
        }
        return what + when (by) { "ai" -> " · ИИ"; "swipe" -> " · свайп"; else -> "" }
    }
    override val listBlock = "Список"
    override val listItem = "Пункт списка"
    override val remindWord = "Напомнить"
    override val removeWord = "Убрать"
    override fun fillHint(what: String) = "$what — напишите или скажите"
    override val freeDayHint = "На сегодня ничего не запланировано. Можно отдохнуть — или взять что-то из «Недели»."
    override val askTime = "Уточнить время?"
    override val askWhen = "Уточнить срок?"
    override fun ideaBecameTask(title: String) = "Теперь это задача: $title"
    override val granted = "разрешено"
    override val youWrote = "Вы написали"
    override fun nextOn(date: String) = "следующий раз $date"
    override fun was(what: String) = "было $what"
    override val makeBrief = "Собрать бриф"
    override val refresh = "Обновить"
    override val startFocus = "Начать с главного"
    override val focusWindow = "Время для главного"

    override val subtasks = "Подзадачи"
    override val comment = "Комментарий"
    override val commentHint = "Добавить комментарий"
    override val addSubtaskHint = "Добавить подзадачу…"
    override val dates = "Сроки"
    override val noDates = "Без даты"
    override val start = "Начало"
    override val due = "Срок"
    override val time = "Время"
    override val reminder = "Напоминание"
    override val noReminder = "Нет"
    override val project = "Проект"
    override val noProject = "Без проекта"
    override val responsible = "Ответственные"
    override val nobody = "Не указаны"
    override val delete = "Удалить"
    override val deleteTaskQ = "Удалить задачу?"
    override val cancel = "Отмена"
    override val save = "Сохранить"
    override val clear = "Очистить"
    override val breakDown = "Разбить на шаги"
    override fun breakDownQ(title: String) = "Разбить «$title» на шаги?"
    override val breakDownYes = "Разбить"
    override val notNeeded = "Не нужно"
    override val plusWeek = "+1 неделя"
    override val me = "Я"
    override val title = "Название"
    override val peopleHint = "Имена через запятую"

    override val searchNotes = "Поиск по заметкам…"
    override val newNote = "Новая заметка"
    override val noteTitleHint = "Заголовок"
    override val noteBodyHint = "Пишите свободно…"
    override val emptyNotes = "Заметок пока нет"
    override val untitled = "Без названия"

    override val aiSection = "ИИ"
    override val apiKey = "Ключ API"
    override val apiKeyHint = "sk-or-…"
    override val connected = "Подключено"
    override val notConnected = "Не подключено"
    override val check = "Проверить"
    override val mainModel = "Основная модель"
    override val mainModelHint = "брифы, планы, разбиение на шаги"
    override val fastModel = "Быстрый разбор"
    override val fastModelHint = "превращает ваш текст в задачи"
    override val spentMonth = "Расход за месяц"
    override val appearance = "ОФОРМЛЕНИЕ"
    override val themeLight = "Светлая"
    override val themeDark = "Тёмная"
    override val themeAuto = "Авто"
    override val language = "Язык"
    override val langAuto = "Как в системе"
    override val data = "ДАННЫЕ"
    override val searchModels = "Поиск модели…"
    override val loading = "Загрузка…"
    override val perMillion = "за 1M токенов"


    override fun until(day: String) = "до $day"
    override val inWorkThisWeek = "В работе на неделе"
    override val thisMonth = "Этот месяц"
    override val waitingOthers = "Ждём от других"
    override val byDates = "по срокам"
    override val byProjects = "по проектам"
    override val showAll = "Показать все"
    override val mainThing = "Главное"
    override val collapse = "Свернуть"
    override val saved = "Сохранено"
    override val savedToInbox = "Сохранено во «Входящие»"
    override val inboxNote = "Входящие"
    override val inboxHint = "Что ещё не разложено по задачам и заметкам"
    override val setupBriefQ = "Присылать утром короткую сводку дня? Выберите время — или «Не нужно»."
    override val setupBriefOptions = listOf("8:00", "9:00", "10:00", "Не нужно")
    override val setupNameQ = "Как к вам обращаться? Напишите имя — или пропустите."
    override fun setupDone(name: String) = (if (name.isNotBlank()) "Приятно познакомиться, $name. " else "Готово. ") +
        "Пишите внизу, что нужно сделать, — обычными словами. Поменять это можно так же: «сводку в 9», «называй меня Аня»."
    override val settingsChanged = "Настройки изменены"
    override val inboxEmpty = "Здесь пусто"
    override val inboxEmptyHint = "Сюда попадает то, что записано без интернета, — оно разберётся само, когда связь вернётся."
    override val savedUnsorted = "СОХРАНЕНО БЕЗ РАЗБОРА"
    override fun queuedLine(n: Int) = "Ждут интернета: " + n + " " + pluralRu(n, "запись", "записи", "записей")
    override val nothingFound = "Задач не найдено"
    override val entryAdded = "Добавлена запись"
    override val addOrChange = "Добавить или изменить…"
    override fun more(n: Int) = "ещё $n"
    override fun doneCount(n: Int) = "Выполнено · $n"
    override val records = "Записи"
    override fun allRecords(n: Int) = "Все записи ($n)"
    override val attachPhoto = "Фото и видео"
    override val attachCamera = "Камера"
    override val attachFile = "Файл"
    override val attachContact = "Контакт"
    override val transcribing = "Распознаю…"
    override val micPermission = "Нужен доступ к микрофону"
    override val voiceNote = "Голосовая"
    override val video = "Видео"
    override fun nPhotos(n: Int) = "$n фото"
    override fun nFiles(n: Int) = "$n " + pluralRu(n, "файл", "файла", "файлов")
    override fun nVoice(n: Int) = "$n " + pluralRu(n, "голосовая", "голосовые", "голосовых")
    override fun nContacts(n: Int) = "$n " + pluralRu(n, "контакт", "контакта", "контактов")
    override val notesHint = "Мысль, идея, итоги встречи…"
    override val appendHint = "Дописать мысль…"
    override val allFilter = "Все"
    override fun entriesCount(n: Int) = "$n " + pluralRu(n, "запись", "записи", "записей")
    override fun updated(d: String) = "обновлено $d"
    override val peopleTitle = "Люди"
    override val emptyPeople = "Люди появятся сами, когда вы упомянете их в задачах или заметках."
    override val rename = "Переименовать"
    override val mergeWith = "Объединить с…"
    override val aboutHint = "ИИ опишет, кто это и чем вы заняты вместе"
    override val waitingFromThem = "Ждём"
    override val voiceSection = "ГОЛОС"
    override val voiceAi = "ИИ-распознавание речи"
    override val voiceAiHint = "Точнее имена, названия и знаки препинания"
    override val voiceModel = "Модель распознавания"
    override val voiceSendNow = "Отправлять сразу после диктовки"

    override val offline = "Нет интернета"
    override val offlineSaved = "Нет интернета. Всё сохранено во «Входящие» и будет разобрано, как только появится сеть."
    override val queueDoneTitle = "Входящие разобраны"
    override val waitingNet = "ЖДУТ ИНТЕРНЕТА"
    override val whisperOfferOffline = "Нет интернета. Скачайте офлайн-распознавание в настройках, чтобы диктовать без сети."
    override val snoozeHour = "Через час"
    override val repeat = "Повтор"
    override val noRepeat = "Не повторять"
    override val everyDay = "Каждый день"
    override val weekdays = "По будням"
    override val everyWeek = "Каждую неделю"
    override val everyMonth = "Каждый месяц"
    override val everyYear = "Каждый год"
    override val overdueWaiting = "Срок прошёл"
    override val search = "Поиск"
    override val searchHint = "Задачи, заметки, люди…"
    override val nothing = "Ничего не найдено"
    override val calendarSection = "КАЛЕНДАРЬ"
    override val calendarRead = "Учитывать календарь телефона"
    override val calendarHint = "ИИ видит встречи и не ставит задачи поверх них"
    override val offlineSection = "ОФЛАЙН-РАСПОЗНАВАНИЕ"
    override val whisperState = "Whisper на телефоне"
    override val whisperMissing = "Не скачан"
    override val whisperBase = "Базовая · 60 МБ · быстрее"
    override val whisperSmall = "Точная · 190 МБ · лучше русский"
    override val whisperAlways = "Всегда распознавать на телефоне"
    override val whisperAlwaysHint = "Без отправки голоса в интернет"
    override val deleteModel = "Удалить модель"
    override val cancelDownload = "Остановить загрузку"
    override val backupSection = "РЕЗЕРВНАЯ КОПИЯ"
    override val googleBackupHint = "Раз в день задачи, заметки и настройки сохраняются в ваш аккаунт Google (Google Диск) и вернутся на новом телефоне."
    override val googleBackup = "Копия в аккаунт Google"
    override val googleBackupOff = "Выключена: если телефон потеряется, задачи и заметки не вернутся."
    override val skip = "Пропустить"
    override val next = "Дальше"
    override val begin = "Начать"
    override val kindTask = "задача"
    override val freshSummary = "СВЕЖАЯ СВОДКА"
    override val weekSummary = "СВОДКА ПРОЕКТА"
    override val toTaskPill = "В задачу"
    override val ideasOpen = "ИДЕИ БЕЗ ЗАДАЧ"
    override fun waitLine(what: String) = "ждём: $what"
    override fun promiseLine(what: String) = "вы обещали: $what"
    override val ideaMark = "из идеи"
    override val linksTitle = "СВЯЗИ"
    override val linkIdea = "Идея"
    override val inTasks = "Уже в задачах"
    override val fieldDue = "Срок"
    override val fieldTime = "Время"
    override val fieldRepeat = "Повтор"
    override val fieldPeople = "Люди"
    override val fieldPlace = "Место"
    override val fieldRole = "Должность"
    override val fieldCompany = "Компания"
    override val fieldPhone = "Телефон"
    override val fieldEmail = "Почта"
    override val fieldAddress = "Адрес"
    override val personalTitle = "ЛИЧНАЯ ИНФОРМАЦИЯ"
    override val personalHint = "день рождения, семья, что любит"
    override val copied = "Скопировано"
    override val placeHint = "Адрес или название, например «Ленина 5»"
    override val historyTitle = "ИСТОРИЯ"
    override val srcTaskEntry = "из записи задачи"
    override val srcChat = "из разговора"
    override val srcNote = "заметка"
    override val noDue = "без срока"
    override val tasksCaps = "ЗАДАЧИ"
    override val makeSummary = "Собрать сводку"
    override fun fromNote(title: String, date: String) = "из заметки «$title» · $date"
    override fun fromChat(date: String) = "из разговора · $date"
    override fun promised(who: String) = "Вы обещали: $who"
    override fun summaryAt(t: String) = "обновлено $t"
    override val reopened = "Задача снова открыта"
    override val markedDone = "Готово"
    override val allDoneToday = "Главное на сегодня сделано"
    override val quickToday = "Сегодня"
    override val quickTomorrow = "Завтра"
    override val quickNextWeek = "След. неделя"
    override val quickNone = "Без даты"
    override val rangeLink = "Период…"
    override val singleLink = "Одна дата"
    override val savedTick = "Сохранено"
    override val toSeparateNote = "в отдельную заметку"
    override val devSection = "ДЛЯ РАЗРАБОТЧИКА"
    override val devUnlocked = "Раздел для разработчика открыт"
    override val onDevice = "Распознавать речь на телефоне"
    override val onDeviceHint = "Голос не уходит в интернет. Разбор текста пока идёт в облаке."
    override val onDeviceModel = "Модель на телефоне"
    override fun movedTo(d: String) = "Перенесено: $d"
    override fun briefAt(t: String) = "обновлено $t"
    override fun version(v: String) = "Margin $v"
    override val parsingMode = "Разбор сообщений"
    override val modeHybrid = "Гибрид"
    override val modeEconomy = "Экономно"
    override val modePrecise = "Точно"
    override val modeHybridHint = "Простые фразы — Alice Flash, сложные — DeepSeek"
    override val modeEconomyHint = "Всё через Alice Flash: дешевле, но чаще ошибается"
    override val modePreciseHint = "Всё через DeepSeek: точнее, дороже примерно вдвое"
    override fun kindTasks(n: Int) = "$n " + pluralRu(n, "задача", "задачи", "задач")
    override val kindChanged = "изменено"
    override val kindNote = "заметка"
    override val kindContact = "контакт"
    override val readMore = "читать дальше"
    override fun notesToday(titles: String) = "Сегодня в заметках: $titles"
    override val a11yMenu = "Меню"
    override val a11ySend = "Отправить"
    override val a11yMic = "Диктовка"
    override val a11yAttach = "Прикрепить"
    override val allow = "Разрешить"
    override val ob1Title = "Пишите как думаете"
    override val ob1Text = "Одной фразой: «на этой неделе запустить сайт, Дима — дизайн». Margin сам сделает задачу, сроки, подзадачи и ответственных."
    override val ob2Title = "Жесты"
    override val ob2Text = "Свайп вправо — готово. Свайп влево — перенести на день. Меню — кнопка ☰ слева вверху. Нажатие на задачу открывает её карточку."
    override val ob3Title = "Голос, фото, файлы"
    override val ob3Text = "Микрофон распознаёт речь с помощью ИИ, «+» прикрепляет фото, файлы и контакты. Мысли и идеи попадают в заметки, люди — в раздел «Люди»."
    override val ob4Title = "Пара разрешений"
    override val ob4Text = "Уведомления — для напоминаний. Календарь — чтобы ИИ планировал вокруг ваших встреч. Всё можно поменять в настройках."
    override val notifications = "Уведомления"
    override val calendar = "Календарь"
    override fun queueDoneText(n: Int) = "Разобрано: $n"
    override fun remindPerson(name: String) = "Напомнить: $name"
    override fun nudgeText(what: String) = "Привет! Как там «$what»?"
    override fun whisperDownloading(p: Int) = "Загрузка… $p%"
    override fun whisperReady(name: String) = "Готово · $name"
    override val errNoKey = "Добавьте ключ OpenRouter в настройках"
    override val errWeird = "Непонятный ответ OpenRouter"
    override val errNet = "Нет связи с интернетом"
    override val errService = "ИИ сейчас недоступен. Попробуйте чуть позже"
    override val errEmpty = "Не получилось разобрать. Попробуйте написать ещё раз"
    override val errBadKey = "Ключ OpenRouter не подходит — проверьте его в настройках"
    override val errNoMoney = "На счёте OpenRouter закончились деньги"
    override val errRate = "Слишком много запросов, попробуйте через минуту"
    override fun errGeneric(code: Int) = "Ошибка OpenRouter ($code)"
    override fun keyStatus(usage: Double?, limit: Double?) = buildString {
        append("Подключено")
        if (usage != null) append(" · потрачено $" + "%.2f".format(usage))
        if (limit != null) append(" из $" + "%.0f".format(limit))
    }

    override val notifChannel = "Напоминания"
    override val notifMorningTitle = "Доброе утро"
    override fun notifMorningText(n: Int, overdue: Int) =
        "Сегодня " + tasksCount(n) + if (overdue > 0) ", просрочено: $overdue" else ""
    override val notifEveningTitle = "Итоги дня"
    override fun notifEveningText(done: Int, left: Int) = "Сделано: $done. Осталось на сегодня: $left"
    override val notifReminder = "Напоминание"

    override val aiLanguage = "русском"
    override val promptNext = "Что мне сделать дальше? Посоветуй одну-две задачи с учётом сроков и времени."
    override val promptPlanDay = "Составь реалистичный план на сегодня: расставь время задачам на сегодня через update_task (time), начиная с ближайшего свободного часа, не трогая задачи с уже заданным временем. В reply — 1–2 предложения, что главное."
    override fun promptBreakDown(what: String) = "Разбей $what на конкретные шаги."
}

object En : Strings {
    override val today = "Today"
    override val thisWeek = "This week"
    override val all = "All"
    override val tomorrow = "Tomorrow"
    override val yesterday = "Yesterday"
    override val tasks = "Tasks"
    override val notes = "Notes"
    override val settings = "Settings"
    override fun fromDate(d: String) = "from $d"
    override val thisWeekSoft = "This week"
    override val nextWeekSoft = "Next week"
    override val later = "Later"
    override val waiting = "Waiting"
    override fun waitingFor(who: String) = "Waiting: $who"
    override val create = "Create"
    override fun stepsCount(n: Int) = "$n " + if (n == 1) "step" else "steps"
    override val chipEvening = "Wrap up the day"
    override val promptEvening = "Do a short evening review: what got done today and what didn't. Suggest what to move to tomorrow, and ask about tasks postponed 3+ times — keep or delete. If tomorrow holds more than is realistic, say so."
    override val notesCreated = "Saved to notes"
    override fun tasksCount(n: Int) = "$n " + if (n == 1) "task" else "tasks"

    override val smart = "LISTS"
    override val inbox = "No date"
    override val overdue = "Overdue"
    override val completed = "Completed"
    override val projects = "PROJECTS"
    override val people = "PEOPLE"
    override val allTasks = "All tasks"

    override val composerHint = "Write a task or a thought…"
    override val noteHint = "New note…"
    override val chipNext = "What's next?"
    override val chipPlanDay = "Plan my day"
    override val thinking = "Thinking…"
    override val voiceUnavailable = "Voice input isn't available on this phone"

    override val taskCreated = "Task created"
    override fun tasksCreated(n: Int) = "$n tasks created"
    override val taskUpdated = "Task updated"
    override fun subtasksAdded(n: Int) = "$n " + (if (n == 1) "subtask" else "subtasks") + " added"
    override val noteCreated = "Note created"
    override val edit = "Edit"
    override val undo = "Undo"
    override val undone = "Undone"
    override val remindFriday = "Remind on Friday"
    override val dismiss = "Dismiss"
    override val noKey = "The AI needs a key to sort your tasks — add it in settings."
    override val openSettings = "Open settings"
    override fun movedOverdue(n: Int) = "Moved to today: $n"
    override val nothingOverdue = "Nothing overdue"

    override val emptyToday = "Nothing for today"
    override val emptyWeek = "Your week is clear"
    override val emptyAll = "No tasks yet"
    override val emptyHint = "Just say or type what you need: a task, a note or a change. For example: “Meeting with Marina tomorrow at 3” or “Watch Nolan’s Odyssey”."
    override val noTime = "Anytime"
    override val done = "Done"
    override val doneLower = "done"
    override val swipeTomorrow = "Tomorrow"
    override val swipeNextWeek = "+1 week"
    override val markDone = "Done"

    override val morningBrief = "Today’s focus"
    override val eveningBrief = "For tomorrow"
    override val nothingToday = "Nothing left for today"
    override fun doneNext(date: String) = "Done · next on $date"
    override fun undatedCount(n: Int) = "Also without a date: " + tasksCount(n)
    override val subtaskOne = "Subtask"
    override val freeDay = "A free day"
    override val insertBelow = "Insert below"
    override val captionWord = "Caption"
    override val moveUp = "Move up"
    override val moveDown = "Move down"
    override val readingPhoto = "Reading the text on the photo…"
    override val textOnPhoto = "Text on the photo"
    override val textWord = "Text"
    override val photoWord = "Photo"
    override val textAfter = "Text after the block"
    override val moveToToday = "Move to today"
    override val skipOnce = "Skip once"
    override val droppedDone = "Won’t do"
    override val madeNote = "Now it’s a note"
    override val toNote = "Make it a note"
    override val wontDo = "Won’t do"
    override val droppedMark = "not done"
    override val historyWord = "History"
    override fun change(kind: String, value: String?, by: String): String {
        val what = when (kind) {
            "created" -> when (value) { "chat" -> "created from a conversation"; "idea" -> "created from an idea"; "repeat" -> "repeats"; else -> "created" }
            "date" -> if (value == null) "date removed" else "date: " + (runCatching { java.time.LocalDate.parse(value).shortLabel() }.getOrNull() ?: value)
            "time" -> if (value == null) "time removed" else "time: $value"
            "repeat" -> if (value == null) "repeat removed" else "repeat changed"
            "waiting" -> if (value == null) "no longer waiting" else "waiting: $value"
            "promised" -> if (value == null) "promise removed" else "you promised: $value"
            "place" -> if (value == null) "place removed" else "place: $value"
            "done" -> "done"
            "dropped" -> "won’t do"
            "reopened" -> "reopened"
            else -> kind
        }
        return what + when (by) { "ai" -> " · AI"; "swipe" -> " · swipe"; else -> "" }
    }
    override val listBlock = "List"
    override val listItem = "List item"
    override val remindWord = "Remind"
    override val removeWord = "Remove"
    override fun fillHint(what: String) = "$what — type or say it"
    override val freeDayHint = "Nothing planned for today. Rest — or take something from “Week”."
    override val askTime = "Set a time?"
    override val askWhen = "Set a date?"
    override fun ideaBecameTask(title: String) = "Now a task: $title"
    override val granted = "allowed"
    override val youWrote = "You wrote"
    override fun nextOn(date: String) = "next on $date"
    override fun was(what: String) = "was $what"
    override val makeBrief = "Make a brief"
    override val refresh = "Refresh"
    override val startFocus = "Start with the main thing"
    override val focusWindow = "Time for the main thing"

    override val subtasks = "Subtasks"
    override val comment = "Comment"
    override val commentHint = "Add a comment"
    override val addSubtaskHint = "Add a subtask…"
    override val dates = "Dates"
    override val noDates = "No date"
    override val start = "Start"
    override val due = "Due"
    override val time = "Time"
    override val reminder = "Reminder"
    override val noReminder = "None"
    override val project = "Project"
    override val noProject = "No project"
    override val responsible = "Assignees"
    override val nobody = "None"
    override val delete = "Delete"
    override val deleteTaskQ = "Delete this task?"
    override val cancel = "Cancel"
    override val save = "Save"
    override val clear = "Clear"
    override val breakDown = "Break into steps"
    override fun breakDownQ(title: String) = "Break “$title” into steps?"
    override val breakDownYes = "Break down"
    override val notNeeded = "No thanks"
    override val plusWeek = "+1 week"
    override val me = "Me"
    override val title = "Title"
    override val peopleHint = "Names, comma separated"

    override val searchNotes = "Search notes…"
    override val newNote = "New note"
    override val noteTitleHint = "Title"
    override val noteBodyHint = "Write freely…"
    override val emptyNotes = "No notes yet"
    override val untitled = "Untitled"

    override val aiSection = "AI"
    override val apiKey = "API key"
    override val apiKeyHint = "sk-or-…"
    override val connected = "Connected"
    override val notConnected = "Not connected"
    override val check = "Check"
    override val mainModel = "Main model"
    override val mainModelHint = "briefs, plans, breakdowns"
    override val fastModel = "Quick parsing"
    override val fastModelHint = "turns your text into tasks"
    override val spentMonth = "Spent this month"
    override val appearance = "APPEARANCE"
    override val themeLight = "Light"
    override val themeDark = "Dark"
    override val themeAuto = "Auto"
    override val language = "Language"
    override val langAuto = "System"
    override val data = "DATA"
    override val searchModels = "Search models…"
    override val loading = "Loading…"
    override val perMillion = "per 1M tokens"


    override fun until(day: String) = "by $day"
    override val inWorkThisWeek = "In progress this week"
    override val thisMonth = "This month"
    override val waitingOthers = "Waiting on others"
    override val byDates = "by date"
    override val byProjects = "by project"
    override val showAll = "Show all"
    override val mainThing = "Focus"
    override val collapse = "Collapse"
    override val saved = "Saved"
    override val savedToInbox = "Saved to Inbox"
    override val inboxNote = "Inbox"
    override val inboxHint = "What isn’t sorted into tasks and notes yet"
    override val setupBriefQ = "Send a short summary of the day in the morning? Pick a time — or “No need”."
    override val setupBriefOptions = listOf("8:00", "9:00", "10:00", "No need")
    override val setupNameQ = "What should I call you? Type a name — or skip."
    override fun setupDone(name: String) = (if (name.isNotBlank()) "Nice to meet you, $name. " else "All set. ") +
        "Type below what needs doing, in plain words. You can change this the same way: “summary at 9”, “call me Anna”."
    override val settingsChanged = "Settings changed"
    override val inboxEmpty = "Nothing here"
    override val inboxEmptyHint = "Things written without internet land here and sort themselves once you’re back online."
    override val savedUnsorted = "SAVED UNSORTED"
    override fun queuedLine(n: Int) = "Waiting for internet: $n"
    override val nothingFound = "No tasks found"
    override val entryAdded = "Record added"
    override val addOrChange = "Add or change…"
    override fun more(n: Int) = "$n more"
    override fun doneCount(n: Int) = "Done · $n"
    override val records = "Records"
    override fun allRecords(n: Int) = "All records ($n)"
    override val attachPhoto = "Photos & videos"
    override val attachCamera = "Camera"
    override val attachFile = "File"
    override val attachContact = "Contact"
    override val transcribing = "Transcribing…"
    override val micPermission = "Microphone access is needed"
    override val voiceNote = "Voice"
    override val video = "Video"
    override fun nPhotos(n: Int) = "$n " + if (n == 1) "photo" else "photos"
    override fun nFiles(n: Int) = "$n " + if (n == 1) "file" else "files"
    override fun nVoice(n: Int) = "$n voice"
    override fun nContacts(n: Int) = "$n " + if (n == 1) "contact" else "contacts"
    override val notesHint = "A thought, an idea, meeting notes…"
    override val appendHint = "Add a thought…"
    override val allFilter = "All"
    override fun entriesCount(n: Int) = "$n " + if (n == 1) "entry" else "entries"
    override fun updated(d: String) = "updated $d"
    override val peopleTitle = "People"
    override val emptyPeople = "People appear by themselves once you mention them in tasks or notes."
    override val rename = "Rename"
    override val mergeWith = "Merge with…"
    override val aboutHint = "AI will describe who this is and what you do together"
    override val waitingFromThem = "Waiting on"
    override val voiceSection = "VOICE"
    override val voiceAi = "AI speech recognition"
    override val voiceAiHint = "Better names, terms and punctuation"
    override val voiceModel = "Recognition model"
    override val voiceSendNow = "Send right after dictation"

    override val offline = "Offline"
    override val offlineSaved = "No internet. Everything is saved to Inbox and will be sorted as soon as you're back online."
    override val queueDoneTitle = "Inbox sorted"
    override val waitingNet = "WAITING FOR INTERNET"
    override val whisperOfferOffline = "No internet. Download offline recognition in Settings to dictate without a connection."
    override val snoozeHour = "In an hour"
    override val repeat = "Repeat"
    override val noRepeat = "Don't repeat"
    override val everyDay = "Every day"
    override val weekdays = "Weekdays"
    override val everyWeek = "Every week"
    override val everyMonth = "Every month"
    override val everyYear = "Every year"
    override val overdueWaiting = "It's overdue"
    override val search = "Search"
    override val searchHint = "Tasks, notes, people…"
    override val nothing = "Nothing found"
    override val calendarSection = "CALENDAR"
    override val calendarRead = "Use the phone calendar"
    override val calendarHint = "AI sees meetings and plans around them"
    override val offlineSection = "OFFLINE RECOGNITION"
    override val whisperState = "Whisper on the phone"
    override val whisperMissing = "Not downloaded"
    override val whisperBase = "Base · 60 MB · faster"
    override val whisperSmall = "Accurate · 190 MB · better quality"
    override val whisperAlways = "Always recognize on the phone"
    override val whisperAlwaysHint = "Voice never leaves the phone"
    override val deleteModel = "Delete model"
    override val cancelDownload = "Stop download"
    override val backupSection = "BACKUP"
    override val googleBackupHint = "Once a day tasks, notes and settings are saved to your Google account (Google Drive) and come back on a new phone."
    override val googleBackup = "Copy to your Google account"
    override val googleBackupOff = "Off: if the phone is lost, tasks and notes won’t come back."
    override val skip = "Skip"
    override val next = "Next"
    override val begin = "Start"
    override val kindTask = "task"
    override val freshSummary = "FRESH SUMMARY"
    override val weekSummary = "PROJECT SUMMARY"
    override val toTaskPill = "Make a task"
    override val ideasOpen = "IDEAS WITHOUT TASKS"
    override fun waitLine(what: String) = "waiting: $what"
    override fun promiseLine(what: String) = "you promised: $what"
    override val ideaMark = "from an idea"
    override val linksTitle = "LINKS"
    override val linkIdea = "Idea"
    override val inTasks = "Already a task"
    override val fieldDue = "Date"
    override val fieldTime = "Time"
    override val fieldRepeat = "Repeat"
    override val fieldPeople = "People"
    override val fieldPlace = "Place"
    override val fieldRole = "Role"
    override val fieldCompany = "Company"
    override val fieldPhone = "Phone"
    override val fieldEmail = "Email"
    override val fieldAddress = "Address"
    override val personalTitle = "PERSONAL"
    override val personalHint = "birthday, family, what they like"
    override val copied = "Copied"
    override val placeHint = "Address or name, e.g. “5 Lenin St”"
    override val historyTitle = "HISTORY"
    override val srcTaskEntry = "from a task record"
    override val srcChat = "from a conversation"
    override val srcNote = "note"
    override val noDue = "no date"
    override val tasksCaps = "TASKS"
    override val makeSummary = "Make a summary"
    override fun fromNote(title: String, date: String) = "from note “$title” · $date"
    override fun fromChat(date: String) = "from a conversation · $date"
    override fun promised(who: String) = "You promised: $who"
    override fun summaryAt(t: String) = "updated $t"
    override val reopened = "Task reopened"
    override val markedDone = "Done"
    override val allDoneToday = "Today's main things are done"
    override val quickToday = "Today"
    override val quickTomorrow = "Tomorrow"
    override val quickNextWeek = "Next week"
    override val quickNone = "No date"
    override val rangeLink = "Range…"
    override val singleLink = "Single date"
    override val savedTick = "Saved"
    override val toSeparateNote = "as a separate note"
    override val devSection = "DEVELOPER"
    override val devUnlocked = "Developer section unlocked"
    override val onDevice = "Recognize speech on the phone"
    override val onDeviceHint = "Voice never leaves the phone. Text is still understood in the cloud."
    override val onDeviceModel = "On-device model"
    override fun movedTo(d: String) = "Moved to $d"
    override fun briefAt(t: String) = "updated $t"
    override fun version(v: String) = "Margin $v"
    override val parsingMode = "Message parsing"
    override val modeHybrid = "Hybrid"
    override val modeEconomy = "Economy"
    override val modePrecise = "Precise"
    override val modeHybridHint = "Simple phrases — Alice Flash, complex ones — DeepSeek"
    override val modeEconomyHint = "Everything via Alice Flash: cheaper, more mistakes"
    override val modePreciseHint = "Everything via DeepSeek: more accurate, about twice the cost"
    override fun kindTasks(n: Int) = "$n tasks"
    override val kindChanged = "updated"
    override val kindNote = "note"
    override val kindContact = "contact"
    override val readMore = "read more"
    override fun notesToday(titles: String) = "In notes today: $titles"
    override val a11yMenu = "Menu"
    override val a11ySend = "Send"
    override val a11yMic = "Dictate"
    override val a11yAttach = "Attach"
    override val allow = "Allow"
    override val ob1Title = "Write the way you think"
    override val ob1Text = "In one sentence: “launch the site this week, Dima does design”. Margin makes the task, dates, subtasks and owners."
    override val ob2Title = "Gestures"
    override val ob2Text = "Swipe right — done. Swipe left — move a day later. Menu — the ☰ button at the top left. Tap a task to open it."
    override val ob3Title = "Voice, photos, files"
    override val ob3Text = "The mic transcribes with AI, “+” attaches photos, files and contacts. Thoughts go to notes, people to People."
    override val ob4Title = "A couple of permissions"
    override val ob4Text = "Notifications — for reminders. Calendar — so AI plans around your meetings. You can change this in Settings."
    override val notifications = "Notifications"
    override val calendar = "Calendar"
    override fun queueDoneText(n: Int) = "Sorted: $n"
    override fun remindPerson(name: String) = "Nudge $name"
    override fun nudgeText(what: String) = "Hi! How is “$what” going?"
    override fun whisperDownloading(p: Int) = "Downloading… $p%"
    override fun whisperReady(name: String) = "Ready · $name"
    override val errNoKey = "Add your OpenRouter key in settings"
    override val errWeird = "Unexpected response from OpenRouter"
    override val errNet = "No internet connection"
    override val errService = "The AI is unavailable right now. Try again a bit later"
    override val errEmpty = "Couldn’t make sense of it. Please try again"
    override val errBadKey = "The OpenRouter key doesn't work — check it in settings"
    override val errNoMoney = "Your OpenRouter balance is empty"
    override val errRate = "Too many requests, try again in a minute"
    override fun errGeneric(code: Int) = "OpenRouter error ($code)"
    override fun keyStatus(usage: Double?, limit: Double?) = buildString {
        append("Connected")
        if (usage != null) append(" · spent $" + "%.2f".format(java.util.Locale.US, usage))
        if (limit != null) append(" of $" + "%.0f".format(java.util.Locale.US, limit))
    }

    override val notifChannel = "Reminders"
    override val notifMorningTitle = "Good morning"
    override fun notifMorningText(n: Int, overdue: Int) =
        "Today: " + tasksCount(n) + if (overdue > 0) ", overdue: $overdue" else ""
    override val notifEveningTitle = "Your day"
    override fun notifEveningText(done: Int, left: Int) = "Done: $done. Left for today: $left"
    override val notifReminder = "Reminder"

    override val aiLanguage = "English"
    override val promptNext = "What should I do next? Suggest one or two tasks given deadlines and the time of day."
    override val promptPlanDay = "Make a realistic plan for today: give today's tasks times via update_task (time), starting from the next free hour, leaving already-timed tasks alone. In reply, 1–2 sentences on what matters most."
    override fun promptBreakDown(what: String) = "Break $what into concrete steps."
}

/** Current language for code outside composition (dates, notifications, prompts). */
object L {
    @Volatile var lang: Lang = Lang.Ru
    val s: Strings get() = if (lang == Lang.Ru) Ru else En
}

fun resolveLang(mode: ru.anjeib.margin.data.LangMode): Lang = when (mode) {
    ru.anjeib.margin.data.LangMode.Ru -> Lang.Ru
    ru.anjeib.margin.data.LangMode.En -> Lang.En
    ru.anjeib.margin.data.LangMode.Auto ->
        if (java.util.Locale.getDefault().language in setOf("ru", "uk", "be", "kk")) Lang.Ru else Lang.En
}

fun stringsFor(lang: Lang): Strings = if (lang == Lang.Ru) Ru else En
