package ru.anjeib.margin.i18n

import ru.anjeib.margin.data.dative
import ru.anjeib.margin.data.genitive

import ru.anjeib.margin.data.pluralRu
import ru.anjeib.margin.data.shortLabel

enum class Lang { Ru, En }

/** All user-facing text. One instance per language; the UI never mixes them. */
/** One part of «Помощь»: a line of explanation, examples to say, and «what → what it does» pairs. */
data class HelpSection(val title: String, val text: String, val examples: List<String> = emptyList(), val pairs: List<Pair<String, String>> = emptyList())

interface Strings {
    // help & tour
    val helpTitle: String
    val helpIntro: String
    val helpSections: List<HelpSection>
    val tourWriteTitle: String
    val tourWriteText: String
    val tourWriteExample: String
    val tourGesturesTitle: String
    val tourGesturesText: String
    val tourSwipeDone: String
    val tourSwipeLater: String
    val tourFixTitle: String
    val tourFixText: String
    val tourCommands: List<String>
    val tourMore: String
    val tourDemoCall: String
    val tourDemoEdits: String
    val tourDemoName: String
    val aiUndoTitle: String
    val inTaskWord: String
    fun aiUndoLine(where: String, n: Int, undone: Int): String
    // forwarding
    val close: String
    val connect: String
    val disconnect: String
    val viaMail: String
    val viaOther: String
    val viaCopy: String
    val viaMailTitle: String
    val forwardKept: String
    val tgBadToken: String
    val tgWelcome: String
    val tgNotYours: String
    val mailBadLogin: String
    val forwardTitle: String
    val forwardCommentHint: String
    val forwardHint: String
    val forwardSend: String
    val forwardDone: String
    val pasteCopied: String
    val forwardSection: String
    val tgHint: String
    val mailHint: String
    val maxHint: String
    val tgOpenBot: String
    val tgWhy: String
    val tgStep1: String
    val tgOpenBotFather: String
    val tgStep2: String
    val tgTokenHint: String
    val tgStep3: String
    val mailWhy: String
    val mailAddressHint: String
    val mailPasswordHint: String
    val mailPasswordHow: String
    fun waitingShort(n: String): String
    val promiseWord: String
    val feedbackTitle: String
    val feedbackStub: String
    val forwardHowTo: String
    val allDay: String
    val addContact: String
    fun notAContact(n: String): String
    val inContacts: String
    val promisesTitle: String
    fun promiseToYou(from: String): String
    /** «Вам от » + «Марины» + «» — the name part becomes a link. */
    fun promiseToYouParts(from: String): Triple<String, String, String>
    fun promiseFromYouParts(to: String): Triple<String, String, String>
    fun promiseFromYou(to: String): String
    fun promisedShort(n: String): String
    fun forwardFrom(via: String): String
    fun forwardFiles(n: Int): String
    fun tgOn(bot: String): String
    fun mailOn(a: String): String
    fun tgConnectedText(bot: String): String
    fun mailConnectedText(a: String): String
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
    fun nextDue(d: String): String
    val archivedOne: String
    val restoredOne: String
    val deletedOne: String
    val toArchive: String
    val archiveTitle: String
    val archiveEmpty: String
    val restoreWord: String
    val calendarTitle: String
    val projectsEmpty: String
    val projectsHint: String
    fun projectStats(tasks: Int, notes: Int): String
    val postponeTitle: String
    val inAWeek: String
    val inAMonth: String
    val pickDay: String
    fun fromMessage(stamp: String): String
    val saveToPhone: String
    val savedToPhone: String
    val saveFailed: String
    val shareWord: String
    val closeWord2: String
    val contactInNote: String
    fun noSubtask(n: Int): String
    fun dayPart(p: String): String
    val insertBelow: String
    val captionWord: String
    val moveUp: String
    val moveDown: String
    val readingPhoto: String
    val photoTextAction: String
    val insertAsText: String
    val makeContact: String
    val makeTasks: String
    val noTextOnPhoto: String
    fun contactMade(name: String): String
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
    // v2 «Темы»
    val solvedWord: String
    val gotIt: String
    fun archivedTopic(t: String): String
    val feedTab: String
    val questionsTab: String
    val materialsTab: String
    fun topicSince(d: String): String
    val questionPrefix: String
    val solvedPrefix: String
    fun waitPrefix(who: String): String
    fun gotPrefix(who: String): String
    val becameTopic: String
    val revivedTopic: String
    fun solvedOn(d: String): String
    fun gotOn(d: String): String
    val allSettled: String
    val archiveQ: String
    fun archiveText(t: String): String
    fun inArchiveSince(d: String): String
    val bringBack: String
    val openCaps: String
    val waitingCaps: String
    val solvedCaps: String
    fun inFeed(d: String): String
    val noQuestions: String
    val newQuestionHint: String
    val writeInTopic: String
    val materialsEmpty: String
    val searchMaterials: String
    val kindPhotos: String
    val kindDocs: String
    val kindVoice: String
    val noMaterialsFound: String
    val topicsInWork: String
    val simpleTasks: String
    val thoughts: String
    val noNextStepCaps: String
    fun nQuestions(n: Int): String
    fun waitUntil(d: String): String
    val allDoneShort: String
    fun nMaterials(n: Int): String
    fun nEntries(n: Int): String
    val thoughtWord: String
    val whatNext: String
    val labelWord: String
    val casesTitle: String
    val materialsTitle: String
    val materialsSub: String
    val noLabel: String
    val noDateQ: String
    val kindTopic: String
    val kindThought: String
    fun lateSince(d: String): String
    fun doneCountWord(n: Int): String
    val freeWord: String
    val dealsBadge: String
    val topicsBadge: String
    fun buildLabel(name: String, code: Int, debug: Boolean): String
    val backWord: String
    val todayShort: String
    fun progressOf(d: Int, n: Int): String
    fun progressSolved(d: Int, n: Int): String
    fun movedToTopic(t: String): String
    val toOtherTopic: String
    val toTopicPick: String
    val recognizeWord: String
    val feedTitle: String
    val filterWord: String
    val filterAll: String
    val spherePersonal: String
    val sphereWork: String
    val sphereAdd: String
    val orgsBadge: String
    val peopleBadge: String
    val markOrg: String
    val markPerson: String
    val stepOne: String
    val stepHint: String
    val peopleSub: String
    val archiveSub: String
    val sinceWord: String
    val sortsWhenOnline: String
    val unplacedBadge: String
    val photoNoCaption: String
    fun thoughtSince(d: String): String
    val thoughtsToday: String
    val questionDateQ: String
}

object Ru : Strings {
    override val helpTitle = "Помощь"
    override val helpIntro = "Коротко о том, как устроен Margin."
    override val helpSections = listOf(
        HelpSection("Как писать", "Обычными словами или голосом. В одном сообщении может быть несколько дел — Margin разложит их сам.",
            examples = listOf("Завтра в 15 созвон с Мариной", "Лена до пятницы пришлёт график, я обещал Виктору оплату до 10-го", "Идея: на сайте нужен калькулятор цен")),
        HelpSection("Разделы", "", pairs = listOf(
            "Задачи" to "дела со сроком и без: Сегодня, Неделя, Все",
            "Заметки" to "мысли, идеи, фото, голосовые, списки",
            "Контакты" to "люди и компании, которых вы добавили: «+ контакт» в пузыре, нажатие на серое имя или «Добавить контакт: …»",
            "Проекты" to "всё по одной теме: задачи, заметки, люди",
            "Входящие" to "ещё не разобранное: без интернета и пересланное",
            "Календарь" to "месяц и число дел в каждом дне",
            "Архив" to "убранные заметки и контакты, можно вернуть",
        )),
        HelpSection("Обещания", "Margin отмечает, кто кому что должен. На главной — обещания на ближайшие три дня и просроченные.", pairs = listOf(
            "«Марина пришлёт правки до среды»" to "под задачей: Вам от Марины · ср",
            "«Обещал Виктору оплату до 10-го»" to "под задачей: Виктору от Вас · сб",
            "⧗ песочные часы" to "нажмите, когда получили",
        )),
        HelpSection("Внутри задачи", "Всё, что вы пишете в карточке, относится к этой задаче.",
            examples = listOf("Подзадачи: купить полис, взять паспорт", "Подзадача 2 готова", "Перенеси на пятницу в 15:00", "Место: Ленина, 5", "Это у доктора Иванова, не забыть полис")),
        HelpSection("Внутри заметки", "Написанное добавляется новым блоком.", pairs = listOf(
            "«+»" to "фото, видео, голосовое, файл, контакт, список",
            "нажатие на блок" to "вставить рядом",
            "удержание" to "изменить, подпись, удалить",
            "«В задачу»" to "сделать из идеи задачу",
        )),
        HelpSection("Команды", "Если Margin понял не так — скажите прямо. Команды понимаются сразу, без ИИ.", pairs = listOf(
            "Добавить задачу: …" to "новая задача",
            "Добавить заметку: …" to "новая заметка",
            "Добавить контакт: …" to "человек или компания",
            "Добавить подзадачу: …" to "в открытую задачу",
            "Исправить: …" to "изменить существующее, ничего нового",
            "Место / Время / Телефон: …" to "одно поле задачи",
        )),
        HelpSection("Жесты", "", pairs = listOf(
            "вправо по задаче" to "готово",
            "влево по задаче" to "отложить: завтра, неделя, месяц, свой срок",
            "влево по заметке, контакту" to "в архив",
            "вправо по заметке, контакту" to "удалить",
            "«Отменить»" to "есть под каждым изменением несколько секунд",
        )),
        HelpSection("Связи", "Задача помнит, откуда пришла: «↳ из сообщения» или идея из заметки. В карточке — «Связи»: люди, их другие обещания, проект. Под задачей ссылки выделены цветом: контакт открывает его страницу, адрес — карту."),
        HelpSection("Пересылка", "Чужое сообщение — из Telegram, MAX, почты — Margin разберёт как чужие слова: что вам обещали, о чём просят.", pairs = listOf(
            "«Поделиться»" to "→ «В Margin»",
            "выделить текст" to "→ «В Margin» в меню",
            "«Копировать»" to "→ в Margin «Вставить скопированное»",
        )),
        HelpSection("Голос и файлы", "", pairs = listOf(
            "микрофон" to "диктовка — сразу текстом",
            "«+» → «Голосовое»" to "запись сохраняется с расшифровкой",
            "фото → «Распознать»" to "текст, визитка в контакт, доска в задачи",
        )),
    )
    override val tourWriteTitle = "Пишите как думаете"
    override val tourWriteText = "Словами или голосом — Margin сам разложит на задачи, сроки и обещания."
    override val tourWriteExample = "Завтра в 15 созвон с Мариной, она пришлёт правки до среды"
    override val tourGesturesTitle = "Жесты"
    override val tourGesturesText = "Нажатие открывает карточку задачи — всё, что пишете в ней, относится к этой задаче."
    override val tourSwipeDone = "→ вправо — готово"
    override val tourSwipeLater = "← влево — отложить"
    override val tourFixTitle = "Если понял не так"
    override val tourFixText = "Под каждым изменением есть «Отменить». Или скажите прямо:"
    override val tourCommands = listOf("Добавить задачу: …", "Добавить заметку: …", "Добавить контакт: …", "Исправить: …")
    override val tourMore = "Подробнее — в меню, «Помощь»."
    override val tourDemoCall = "Созвон с Мариной"
    override val tourDemoEdits = "Получить правки от Марины"
    override val tourDemoName = "Марина"
    override val aiUndoTitle = "Отмены после ИИ"
    override val inTaskWord = "В задаче"
    override fun aiUndoLine(where: String, n: Int, undone: Int) = "$where: $undone из $n" + if (n > 0) " (${undone * 100 / n}%)" else ""
    override val close = "Закрыть"
    override val connect = "Подключить"
    override val disconnect = "Отключить"
    override val viaMail = "почты"
    override val viaOther = "другого приложения"
    override val viaCopy = "буфера обмена"
    override val viaMailTitle = "Почта"
    override val forwardKept = "Сохранено во «Входящие»"
    override val tgBadToken = "Токен не подошёл — скопируйте его из @BotFather целиком"
    override val tgWelcome = "Готово, это ваш бот Margin. Пересылайте сюда сообщения — из чатов, каналов, от кого угодно. Margin разберёт: задачи, обещания, заметки — и ответит, что получилось. Можно и просто написать, что нужно сделать."
    override val tgNotYours = "Это личный бот, он принимает сообщения только от владельца."
    override val mailBadLogin = "Почта не пустила: проверьте адрес и пароль приложения (обычный пароль не подойдёт)"
    override val forwardTitle = "В Margin"
    override val forwardCommentHint = "Что с этим сделать — или просто отправьте"
    override val forwardHint = "Margin разберёт: задачи, обещания, заметки"
    override val forwardSend = "Отправить"
    override val forwardDone = "Отправлено в Margin"
    override val pasteCopied = "Вставить скопированное"
    override val forwardSection = "Пересылка в Margin"
    override val tgHint = "Свой бот: пересылайте ему сообщения"
    override val mailHint = "Пересылайте письма себе — Margin их заберёт"
    override val maxHint = "Своих ботов MAX не даёт. Нажмите «Копировать» на сообщении и откройте Margin — появится «Вставить скопированное». Или выделите текст → «В Margin»."
    override val tgOpenBot = "Открыть бота"
    override val tgWhy = "Бот будет только вашим: пересылаете ему сообщение — Margin разбирает и отвечает, что получилось. Сообщения идут через Telegram прямо в телефон."
    override val tgStep1 = "Откройте @BotFather, отправьте /\u2060newbot, придумайте имя и адрес бота."
    override val tgOpenBotFather = "Открыть @BotFather"
    override val tgStep2 = "Скопируйте токен из его ответа и вставьте сюда:"
    override val tgTokenHint = "123456:ABC…"
    override val tgStep3 = "Нажмите «Подключить», затем «Старт» в своём боте — он запомнит вас как владельца."
    override val mailWhy = "Перешлите письмо на свой же адрес (или на адрес с «+margin»: ivan+margin@yandex.ru) — Margin заберёт его. В почте ничего не помечается и не удаляется."
    override val mailAddressHint = "Адрес почты"
    override val mailPasswordHint = "Пароль приложения"
    override val mailPasswordHow = "Пароль приложения создаётся в настройках почты, обычный не подойдёт. Яндекс: id.yandex.ru → Безопасность → Пароли приложений → Почта (и включите IMAP в настройках почты). Gmail: myaccount.google.com/apppasswords. Mail.ru: Настройки → Безопасность → Пароли для внешних приложений."
    override fun waitingShort(n: String) = "обещание: Вам от ${n.genitive()}"
    override fun promisedShort(n: String) = "обещание: ${n.dative()} от Вас"
    override val promiseWord = "Обещание"
    override val feedbackTitle = "Пожаловаться или похвалить"
    override val feedbackStub = "Скоро здесь можно будет написать нам напрямую — что мешает, чего не хватает, что нравится. Спасибо, что пользуетесь Margin."
    override val forwardHowTo = "Из Telegram, MAX, почты: «Поделиться» или выделить текст → «В Margin». Или «Копировать» — и в Margin появится «Вставить скопированное»."
    override val allDay = "весь день"
    override val addContact = "+ контакт"
    override fun notAContact(n: String) = "$n — нет в контактах"
    override val inContacts = "в контактах"
    override val promisesTitle = "Обещания"
    override fun promiseToYou(from: String) = "Вам от ${from.genitive()}"
    override fun promiseToYouParts(from: String) = Triple("Вам от ", from.genitive(), "")
    override fun promiseFromYouParts(to: String) = Triple("", to.dative(), " от Вас")
    override fun promiseFromYou(to: String) = "${to.dative()} от Вас"
    override fun forwardFrom(via: String) = "из $via"
    override fun forwardFiles(n: Int) = "📎 $n"
    override fun tgOn(bot: String) = "@$bot — пересылайте ему сообщения"
    override fun mailOn(a: String) = "Пересылайте письма на $a"
    override fun tgConnectedText(bot: String) = "Подключён @$bot. Пересылайте ему сообщения: Margin разберёт их и ответит, что получилось. Пока Margin открыт — сразу, иначе в течение 15 минут."
    override fun mailConnectedText(a: String) = "Пересылайте письма на $a — Margin заберёт их. Пока Margin открыт — каждые две минуты, иначе в течение 15 минут."
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
    override fun waitingFor(who: String) = "Обещание: Вам от ${who.genitive()}"
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
    override val people = "КОНТАКТЫ"
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
    override fun nextDue(d: String) = "ближайшее: $d"
    override val archivedOne = "Убрано в архив"
    override val restoredOne = "Возвращено"
    override val deletedOne = "Удалено"
    override val toArchive = "В архив"
    override val archiveTitle = "Архив"
    override val archiveEmpty = "Сюда уходят решённые темы — со всей лентой и материалами. Новость в теме или «Вернуть» возвращают её."
    override val restoreWord = "Вернуть"
    override val calendarTitle = "Календарь"
    override val projectsEmpty = "Проектов пока нет"
    override val projectsHint = "Проект появляется сам, когда вы пишете о нём: «для vuzburo купить домен», «идея по сайту vuzburo»."
    override fun projectStats(tasks: Int, notes: Int) = listOfNotNull(tasksCount(tasks).takeIf { tasks > 0 }, notes.takeIf { it > 0 }?.let { "$it " + pluralRu(it, "заметка", "заметки", "заметок") }).joinToString(" · ").ifBlank { "пусто" }
    override val postponeTitle = "Отложить"
    override val inAWeek = "Через неделю"
    override val inAMonth = "Через месяц"
    override val pickDay = "Свой срок…"
    override fun fromMessage(stamp: String) = "из сообщения $stamp"
    override val saveToPhone = "Сохранить на телефон"
    override val savedToPhone = "Сохранено в папку Margin"
    override val saveFailed = "Не получилось сохранить — попробуйте «Переслать»"
    override val shareWord = "Переслать"
    override val closeWord2 = "Закрыть"
    override val contactInNote = "контакт в заметке"
    override fun noSubtask(n: Int) = "Подзадачи $n нет"
    override fun dayPart(p: String) = when (p) { "morning" -> "утром"; "afternoon" -> "после обеда"; "evening" -> "вечером"; "night" -> "ночью"; else -> "днём" }
    override val insertBelow = "Вставить ниже"
    override val captionWord = "Подпись"
    override val moveUp = "Выше"
    override val moveDown = "Ниже"
    override val readingPhoto = "Читаю текст на фото…"
    override val photoTextAction = "Распознать"
    override val insertAsText = "Вставить текстом"
    override val makeContact = "Создать контакт"
    override val makeTasks = "Сделать задачи"
    override val noTextOnPhoto = "На фото не нашлось текста"
    override fun contactMade(name: String) = "В «Контактах»: $name"
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
    override val langAuto = "Системный"
    override val data = "ДАННЫЕ"
    override val searchModels = "Поиск модели…"
    override val loading = "Загрузка…"
    override val perMillion = "за 1M токенов"


    override fun until(day: String) = "до $day"
    override val inWorkThisWeek = "На неделе"
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
    override val inboxHint = "что ещё не разложено по делам и темам"
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
    override val peopleTitle = "Контакты"
    override val emptyPeople = "Контакты появятся сами, когда вы упомянете человека или компанию в задачах или заметках."
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
    override val searchHint = "Задачи, заметки, контакты…"
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
    override val inTasks = "В задачах"
    override val fieldDue = "Срок"
    override val fieldTime = "Время"
    override val fieldRepeat = "Повтор"
    override val fieldPeople = "Контакты"
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
    override fun promised(who: String) = "Обещание: ${who.dative()} от Вас"
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
    override val ob3Text = "Микрофон распознаёт речь с помощью ИИ, «+» прикрепляет фото, файлы и контакты. Мысли и идеи попадают в заметки, люди и компании — в «Контакты»."
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
    // v2 «Темы»
    override val solvedWord = "Решено"
    override val gotIt = "Получено"
    override fun archivedTopic(t: String) = "«$t» в архиве"
    override val feedTab = "Лента"
    override val questionsTab = "Вопросы"
    override val materialsTab = "Материалы"
    override fun topicSince(d: String) = "тема с $d"
    override val questionPrefix = "Вопрос:"
    override val solvedPrefix = "Решено:"
    override fun waitPrefix(who: String) = "Ждём от ${who.genitive()}:"
    override fun gotPrefix(who: String) = "Получено от ${who.genitive()}:"
    override val becameTopic = "Задача стала темой"
    override val revivedTopic = "Тема вернулась из архива"
    override fun solvedOn(d: String) = "решено $d"
    override fun gotOn(d: String) = "получено $d"
    override val allSettled = "Похоже, всё решено."
    override val archiveQ = "Тема решена?"
    override fun archiveText(t: String) = "«$t» уйдёт в архив вместе с материалами. Новое событие в ней вернёт её обратно."
    override fun inArchiveSince(d: String) = "В архиве с $d"
    override val bringBack = "Вернуть"
    override val openCaps = "Открыто"
    override val waitingCaps = "Ждём"
    override val solvedCaps = "Решено"
    override fun inFeed(d: String) = "в ленте $d ›"
    override val noQuestions = "Вопросов нет. Напишите внизу — появится вопрос."
    override val newQuestionHint = "Новый вопрос…"
    override val writeInTopic = "Написать в тему…"
    override val materialsEmpty = "Материалов пока нет. Фото, файлы и голосовые из ленты появятся здесь."
    override val searchMaterials = "Искать в материалах и в тексте на фото"
    override val kindPhotos = "Фото"
    override val kindDocs = "Документы"
    override val kindVoice = "Голосовые"
    override val noMaterialsFound = "Ничего не нашлось."
    override val topicsInWork = "Темы"
    override val simpleTasks = "Дела"
    override val thoughts = "Идеи"
    override val noNextStepCaps = "Без следующего шага"
    override fun nQuestions(n: Int) = "$n " + pluralRu(n, "вопрос", "вопроса", "вопросов")
    override fun waitUntil(d: String) = "ждём до $d"
    override val allDoneShort = "всё решено"
    override fun nMaterials(n: Int) = "$n " + pluralRu(n, "материал", "материала", "материалов")
    override fun nEntries(n: Int) = "$n " + pluralRu(n, "запись", "записи", "записей")
    override val thoughtWord = "идея"
    override val whatNext = "Что дальше?"
    override val labelWord = "Метка"
    override val casesTitle = "Дела"
    override val materialsTitle = "Материалы"
    override val materialsSub = "все фото, файлы и голосовые из тем"
    override val noLabel = "Без метки"
    override val noDateQ = "без срока"
    override val kindTopic = "в тему"
    override val kindThought = "идея"
    override fun lateSince(d: String) = "с $d"
    override fun doneCountWord(n: Int) = "сделано $n"
    override val freeWord = "свободно"
    override val dealsBadge = "Дела"
    override val topicsBadge = "Темы"
    override fun buildLabel(name: String, code: Int, debug: Boolean) = "Margin 2 · сборка $name ($code)" + if (debug) " · отладка" else ""
    override val backWord = "Назад"
    override val todayShort = "сег."
    override fun progressOf(d: Int, n: Int) = "$d из $n"
    override fun progressSolved(d: Int, n: Int) = "$d из $n решено"
    override fun movedToTopic(t: String) = "Перенесено в «$t»"
    override val toOtherTopic = "В другую тему"
    override val toTopicPick = "В тему…"
    override val recognizeWord = "Распознать"
    override val feedTitle = "Лента"
    override val filterWord = "Показать"
    override val filterAll = "Всё"
    override val spherePersonal = "Личное"
    override val sphereWork = "Работа"
    override val sphereAdd = "личное или работа?"
    override val orgsBadge = "Организации"
    override val peopleBadge = "Люди"
    override val markOrg = "Это организация"
    override val markPerson = "Это человек"
    override val stepOne = "вопрос"
    override val stepHint = "с вопросами дело станет темой"
    override val peopleSub = "люди из ваших дел и тем"
    override val archiveSub = "решённые темы — со всеми материалами"
    override val sinceWord = "с"
    override val sortsWhenOnline = "разберётся, когда появится связь"
    override val unplacedBadge = "Без места"
    override val photoNoCaption = "Фото без подписи"
    override fun thoughtSince(d: String) = "идея с $d"
    override val thoughtsToday = "Идеи за сегодня"
    override val questionDateQ = "Срок вопроса"
}

object En : Strings {
    override val helpTitle = "Help"
    override val helpIntro = "How Margin works, in short."
    override val helpSections = listOf(
        HelpSection("How to write", "In plain words or by voice. One message can hold several things — Margin sorts them out.",
            examples = listOf("Call with Marina tomorrow at 3 pm", "Lena sends the schedule by Friday, I promised Victor the payment by the 10th", "Idea: the site needs a price calculator")),
        HelpSection("Sections", "", pairs = listOf(
            "Tasks" to "with or without dates: Today, Week, All",
            "Notes" to "thoughts, ideas, photos, voice, lists",
            "Contacts" to "people and companies you added: “+ contact” in the bubble, a tap on a grey name or “Add contact: …”",
            "Projects" to "everything on one topic: tasks, notes, people",
            "Inbox" to "not sorted yet: offline and forwarded",
            "Calendar" to "the month and how much each day holds",
            "Archive" to "notes and contacts put away, can come back",
        )),
        HelpSection("Promises", "Margin keeps track of who owes whom. The main screen shows promises due within three days and late ones.", pairs = listOf(
            "“Marina sends the edits by Wednesday”" to "under the task: to you from Marina · Wed",
            "“Promised Victor the payment by the 10th”" to "under the task: to Victor from you · Sat",
            "⧗ hourglass" to "tap when you got it",
        )),
        HelpSection("Inside a task", "Whatever you write in the card is about this task.",
            examples = listOf("Subtasks: buy the policy, take the passport", "Subtask 2 done", "Move to Friday 3 pm", "Place: 5 Lenina St", "It’s at Dr Ivanov’s, don’t forget the policy")),
        HelpSection("Inside a note", "What you write becomes a new block.", pairs = listOf(
            "“+”" to "photo, video, voice, file, contact, list",
            "tap a block" to "insert next to it",
            "hold" to "edit, caption, delete",
            "“To task”" to "turn an idea into a task",
        )),
        HelpSection("Commands", "If Margin got it wrong — say it directly. Commands are understood at once, without AI.", pairs = listOf(
            "Add task: …" to "a new task",
            "Add note: …" to "a new note",
            "Add contact: …" to "a person or a company",
            "Add subtask: …" to "to the open task",
            "Fix: …" to "change what exists, nothing new",
            "Place / Time / Phone: …" to "one field of the task",
        )),
        HelpSection("Gestures", "", pairs = listOf(
            "right on a task" to "done",
            "left on a task" to "put off: tomorrow, week, month, a date",
            "left on a note, contact" to "archive",
            "right on a note, contact" to "delete",
            "“Undo”" to "under every change for a few seconds",
        )),
        HelpSection("Links", "A task remembers where it came from: “↳ from a message” or an idea in a note. The card shows “Links”: people, their other promises, the project. Under a task, links are in colour: a contact opens their page, an address opens the map."),
        HelpSection("Forwarding", "Someone else’s message — from Telegram, MAX, mail — Margin reads as their words: what they promised you, what they ask.", pairs = listOf(
            "“Share”" to "→ “To Margin”",
            "select text" to "→ “To Margin” in the menu",
            "“Copy”" to "→ in Margin “Paste what you copied”",
        )),
        HelpSection("Voice and files", "", pairs = listOf(
            "mic" to "dictation — straight to text",
            "“+” → “Voice”" to "a recording kept with its transcript",
            "photo → “Recognize”" to "text, a business card to a contact, a whiteboard to tasks",
        )),
    )
    override val tourWriteTitle = "Write as you think"
    override val tourWriteText = "In words or by voice — Margin turns it into tasks, dates and promises."
    override val tourWriteExample = "Call with Marina tomorrow at 3 pm, she sends the edits by Wednesday"
    override val tourGesturesTitle = "Gestures"
    override val tourGesturesText = "A tap opens the task card — whatever you write there is about that task."
    override val tourSwipeDone = "→ right — done"
    override val tourSwipeLater = "← left — put off"
    override val tourFixTitle = "If it got it wrong"
    override val tourFixText = "Every change has “Undo”. Or say it directly:"
    override val tourCommands = listOf("Add task: …", "Add note: …", "Add contact: …", "Fix: …")
    override val tourMore = "More in the menu, “Help”."
    override val tourDemoCall = "Call with Marina"
    override val tourDemoEdits = "Get the edits from Marina"
    override val tourDemoName = "Marina"
    override val aiUndoTitle = "Undone after AI"
    override val inTaskWord = "In a task"
    override fun aiUndoLine(where: String, n: Int, undone: Int) = "$where: $undone of $n" + if (n > 0) " (${undone * 100 / n}%)" else ""
    override val close = "Close"
    override val connect = "Connect"
    override val disconnect = "Disconnect"
    override val viaMail = "mail"
    override val viaOther = "another app"
    override val viaCopy = "clipboard"
    override val viaMailTitle = "Mail"
    override val forwardKept = "Saved to Inbox"
    override val tgBadToken = "The token didn’t work — copy all of it from @BotFather"
    override val tgWelcome = "Done, this is your Margin bot. Forward messages here — from chats, channels, anyone. Margin sorts them into tasks, promises and notes and replies with what it made. You can also just write what needs doing."
    override val tgNotYours = "This is a personal bot; it only takes messages from its owner."
    override val mailBadLogin = "Mail refused: check the address and the app password (the usual password won’t do)"
    override val forwardTitle = "To Margin"
    override val forwardCommentHint = "What to do with it — or just send"
    override val forwardHint = "Margin will sort it: tasks, promises, notes"
    override val forwardSend = "Send"
    override val forwardDone = "Sent to Margin"
    override val pasteCopied = "Paste what you copied"
    override val forwardSection = "Forwarding to Margin"
    override val tgHint = "Your own bot: forward messages to it"
    override val mailHint = "Forward letters to yourself — Margin picks them up"
    override val maxHint = "MAX gives no personal bots. Tap “Copy” on a message and open Margin — “Paste what you copied” appears. Or select text → “To Margin”."
    override val tgOpenBot = "Open the bot"
    override val tgWhy = "The bot is yours alone: forward a message to it — Margin sorts it and replies with what it made. Messages go through Telegram straight to your phone."
    override val tgStep1 = "Open @BotFather, send /\u2060newbot, choose a name and an address."
    override val tgOpenBotFather = "Open @BotFather"
    override val tgStep2 = "Copy the token from its reply and paste it here:"
    override val tgTokenHint = "123456:ABC…"
    override val tgStep3 = "Tap “Connect”, then “Start” in your bot — it remembers you as the owner."
    override val mailWhy = "Forward a letter to your own address (or to a “+margin” one: ivan+margin@gmail.com) — Margin picks it up. Nothing is marked or deleted in your mail."
    override val mailAddressHint = "Email address"
    override val mailPasswordHint = "App password"
    override val mailPasswordHow = "An app password is made in your mail settings; the usual one won’t do. Gmail: myaccount.google.com/apppasswords. Yandex: id.yandex.ru → Security → App passwords → Mail (and turn on IMAP). Mail.ru: Settings → Security → Passwords for external apps."
    override fun waitingShort(n: String) = "promise to you from $n"
    override fun promisedShort(n: String) = "promise to $n from you"
    override val promiseWord = "Promise"
    override val feedbackTitle = "Complain or praise"
    override val feedbackStub = "Soon you’ll be able to write to us right here — what gets in the way, what’s missing, what you like. Thank you for using Margin."
    override val forwardHowTo = "From Telegram, MAX, mail: “Share” or select text → “To Margin”. Or “Copy” — and “Paste what you copied” appears in Margin."
    override val allDay = "all day"
    override val addContact = "+ contact"
    override fun notAContact(n: String) = "$n isn’t in contacts"
    override val inContacts = "in contacts"
    override val promisesTitle = "Promises"
    override fun promiseToYou(from: String) = "to you from $from"
    override fun promiseToYouParts(from: String) = Triple("to you from ", from, "")
    override fun promiseFromYouParts(to: String) = Triple("to ", to, " from you")
    override fun promiseFromYou(to: String) = "to $to from you"
    override fun forwardFrom(via: String) = "from $via"
    override fun forwardFiles(n: Int) = "📎 $n"
    override fun tgOn(bot: String) = "@$bot — forward messages to it"
    override fun mailOn(a: String) = "Forward letters to $a"
    override fun tgConnectedText(bot: String) = "@$bot is connected. Forward messages to it: Margin sorts them and replies with what it made — at once while Margin is open, otherwise within 15 minutes."
    override fun mailConnectedText(a: String) = "Forward letters to $a — Margin picks them up: every two minutes while it’s open, otherwise within 15 minutes."
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
    override fun waitingFor(who: String) = "Promise: to you from $who"
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
    override val people = "CONTACTS"
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
    override fun nextDue(d: String) = "next: $d"
    override val archivedOne = "Archived"
    override val restoredOne = "Restored"
    override val deletedOne = "Deleted"
    override val toArchive = "Archive"
    override val archiveTitle = "Archive"
    override val archiveEmpty = "Settled topics come here with their whole feed and materials. News in a topic or «Bring back» returns it."
    override val restoreWord = "Restore"
    override val calendarTitle = "Calendar"
    override val projectsEmpty = "No projects yet"
    override val projectsHint = "A project appears by itself when you write about it: “buy a domain for vuzburo”, “an idea for the vuzburo site”."
    override fun projectStats(tasks: Int, notes: Int) = listOfNotNull(tasksCount(tasks).takeIf { tasks > 0 }, notes.takeIf { it > 0 }?.let { "$it " + (if (it == 1) "note" else "notes") }).joinToString(" · ").ifBlank { "empty" }
    override val postponeTitle = "Postpone"
    override val inAWeek = "In a week"
    override val inAMonth = "In a month"
    override val pickDay = "Pick a day…"
    override fun fromMessage(stamp: String) = "from a message, $stamp"
    override val saveToPhone = "Save to phone"
    override val savedToPhone = "Saved to the Margin folder"
    override val saveFailed = "Couldn’t save — try “Share”"
    override val shareWord = "Share"
    override val closeWord2 = "Close"
    override val contactInNote = "contact card in the note"
    override fun noSubtask(n: Int) = "There is no subtask $n"
    override fun dayPart(p: String) = when (p) { "morning" -> "in the morning"; "afternoon" -> "in the afternoon"; "evening" -> "in the evening"; "night" -> "at night"; else -> "during the day" }
    override val insertBelow = "Insert below"
    override val captionWord = "Caption"
    override val moveUp = "Move up"
    override val moveDown = "Move down"
    override val readingPhoto = "Reading the text on the photo…"
    override val photoTextAction = "Recognize"
    override val insertAsText = "Insert as text"
    override val makeContact = "Create a contact"
    override val makeTasks = "Make tasks"
    override val noTextOnPhoto = "No text found on the photo"
    override fun contactMade(name: String) = "Added to Contacts: $name"
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
    override val inWorkThisWeek = "This week"
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
    override val inboxHint = "what isn’t sorted into matters and topics yet"
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
    override val peopleTitle = "Contacts"
    override val emptyPeople = "Contacts appear by themselves once you mention a person or a company in tasks or notes."
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
    override val searchHint = "Tasks, notes, contacts…"
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
    override val fieldPeople = "Contacts"
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
    override fun promised(who: String) = "Promise: to $who from you"
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
    override val ob3Text = "The mic transcribes with AI, “+” attaches photos, files and contacts. Thoughts go to notes, people and companies to Contacts."
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
    // v2 topics
    override val solvedWord = "Settled"
    override val gotIt = "Received"
    override fun archivedTopic(t: String) = "“$t” archived"
    override val feedTab = "Feed"
    override val questionsTab = "Open items"
    override val materialsTab = "Materials"
    override fun topicSince(d: String) = "topic since $d"
    override val questionPrefix = "Open item:"
    override val solvedPrefix = "Settled:"
    override fun waitPrefix(who: String) = "Waiting for $who:"
    override fun gotPrefix(who: String) = "Received from $who:"
    override val becameTopic = "The task became a topic"
    override val revivedTopic = "The topic is back from the archive"
    override fun solvedOn(d: String) = "settled $d"
    override fun gotOn(d: String) = "received $d"
    override val allSettled = "Looks like it's all settled."
    override val archiveQ = "Is the topic settled?"
    override fun archiveText(t: String) = "“$t” moves to the archive with its materials. Any news in it brings it back."
    override fun inArchiveSince(d: String) = "Archived $d"
    override val bringBack = "Bring back"
    override val openCaps = "Open"
    override val waitingCaps = "Waiting"
    override val solvedCaps = "Settled"
    override fun inFeed(d: String) = "in the feed, $d ›"
    override val noQuestions = "No open items. Write below to add one."
    override val newQuestionHint = "New item…"
    override val writeInTopic = "Write to the topic…"
    override val materialsEmpty = "No materials yet. Photos, files and voice notes from the feed appear here."
    override val searchMaterials = "Search materials and text on photos"
    override val kindPhotos = "Photos"
    override val kindDocs = "Documents"
    override val kindVoice = "Voice"
    override val noMaterialsFound = "Nothing found."
    override val topicsInWork = "Topics"
    override val simpleTasks = "To-dos"
    override val thoughts = "Ideas"
    override val noNextStepCaps = "No next step"
    override fun nQuestions(n: Int) = if (n == 1) "1 open item" else "$n open items"
    override fun waitUntil(d: String) = "waiting till $d"
    override val allDoneShort = "all settled"
    override fun nMaterials(n: Int) = if (n == 1) "1 file" else "$n files"
    override fun nEntries(n: Int) = if (n == 1) "1 entry" else "$n entries"
    override val thoughtWord = "idea"
    override val whatNext = "What's next?"
    override val labelWord = "Label"
    override val casesTitle = "To-dos"
    override val materialsTitle = "Materials"
    override val materialsSub = "all photos, files and voice notes from topics"
    override val noLabel = "No label"
    override val noDateQ = "no date"
    override val kindTopic = "to the topic"
    override val kindThought = "idea"
    override fun lateSince(d: String) = "since $d"
    override fun doneCountWord(n: Int) = "$n done"
    override val freeWord = "free"
    override val dealsBadge = "To-dos"
    override val topicsBadge = "Topics"
    override fun buildLabel(name: String, code: Int, debug: Boolean) = "Margin 2 · build $name ($code)" + if (debug) " · debug" else ""
    override val backWord = "Back"
    override val todayShort = "today"
    override fun progressOf(d: Int, n: Int) = "$d of $n"
    override fun progressSolved(d: Int, n: Int) = "$d of $n settled"
    override fun movedToTopic(t: String) = "Moved to “$t”"
    override val toOtherTopic = "Another topic"
    override val toTopicPick = "To a topic…"
    override val recognizeWord = "Read text"
    override val feedTitle = "Feed"
    override val filterWord = "Show"
    override val filterAll = "Everything"
    override val spherePersonal = "Personal"
    override val sphereWork = "Work"
    override val sphereAdd = "personal or work?"
    override val orgsBadge = "Businesses"
    override val peopleBadge = "People"
    override val markOrg = "It’s a business"
    override val markPerson = "It’s a person"
    override val stepOne = "question"
    override val stepHint = "with questions a matter becomes a topic"
    override val peopleSub = "people from your matters and topics"
    override val archiveSub = "settled topics, with all their materials"
    override val sinceWord = "since"
    override val sortsWhenOnline = "will be sorted once you’re online"
    override val unplacedBadge = "Unplaced"
    override val photoNoCaption = "Photo, no caption"
    override fun thoughtSince(d: String) = "idea since $d"
    override val thoughtsToday = "Ideas today"
    override val questionDateQ = "Item date"
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
