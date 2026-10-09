package ru.anjeib.margin.i18n

import ru.anjeib.margin.data.dative
import ru.anjeib.margin.data.genitive

import ru.anjeib.margin.data.pluralRu
import ru.anjeib.margin.data.shortLabel

enum class Lang { Ru, En }

/** All user-facing text. One instance per language; the UI never mixes them. */
/** One part of «Помощь»: a line of explanation, examples to say, and «what → what it does» pairs. */
data class HelpSection(val title: String, val text: String, val examples: List<String> = emptyList(), val pairs: List<Pair<String, String>> = emptyList(),
                       /** A screenshot from assets/help (without «.webp»), shown under the words; a tap opens it full size. */
                       val image: String? = null,
                       /** «icons» / «badges» — a legend drawn with the app's own marks, so it always matches what is on screen. */
                       val legend: String? = null)

interface Strings {
    // help & tour
    val helpTitle: String
    val helpIntro: String
    val helpSections: List<HelpSection>
    /** «Помощь» opens on these groups (a title → the section titles in it); sections not named fall into the last one. */
    val helpGroups: List<Pair<String, List<String>>>
    val helpSearch: String
    val tourWriteTitle: String
    val tourWriteText: String
    val tourWriteExample: String
    val tourGesturesTitle: String
    val tourGesturesText: String
    val tourSwipeDone: String
    val tourSwipeLater: String
    val tourMarks: List<String>
    val tourMarksText: String
    val tourTopicTitle: String
    val tourTopicText: String
    val tourTopicName: String
    val tourTopicDeals: List<String>
    val tourTopicWho: String
    val tourHold: String
    val tourOpened: String
    fun pageOf(n: Int, of: Int): String
    val tourUndo: String
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
    val samplesLine: String
    val ideaBadge: String
    val openTopic: String
    fun aboutDeal(title: String, text: String): String
    val ideaHint: String
    val openWhole: String
    val ideasBadge: String
    val samplesRemove: String
    val samplesBack: String
    val samplesTitle: String
    val askOffline: String
    val errInvite: String
    val inviteTitle: String
    val inviteHint: String
    val inviteNone: String
    val inviteAlpha: String
    val privacyPolicy: String
    val forwardSorting: String
    val forwardResult: String
    val forwardOpen: String
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
    val storeRestoredTitle: String
    val storeRestoredText: String
    val storeLostTitle: String
    val storeLostText: String
    val storeNewerTitle: String
    val storeNewerText: String
    val tryExamples: List<String>
    val tryHint: String
    val toStart: String
    val orderAz: String
    val orderNew: String
    val frequentBadge: String
    val allBadge: String
    val dateWord: String
    val toTomorrow: String
    val privacySection: String
    val lockTitle: String
    val lockHint: String
    val lockOpen: String
    val whereDataTitle: String
    val whereDataText: String
    val widgetFree: String
    val moreWord: String
    val exportDone: String
    val exportWaiting: String
    val exportOpen: String
    val exportNotes: String
    val weekTitle: String
    fun weekLine(done: Int, owed: Int, owe: Int): String
    val weekStill: String
    val remindLink: String
    fun remindText(name: String, what: String, date: String?): String
    fun likelyTopic(title: String): String
    val misparsed: String
    val misparsedThanks: String
    val sendFeedback: String
    fun feedbackSubject(version: String): String
    fun feedbackBody(version: String, device: String, files: Int): String
    val feedbackNoMail: String
    val crashTitle: String
    val crashText: String
    val crashSend: String
    val crashLater: String
    val searchRecent: String
    val searchTry: List<String>
    val tileWrite: String
    val shortcutDictate: String
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
    fun inboxLine(n: Int): String
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
    val fieldSocial: String
    val socialHint: String
    val personalTitle: String
    val personalHint: String
    val orgAboutTitle: String
    val fieldOrgKind: String
    val orgAboutHint: String
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
    val tagWait: String
    val tagPromise: String
    val tagDone: String
    val tagRecord: String
    val hashWhat: String
    val atWhat: String
    fun doneMarked(t: String): String
    val doneNotFound: String
    fun intoTopic(t: String): String
    fun previewDone(t: String): String
    val previewRecord: String
    val recordBadge: String
    val openWord: String
    val readTextWord: String
    val searchWord: String
    fun nIdeas(n: Int): String
    val overviewWord: String
    val monthWord: String
    val dealsOnlyWord: String
    fun moreDeals(n: Int): String
    fun summaryDeals(n: Int): String
    fun summaryTopics(n: Int): String
    fun summaryMeetings(n: Int): String
    val parseSection: String
    val parseLocal: String
    val parseAuto: String
    val parseCloud: String
    val parseLocalHint: String
    val parseAutoHint: String
    val parseCloudHint: String
    val previewAi: String
    val demoSection: String
    val demoAskTitle: String
    val demoAskText: String
    val demoLoad: String
    val demoLater: String
    val demoRestore: String
    val demoRestoreHint: String
    val demoLoadHint: String
    val daysWord: String
    val addDay: String
    fun dayOfDays(i: Int, n: Int): String
    val tagDeal: String
    val tagTopic: String
    val tagIdea: String
    val tagWork: String
    val tagPersonal: String
    val removedWord: String
    val restoreWord2: String
    val purgeWord: String
    val underLine: String
    val dueWord: String
    val slashWhat: String
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
    override val helpIntro = "Всё о разделах, метках и жестах"
    override val helpSearch = "Поиск по справке…"
    override val helpGroups = listOf(
        "Как это устроено" to listOf("Как это устроено", "ИИ-помощник", "Понятия и связи", "Меню"),
        "Экраны" to listOf("Сегодня", "Календарь", "Обзор", "Тема", "Действия в теме", "Страница дела", "Входящие", "Материалы", "Архив"),
        "Ввод" to listOf("Поле ввода и метки", "Окошко тем", "Даты, время, повторы", "Списки", "Голос и файлы", "Быстрый ввод и отзыв", "Пересылка в Margin"),
        "Люди и обещания" to listOf("Люди и обещания", "Контакты", "Неделя, напоминания, виджет"),
        "Значки и жесты" to listOf("Значки", "Бейджи и цвета", "Жесты"),
        "Настройки и данные" to listOf("Настройки", "ИИ и без ИИ", "Приватность", "Примеры"),
    )
    override val helpSections = listOf(
        // ---- concepts ----
        HelpSection("Как это устроено", "Margin — ежедневник, в который пишут как в мессенджер. Одно поле внизу: пишете или диктуете — из слов получаются дела, темы и идеи. Обычные фразы Margin понимает сам, без интернета; со сложными помогает ИИ, если есть сеть (подробно — в разделе «ИИ и без ИИ»).",
            examples = listOf("Завтра в 15 созвон с Мариной", "Купить корм коту", "Идея: блог о дизайне")),
        HelpSection("ИИ-помощник", "Помощник ведёт ежедневник с ваших слов: разбирает фразы на дела, даты и обещания, переносит и закрывает дела, пополняет темы. На вопросы о делах отвечает коротко, в два-три предложения. Нужны интернет и код приглашения; обычные фразы Margin понимает и без помощника. Длинных текстов вроде договоров он не пишет — «подготовить договор» станет вашим делом.", pairs = listOf(
            "что горит на этой неделе?" to "просроченное, ближайшие даты, кто кому должен — с пояснением",
            "что я обещал Лёше?" to "ваши обещания этому человеку",
            "перенеси стоматолога на пятницу" to "переносит дело",
            "созвонились с Олегом, макет утвердили" to "закрывает дело и добавляет запись в тему",
            "разбей ремонт на шаги" to "создаёт тему с делами",
        )),
        HelpSection("Понятия и связи", "", pairs = listOf(
            "Дело" to "одно действие с датой или без: «Купить хлеб», «Стоматолог в пт в 11». Может быть на несколько дней и повторяться",
            "Тема" to "дело, которое разрослось: своя лента записей, файлов и дел. Дело становится темой само, как только в нём появляется запись, файл или ещё одно дело, — или по «+ дело» в карточке",
            "Идея" to "тема без дел — мысль, заметка, список. Когда в идее появляется дело, она становится темой",
            "Запись" to "ваш текст в ленте темы или идеи, с датой и временем. Дела из этой записи стоят под ней",
            "Дела темы" to "видны и в теме, и на «Сегодня» и в «Календаре» — в свои дни, со ссылкой на тему",
            "Обещание" to "дело, в котором кто-то должен: ← ждём от человека, → обещали вы",
            "Сфера" to "«Личное» или «Работа» — у дел и тем; фильтр в «Обзоре»",
            "Архив" to "решённая тема со всей лентой; новость в ней возвращает её",
        )),
        // ---- the screens, in order ----
        HelpSection("Меню", "Слева вверху — меню разделов. Внизу — Настройки, Помощь, отзыв и номер сборки.", image = "drawer", pairs = listOf(
            "Входящие" to "записанное без интернета и присланное без подписи; число — сколько ждёт разбора",
            "Ежедневник" to "главный экран: Сегодня · Календарь · Обзор",
            "Материалы" to "все фото, файлы и голосовые",
            "Контакты" to "люди и компании",
            "Архив" to "завершённые темы",
        )),
        HelpSection("Сегодня", "Что делать сейчас. Слева на полях — сегодняшнее число.", image = "today", pairs = listOf(
            "сводка" to "сколько сегодня дел и тем, а в синей карточке «Обещания» — кто кому что должен: просроченное, на ближайшие 3 дня и без даты",
            "дела дня" to "по времени; просроченное переносится сюда с пометкой «с 6 окт»",
            "≡ Тема ›" to "дела одной темы — блоком под её названием; нажатие на название — тема, на дело — его карточка. Под блоком мелко «17 авг ’26 · 5 записей · ещё 3 дела ▾» — раскрывает остальные дела темы",
            "Без даты" to "дела без даты с пометкой, с какого числа они ждут («с 3 окт»); старше недели — бледнее. Нажмите на заголовок, чтобы свернуть",
            "Завтра — N дел ›" to "внизу; открывает «Календарь»",
        )),
        HelpSection("Календарь", "Что когда. Месяц сеткой: число под днём — сколько в нём дел; сегодня обведено рамкой, выбранный день закрашен. Под сеткой — дела выбранного дня, как на «Сегодня». Стрелками листаются месяцы.", image = "calendar"),
        HelpSection("Обзор", "Что у вас есть. Три вкладки и фильтр «Личное / Работа» нажатие оставляет одну сферу, повторное возвращает обе.", image = "overview", pairs = listOf(
            "Темы" to "на полях — день начала; «N записей ▾» раскрывает записи; «N дел»; у решённых — «всё сделано · В архив»",
            "Дела" to "все открытые дела по дням и «Без даты»",
            "Идеи" to "на полях — день начала; «N записей ▾» раскрывает; ↳ — что из идеи выросло",
        )),
        HelpSection("Тема", "Всё, что было в теме, по дням. Дни — на полях, между ними тонкая линия.", image = "topic", pairs = listOf(
            "шапка" to "название (нажатие — переименовать), сфера (нажатие — переключить), «тема с 17 авг», скрепка с числом файлов",
            "сводка" to "«2 из 9 сделано · 5 записей · 3 материала» и полоска; нажатие — только дела, ещё раз — вся лента",
            "ЗАПИСЬ · 19:40" to "ваши слова; под ними — фото и файлы",
            "ДЕЛО / ДЕЛА · N" to "блок дел этого дня; вложенные — с отступом и тонкой линией от родительского",
            "скрепка" to "только материалы, с поиском и чипами",
            "⋯" to "Изменить, В архив; у архивной — Вернуть, Удалить",
        )),
        HelpSection("Действия в теме", "Долгое нажатие на запись или дело открывает маленькое меню. Удалённое остаётся в ленте зачёркнутым, с пометкой «удалено»; нажмите на него, чтобы вернуть или удалить насовсем.", image = "bubble", pairs = listOf(
            "Добавить дело" to "новое дело с отступом под этой строкой (поле ввода переходит в режим «Добавить дело»)",
            "Изменить" to "текст строки",
            "Дата" to "день дела",
            "Удалить" to "зачеркнуть; вернуть можно",
        )),
        HelpSection("Страница дела", "Открывается нажатием в любом месте строки — у любого дела, и простого, и из темы. Название можно поправить прямо в карточке. Комментарии пишите прямо на странице — у каждого на полях встанут дата и время. В поле внизу можно попросить словами: «перенеси на пятницу».", image = "deal", pairs = listOf(
            "кружок у названия" to "отметить сделанным; долгое нажатие — Завтра, +неделя, Не буду делать, В идеи, Удалить",
            "Добавить комментарий" to "свой текст к делу; у дела в списках появляется значок 💬",
            "Личное / Работа" to "сфера; нажатие переключает",
            "📅 Сегодня, 🕓 19:30" to "дата и время; нажмите, чтобы изменить",
            "+ день · + повтор · + контакты · + место" to "заполнить недостающее",
            "+ дело" to "добавить дело внутри — и дело станет темой",
            "Откуда, Записи, История" to "ниже: откуда пришло, что записано, что менялось",
        )),
        HelpSection("Поле ввода и метки", "Наберите «/», «#» или «@» — появится окошко с вариантами; выбранное появится плашкой над полем. Ещё до отправки под полем видно, как Margin понял фразу.", image = "composer_slash", pairs = listOf(
            "/дело  /тема  /идея" to "что это",
            "/работа  /личное" to "сфера",
            "#тема" to "в какую тему: слова — в её ленту, дела — в её дела; сфера — от темы",
            "@человек" to "кто: контакт или компания; нового можно добавить из окошка",
            "/жду @Маша" to "ждём от Маши: ←",
            "/обещаю @Лёша" to "вы обещали Лёше: →",
            "/готово слова" to "отметить сделанным дело с такими словами; после «:» — запись в его тему",
            "/запись" to "только в ленту, без дел",
        )),
        HelpSection("Окошко тем", "«#» показывает ваши темы, свежие сверху. Начните печатать название, чтобы найти нужную.", image = "composer_hash",
            examples = listOf("#ремонт купить затирку до пятницы", "/жду @Маша ключи до субботы", "/обещаю @Лёша исходники завтра", "/готово #зерно упаковку: Олег одобрил", "#квартира /запись хозяйка согласна на кошку", "позвонить @ЖЭК насчёт счётчиков")),
        HelpSection("Входящие", "Что ещё не разложено: записанное без интернета (Margin разберёт это, когда появится связь) и присланное без подписи. Нажмите на строку, чтобы открыть фото, файл или текст; смахните влево — «Убрать», вправо — «В тему…». В просмотре фото кнопка «Распознать» добавит под ним текст с фото — его можно поправить.", image = "inbox"),
        HelpSection("Материалы", "Все фото, файлы и голосовые по дням. Над файлами — ссылка на тему или дело. Лупа ищет и по тексту на фото и в голосовых; кнопки «Фото», «Документы», «Голосовые» оставляют один вид.", image = "materials"),
        HelpSection("Контакты", "Две вкладки: «Люди» и «Компании». Сверху — «Часто»: с кем больше всего дел за месяц; ниже — все, по алфавиту или сначала новые. В строке — кто это и что с ним открыто (ждём, обещали), справа — число дел. В карточке человека — телефон, почта, адрес, MAX, Telegram и соцсети: вставьте ссылку, сеть Margin узнает сам. Нажатие звонит, пишет или открывает, долгое — изменить. Там же «О человеке», дела и история. В ⋯ — переименовать, объединить, «Это компания».", image = "contacts"),
        HelpSection("Архив", "Завершённые темы со всей лентой и материалами. «Вернуть» снова делает тему рабочей; новая запись в ней — тоже.", image = "archive"),
        HelpSection("Настройки", "", image = "settings", pairs = listOf(
            "Голос" to "распознавать речь на телефоне (без интернета) или в облаке; отправлять сразу после диктовки",
            "Календарь" to "учитывать встречи из календаря телефона",
            "Оформление" to "светлая, тёмная или как в системе; язык",
            "Разбор текста" to "Телефон / Авто / ИИ — см. «ИИ и без ИИ»",
            "Пример данных" to "загрузить два месяца жизни дизайнера; «Вернуть мои данные»",
            "Резервная копия" to "в аккаунт Google",
        )),
        // ---- legends ----
        HelpSection("Значки", "Значок слева от кружка показывает, что это за дело.", legend = "icons"),
        HelpSection("Бейджи и цвета", "", legend = "badges"),
        // ---- writing ----
        HelpSection("Даты, время, повторы", "Пишите обычными словами — Margin поймёт и без ИИ.", pairs = listOf(
            "завтра в 15, в пятницу, 12 октября, 5.11, в 7 вечера" to "день и время",
            "через неделю, до конца месяца, к среде, до 10-го" to "дата",
            "утром, вечером" to "часть дня без точного часа",
            "в пн и чт в 19:00, 12, 15 и 18 октября" to "одно дело на несколько дней: у каждого дня своя галочка",
            "каждый вторник, по понедельникам и средам, первого числа каждый месяц" to "повтор",
        )),
        HelpSection("Люди и обещания", "Margin узнаёт имена в любом падеже — даже со строчной буквы, как при диктовке.", pairs = listOf(
            "Дима пришлёт макет в среду" to "← ждём от Димы",
            "обещал Анне показать калькулятор" to "→ вы обещали Анне",
            "@человек" to "если нужно наверняка",
        )),
        HelpSection("Списки", "Начните строку с «1.», «1)», «-» или «•» — Enter продолжит список, Enter на пустом пункте закончит. Список, отправленный с главного экрана, станет несколькими делами; в записи темы останется просто списком."),
        HelpSection("Жесты", "", pairs = listOf(
            "смахнуть дело вправо" to "сделано",
            "смахнуть дело влево" to "отложить: завтра, неделя, месяц, своя дата",
            "нажатие на дело" to "его карточка (в любом месте строки)",
            "долгое нажатие на дело" to "меню: Готово, Дата, Время",
            "долгое нажатие в теме" to "меню строки",
            "во «Входящих»: влево / вправо" to "Убрать / В тему…",
            "телефон, почта, ссылка в тексте" to "звонок, письмо или приложение по адресу (Telegram, MAX, Instagram…); «@ник» — Telegram",
            "«Отменить»" to "появляется на несколько секунд после любого изменения",
        )),
        HelpSection("Голос и файлы", "", pairs = listOf(
            "микрофон" to "диктовка — сразу текстом",
            "«+»" to "фото, камера, файл, контакт, голосовое",
            "«Голосовое: …» в начале диктовки" to "сохранить аудио с расшифровкой",
            "Поделиться → В Margin" to "из любого приложения; выделенный текст → «В Margin»",
        )),
        // ---- AI ----
        HelpSection("ИИ и без ИИ", "Настройки → «Разбор текста»: «Телефон» — Margin разбирает всё сам, без ИИ; «Авто» (по умолчанию) — сложное передаёт ИИ, когда есть сеть; «ИИ» — всё через ИИ, а без сети — на телефоне. Фразы с метками (#, @, /…) всегда разбираются на телефоне.", pairs = listOf(
            "всегда без ИИ" to "дела, темы, идеи, даты, время, повторы, несколько дней, люди, обещания, списки, все метки, отметки, перенос, поиск, все экраны",
            "ИИ в «Авто», если есть сеть" to "отчёты («созвонились…»), правки («перенеси…», «Исправить: …»), длинная диктовка без точек, несколько дел со своими датами в одной фразе, телефоны и почта",
            "только с ИИ" to "короткое название для новой темы; текст с фото (визитка → контакт, доска → дела); разбор пересланных сообщений (кто что обещал); исправление опечаток в исходной фразе",
            "голос" to "в облаке или на телефоне без сети — переключатель в «Голосе»",
        )),
        HelpSection("Пересылка в Margin", "Из Telegram, MAX, почты: «Поделиться» или выделить текст → «В Margin». Или «Копировать» — и в Margin появится «Вставить скопированное». Margin понимает, что это чужие слова: что обещают вам, станет «ждём», о чём просят вас — делом."),
        HelpSection("Быстрый ввод и отзыв", "Быстро записать можно с плитки «Записать» в шторке (добавьте её через карандаш в быстрых настройках) или долгим нажатием на значок приложения — «Записать» / «Надиктовать». Если Margin понял фразу не так, нажмите «Не совсем понял» под результатом. «Отправить отзыв» в меню открывает письмо разработчику с журналами ошибок; ваши записи не отправляются."),
        HelpSection("Неделя, напоминания, виджет", "По воскресеньям на «Сегодня» появляется итог недели: сколько сделано, кто кому должен, какие темы стоят на месте; «×» скрывает его до следующего воскресенья. Если кто-то просрочил обещание, рядом появится «напомнить»: Margin подготовит сообщение (SMS, если известен телефон, иначе через «Поделиться»), а отправите его вы. Виджет «Margin · Сегодня» на рабочем столе показывает дела дня и открывает поле ввода или диктовку. Тему целиком можно переслать текстом: ⋯ → «Переслать»."),
        HelpSection("Приватность", "Настройки → «Приватность»: вход по отпечатку (или коду блокировки) и «Где ваши данные» — где хранятся записи и что уходит с телефона. На экране блокировки напоминания показываются без текста дел."),
        HelpSection("Примеры", "При первом запуске в Margin уже есть примеры — несколько дней молодого дизайнера, по одному на каждую возможность: тема с записями и файлами, дела со временем, повтором и на несколько дней, просроченное, обещания в обе стороны, дело без даты, идея, контакт и компания. Убрать их можно в Настройках → «Примеры», там же их можно вернуть. Ваши записи останутся на месте."),
    )
    override val tourWriteTitle = "Пишите как думаете"
    override val tourWriteText = "Одно поле внизу — пишите или диктуйте. Margin сам разложит всё на дела, даты и обещания, даже без интернета."
    override val tourWriteExample = "Завтра в 15 созвон с Мариной, она пришлёт правки до среды"
    override val tourMarks = listOf("#тема", "@человек", "/жду", "/обещаю", "/идея")
    override val tourMarksText = "Метки — когда нужна точность:"
    override val tourTopicTitle = "Дело становится темой"
    override val tourTopicText = "Появилась запись, файл или второе дело — начинается тема."
    override val tourTopicName = "Ремонт ванной"
    override val tourTopicDeals = listOf("Купить плитку", "Смета от мастера")
    override val tourTopicWho = "Сергей"
    override val tourGesturesTitle = "Нажмите на дело"
    override val tourGesturesText = "Откроется его карточка."
    override val tourSwipeDone = "→ вправо — сделано"
    override val tourSwipeLater = "← влево — отложить"
    override val tourHold = "удержать — меню"
    override val tourOpened = "В приложении откроется карточка этого дела"
    override fun pageOf(n: Int, of: Int) = "$n из $of"
    override val tourUndo = "Любое изменение можно отменить. Если Margin понял не так — нажмите «Не совсем понял»."
    override val tourMore = "Подробнее — в меню, раздел «Помощь»."
    override val tourDemoCall = "Созвон с Мариной"
    override val tourDemoEdits = "Получить правки от Марины"
    override val tourDemoName = "Марина"
    override val aiUndoTitle = "Отмены после ИИ"
    override val inTaskWord = "В деле"
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
    override val tgWelcome = "Готово, это ваш бот Margin. Пересылайте сюда сообщения — из чатов, каналов, от кого угодно. Margin разложит их на дела, обещания и идеи и ответит, что получилось. Можно и просто написать, что нужно сделать."
    override val tgNotYours = "Это личный бот, он принимает сообщения только от владельца."
    override val mailBadLogin = "Не удалось войти в почту: проверьте адрес и пароль приложения (обычный пароль не подойдёт)"
    override val forwardTitle = "В Margin"
    override val forwardCommentHint = "Что с этим сделать — или просто отправьте"
    override val forwardHint = "Margin найдёт дела, обещания, идеи"
    override val samplesLine = "Здесь примеры"
    override val ideaBadge = "Идея"
    override val openTopic = "Открыть тему"
    override fun aboutDeal(title: String, text: String) = "Про дело «$title»: $text"
    override val ideaHint = "Текст идеи"
    override val openWhole = "Открыть целиком"
    override val ideasBadge = "Идеи"
    override val samplesRemove = "Убрать примеры"
    override val samplesBack = "Вернуть примеры"
    override val samplesTitle = "Примеры"
    override val askOffline = "Чтобы помощник отвечал, нужны интернет и код приглашения."
    override val errInvite = "Код приглашения не подошёл — проверьте его в Настройках"
    override val inviteTitle = "Код приглашения"
    override val inviteHint = "Включает ИИ: сложные фразы, голос, текст с фото. Без кода Margin разбирает всё на телефоне."
    override val inviteNone = "не введён"
    override val inviteAlpha = "альфа-доступ"
    override val privacyPolicy = "Политика конфиденциальности"
    override val forwardSorting = "Разбираю…"
    override val forwardResult = "Вот что получилось"
    override val forwardOpen = "Открыть в Margin"
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
    override fun waitingShort(n: String) = "обещание: вам от ${n.genitive()}"
    override fun promisedShort(n: String) = "обещание: ${n.dative()} от вас"
    override val promiseWord = "Обещание"
    override val feedbackTitle = "Пожаловаться или похвалить"
    override val storeRestoredTitle = "Последнее изменение могло не сохраниться"
    override val storeRestoredText = "Файл с записями оказался повреждён, поэтому открыта предыдущая копия. Повреждённый файл сохранён внутри приложения — ничего не удалено."
    override val storeLostTitle = "Не удалось прочитать записи"
    override val storeLostText = "Файл с записями и его копия повреждены. Оба сохранены внутри приложения — ничего не удалено. Напишите разработчику: записи можно будет вернуть."
    override val storeNewerTitle = "Записи из более новой версии"
    override val storeNewerText = "Эти записи сделаны в более новой версии Margin. Их копия сохранена — обновите приложение, чтобы ничего не потерять."
    override val tryExamples = listOf("Завтра в 15 созвон с Олегом", "Маша вернёт 3000 к пятнице", "Идея: подкаст про дизайн")
    override val tryHint = "Нажмите на пример — он появится в поле"
    override val toStart = "↑ к началу"
    override val orderAz = "А–Я"
    override val orderNew = "Новые"
    override val frequentBadge = "Часто"
    override val allBadge = "Все"
    override val dateWord = "Дата"
    override val toTomorrow = "Перенести на завтра"
    override val privacySection = "Приватность"
    override val lockTitle = "Вход по отпечатку"
    override val lockHint = "При открытии Margin спросит отпечаток или код блокировки"
    override val lockOpen = "Открыть"
    override val whereDataTitle = "Где ваши данные"
    override val whereDataText = "Записи хранятся на этом телефоне — в памяти приложения, других копий у Margin нет.\n\n" +
        "Резервная копия: если включена, раз в день уходит в ваш аккаунт Google (Google Диск) и доступна только вам.\n\n" +
        "Фразы: простые Margin разбирает на телефоне, они никуда не уходят. Сложные, когда есть интернет, отправляются в Yandex AI Studio (серверы в России) вместе с нужной частью записей: связанными делами и именами. Яндекс эти запросы не сохраняет.\n\n" +
        "Голос: на телефоне (Whisper) или в Yandex SpeechKit.\n\n" +
        "Журналы ошибок уходят разработчику только письмом, которое вы отправляете сами; ваших записей в них нет.\n\n" +
        "На экране блокировки напоминания показываются без текста дел."
    override val widgetFree = "На сегодня дел нет"
    override val moreWord = "ещё"
    override val exportDone = "Сделано:"
    override val exportWaiting = "Ждём:"
    override val exportOpen = "Осталось:"
    override val exportNotes = "Последние записи:"
    override val weekTitle = "Неделя"
    override fun weekLine(done: Int, owed: Int, owe: Int) = "Сделано: $done  ·  ждём: $owed  ·  обещали вы: $owe"
    override val weekStill = "Неделю без движения:"
    override val remindLink = "напомнить"
    override fun remindText(name: String, what: String, date: String?) = "Привет, $name. Напоминаю про $what" + (date?.let { " — договаривались на $it" } ?: "") + "."
    override fun likelyTopic(title: String) = "к теме «${title.take(28)}»?"
    override val misparsed = "Не совсем понял"
    override val misparsedThanks = "Спасибо, фраза уйдёт разработчику с отзывом"
    override val sendFeedback = "Отправить отзыв"
    override fun feedbackSubject(version: String) = "Margin · отзыв · $version"
    override fun feedbackBody(version: String, device: String, files: Int) =
        "Что случилось или чего не хватает:\n\n\n—\nMargin $version · $device" + if (files > 0) "\nПриложены журналы ошибок и неверного разбора — без ваших записей." else ""
    override val feedbackNoMail = "Не нашлось почтового приложения"
    override val crashTitle = "Margin закрылся с ошибкой"
    override val crashText = "Отправить разработчику описание ошибки? Ваши записи не отправляются."
    override val crashSend = "Отправить"
    override val crashLater = "Не сейчас"
    override val searchRecent = "Недавние"
    override val searchTry = listOf("люди", "темы", "файлы")
    override val tileWrite = "Записать"
    override val shortcutDictate = "Надиктовать"
    override val feedbackStub = "Скоро здесь можно будет написать нам напрямую — что мешает, чего не хватает, что нравится. Спасибо, что пользуетесь Margin."
    override val forwardHowTo = "Из Telegram, MAX, почты: «Поделиться» или выделить текст → «В Margin». Или «Копировать» — и в Margin появится «Вставить скопированное»."
    override val allDay = "весь день"
    override val addContact = "+ контакт"
    override fun notAContact(n: String) = "$n — нет в контактах"
    override val inContacts = "в контактах"
    override val promisesTitle = "Обещания"
    override fun promiseToYou(from: String) = "Вам от ${from.genitive()}"
    override fun promiseToYouParts(from: String) = Triple("Вам от ", from.genitive(), "")
    override fun promiseFromYouParts(to: String) = Triple("", to.dative(), " от вас")
    override fun promiseFromYou(to: String) = "${to.dative()} от вас"
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
    override val notes = "Идеи"
    override val settings = "Настройки"
    override fun fromDate(d: String) = "с ${d.lowercase()}"
    override val thisWeekSoft = "Эта неделя"
    override val nextWeekSoft = "След. неделя"
    override val later = "Позже"
    override val waiting = "От кого ждём?"
    override fun waitingFor(who: String) = "Обещание: вам от ${who.genitive()}"
    override val create = "Создать"
    override fun stepsCount(n: Int) = "$n " + pluralRu(n, "дело", "дела", "дел")
    override val chipEvening = "Итоги дня"
    override val promptEvening = "Сделай короткий вечерний обзор: что сделано сегодня, что не успел. Предложи, что перенести на завтра, и спроси про задачи, которые переносились 3 раза и больше, — оставить или удалить. Если на завтра задач больше, чем реально успеть, скажи об этом."
    override val notesCreated = "Сохранено в идеи"
    override fun tasksCount(n: Int) = "$n " + pluralRu(n, "дело", "дела", "дел")

    override val smart = "СПИСКИ"
    override val inbox = "Без даты"
    override val overdue = "Просрочено"
    override val completed = "Сделано"
    override val projects = "ПРОЕКТЫ"
    override val people = "КОНТАКТЫ"
    override val allTasks = "Все дела"

    override val composerHint = "Дело, запись или идея…"
    override val noteHint = "Новая идея…"
    override val chipNext = "Что дальше?"
    override val chipPlanDay = "План на день"
    override val thinking = "Думаю…"
    override val voiceUnavailable = "Голосовой ввод недоступен на этом телефоне"

    override val taskCreated = "Создано дело"
    override fun tasksCreated(n: Int) = "Создано " + n + " " + pluralRu(n, "дело", "дела", "дел")
    override val taskUpdated = "Дело обновлено"
    override fun subtasksAdded(n: Int) = "Добавлено $n " + pluralRu(n, "дело", "дела", "дел")
    override val noteCreated = "Создана идея"
    override val edit = "Изменить"
    override val undo = "Отменить"
    override val undone = "Отменено"
    override val remindFriday = "Напомнить в пятницу"
    override val dismiss = "Скрыть"
    override val noKey = "Для ИИ нужен код приглашения — введите его в Настройках. Обычные фразы Margin понимает и без него."
    override val openSettings = "Открыть настройки"
    override fun movedOverdue(n: Int) = "Перенесено на сегодня: $n"
    override val nothingOverdue = "Просроченных дел нет"

    override val emptyToday = "На сегодня ничего"
    override val emptyWeek = "Неделя свободна"
    override val emptyAll = "Дел пока нет"
    override val emptyHint = "Напишите или скажите, что нужно сделать. Например: «Завтра встреча с Мариной в 15» или «Посмотреть „Одиссею“ Нолана»."
    override val noTime = "Без времени"
    override val done = "Сделано"
    override val doneLower = "выполнено"
    override val swipeTomorrow = "Завтра"
    override val swipeNextWeek = "+1 неделя"
    override val markDone = "Готово"

    override val morningBrief = "Главное сегодня"
    override val eveningBrief = "На завтра"
    override val nothingToday = "На сегодня дел нет"
    override fun doneNext(date: String) = "Сделано · следующий раз $date"
    override fun undatedCount(n: Int) = "Ещё без даты: " + tasksCount(n)
    override val subtaskOne = "Подзадача"
    override val freeDay = "Свободный день"
    override fun nextDue(d: String) = "ближайшее: $d"
    override val archivedOne = "Убрано в архив"
    override val restoredOne = "Возвращено"
    override val deletedOne = "Удалено"
    override val toArchive = "В архив"
    override val archiveTitle = "Архив"
    override val archiveEmpty = "Сюда попадают завершённые темы — со всей лентой и материалами. Тема вернётся, если нажать «Вернуть» или добавить в неё запись."
    override val restoreWord = "Вернуть"
    override val calendarTitle = "Календарь"
    override val projectsEmpty = "Проектов пока нет"
    override val projectsHint = "Проект появляется сам, когда вы пишете о нём: «для vuzburo купить домен», «идея по сайту vuzburo»."
    override fun projectStats(tasks: Int, notes: Int) = listOfNotNull(tasksCount(tasks).takeIf { tasks > 0 }, notes.takeIf { it > 0 }?.let { "$it " + pluralRu(it, "заметка", "заметки", "заметок") }).joinToString(" · ").ifBlank { "пусто" }
    override val postponeTitle = "Отложить"
    override val inAWeek = "Через неделю"
    override val inAMonth = "Через месяц"
    override val pickDay = "Своя дата…"
    override fun fromMessage(stamp: String) = "из сообщения $stamp"
    override val saveToPhone = "Сохранить на телефон"
    override val savedToPhone = "Сохранено в папку Margin"
    override val saveFailed = "Не получилось сохранить — попробуйте «Переслать»"
    override val shareWord = "Переслать"
    override val closeWord2 = "Закрыть"
    override val contactInNote = "контакт в идее"
    override fun noSubtask(n: Int) = "Нет дела под номером $n"
    override fun dayPart(p: String) = when (p) { "morning" -> "утром"; "afternoon" -> "после обеда"; "evening" -> "вечером"; "night" -> "ночью"; else -> "днём" }
    override val insertBelow = "Вставить ниже"
    override val captionWord = "Подпись"
    override val moveUp = "Выше"
    override val moveDown = "Ниже"
    override val readingPhoto = "Читаю текст на фото…"
    override val photoTextAction = "Распознать"
    override val insertAsText = "Вставить текстом"
    override val makeContact = "Создать контакт"
    override val makeTasks = "Разложить на дела"
    override val noTextOnPhoto = "На фото не нашлось текста"
    override fun contactMade(name: String) = "В «Контактах»: $name"
    override val textWord = "Текст"
    override val photoWord = "Фото"
    override val textAfter = "Текст после блока"
    override val moveToToday = "Перенести на сегодня"
    override val skipOnce = "Пропустить один раз"
    override val droppedDone = "Решили не делать"
    override val madeNote = "Теперь это идея"
    override val toNote = "В идеи"
    override val wontDo = "Не буду делать"
    override val droppedMark = "не делаем"
    override val historyWord = "История"
    override fun change(kind: String, value: String?, by: String): String {
        val what = when (kind) {
            "created" -> when (value) { "chat" -> "создано из разговора"; "idea" -> "создано из идеи"; "repeat" -> "повтор"; else -> "создано" }
            "date" -> if (value == null) "дата убрана" else "дата: " + (runCatching { java.time.LocalDate.parse(value).shortLabel().lowercase() }.getOrNull() ?: value)
            "time" -> if (value == null) "время убрано" else "время: $value"
            "repeat" -> if (value == null) "повтор убран" else "повтор изменён"
            "waiting" -> if (value == null) "больше не ждём" else "ждём: $value"
            "promised" -> if (value == null) "обещание снято" else "вы обещали: $value"
            "place" -> if (value == null) "место убрано" else "место: $value"
            "done" -> "сделано"
            "dropped" -> "решили не делать"
            "reopened" -> "снова открыто"
            else -> kind
        }
        return what + when (by) { "ai" -> " · ИИ"; "swipe" -> " · жестом"; else -> "" }
    }
    override val listBlock = "Список"
    override val listItem = "Пункт списка"
    override val remindWord = "Напомнить"
    override val removeWord = "Убрать"
    override fun fillHint(what: String) = "Напишите или скажите"
    override val freeDayHint = "На сегодня ничего не запланировано. Можно отдохнуть — или взять что-то из «Недели»."
    override val askTime = "Уточнить время?"
    override val askWhen = "Уточнить дату?"
    override fun ideaBecameTask(title: String) = "Теперь это дело: $title"
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
    override val commentHint = "Комментарий"
    override val addSubtaskHint = "Добавить подзадачу…"
    override val dates = "Даты"
    override val noDates = "Без даты"
    override val start = "Начало"
    override val due = "Дата"
    override val time = "Время"
    override val reminder = "Напоминание"
    override val noReminder = "Нет"
    override val project = "Проект"
    override val noProject = "Без проекта"
    override val responsible = "Контакты"
    override val nobody = "Не указаны"
    override val delete = "Удалить"
    override val deleteTaskQ = "Удалить дело?"
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

    override val searchNotes = "Поиск по идеям…"
    override val newNote = "Новая идея"
    override val noteTitleHint = "Название"
    override val noteBodyHint = "Пишите свободно…"
    override val emptyNotes = "Идей пока нет"
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
    override val fastModelHint = "превращает ваш текст в дела"
    override val spentMonth = "Расход за месяц"
    override val appearance = "ОФОРМЛЕНИЕ"
    override val themeLight = "Светлая"
    override val themeDark = "Тёмная"
    override val themeAuto = "Как в системе"
    override val language = "Язык"
    override val langAuto = "Как в системе"
    override val data = "ДАННЫЕ"
    override val searchModels = "Поиск модели…"
    override val loading = "Загрузка…"
    override val perMillion = "за 1M токенов"


    override fun until(day: String) = "до $day"
    override val inWorkThisWeek = "На неделе"
    override val thisMonth = "Этот месяц"
    override val waitingOthers = "Ждём от других"
    override val byDates = "по датам"
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
    override val inboxEmptyHint = "Сюда попадает то, что записано без интернета (Margin разберёт это, когда появится связь), и то, что прислано через «Поделиться → В Margin»."
    override val savedUnsorted = "СОХРАНЕНО БЕЗ РАЗБОРА"
    override fun queuedLine(n: Int) = "Ждут интернета: " + n + " " + pluralRu(n, "запись", "записи", "записей")
    override fun inboxLine(n: Int) = "Во «Входящих» ждут: " + n
    override val nothingFound = "Ничего не нашлось"
    override val entryAdded = "Добавлена запись"
    override val addOrChange = "Что изменить?"
    override fun more(n: Int) = "ещё $n"
    override fun doneCount(n: Int) = "Сделано · $n"
    override val records = "Записи"
    override fun allRecords(n: Int) = "Все записи ($n)"
    override val attachPhoto = "Фото и видео"
    override val attachCamera = "Камера"
    override val attachFile = "Файл"
    override val attachContact = "Контакт"
    override val transcribing = "Распознаю…"
    override val micPermission = "Нужен доступ к микрофону"
    override val voiceNote = "Голосовое"
    override val video = "Видео"
    override fun nPhotos(n: Int) = "$n фото"
    override fun nFiles(n: Int) = "$n " + pluralRu(n, "файл", "файла", "файлов")
    override fun nVoice(n: Int) = "$n " + pluralRu(n, "голосовое", "голосовых", "голосовых")
    override fun nContacts(n: Int) = "$n " + pluralRu(n, "контакт", "контакта", "контактов")
    override val notesHint = "Мысль, идея, итоги встречи…"
    override val appendHint = "Дописать мысль…"
    override val allFilter = "Все"
    override fun entriesCount(n: Int) = "$n " + pluralRu(n, "запись", "записи", "записей")
    override fun updated(d: String) = "обновлено $d"
    override val peopleTitle = "Контакты"
    override val emptyPeople = "Контакты появятся сами, когда вы упомянете человека или компанию в делах или темах."
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
    override val offlineSaved = "Нет интернета. Всё сохранено во «Входящие» — Margin разберёт, когда появится сеть."
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
    override val overdueWaiting = "Просрочено"
    override val search = "Поиск"
    override val searchHint = "Дела, темы, идеи, люди…"
    override val nothing = "Ничего не найдено"
    override val calendarSection = "КАЛЕНДАРЬ"
    override val calendarRead = "Учитывать календарь телефона"
    override val calendarHint = "ИИ видит встречи и не ставит дела поверх них"
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
    override val googleBackupHint = "Раз в день дела, темы, идеи и настройки сохраняются в ваш аккаунт Google (Google Диск) и вернутся на новом телефоне."
    override val googleBackup = "Копия в аккаунте Google"
    override val googleBackupOff = "Выключена: если потеряете телефон, записи не восстановить."
    override val skip = "Пропустить"
    override val next = "Дальше"
    override val begin = "Начать"
    override val kindTask = "дело"
    override val freshSummary = "СВЕЖАЯ СВОДКА"
    override val weekSummary = "СВОДКА ПРОЕКТА"
    override val toTaskPill = "В дело"
    override val ideasOpen = "ИДЕИ БЕЗ ДЕЛ"
    override fun waitLine(what: String) = "ждём: $what"
    override fun promiseLine(what: String) = "вы обещали: $what"
    override val ideaMark = "из идеи"
    override val linksTitle = "ОТКУДА"
    override val linkIdea = "Идея"
    override val inTasks = "В делах"
    override val fieldDue = "Дата"
    override val fieldTime = "Время"
    override val fieldRepeat = "Повтор"
    override val fieldPeople = "Контакты"
    override val fieldPlace = "Место"
    override val fieldRole = "Должность"
    override val fieldCompany = "Компания"
    override val fieldPhone = "Телефон"
    override val fieldEmail = "Почта"
    override val fieldAddress = "Адрес"
    override val fieldSocial = "Соцсеть"
    override val socialHint = "Ссылка на страницу"
    override val personalTitle = "О ЧЕЛОВЕКЕ"
    override val personalHint = "день рождения, семья, что любит"
    override val orgAboutTitle = "О КОМПАНИИ"
    override val fieldOrgKind = "Чем занимается"
    override val orgAboutHint = "сайт, часы работы, реквизиты"
    override val copied = "Скопировано"
    override val placeHint = "Адрес или название, например «Ленина 5»"
    override val historyTitle = "ИСТОРИЯ"
    override val srcTaskEntry = "из записи дела"
    override val srcChat = "из разговора"
    override val srcNote = "идея"
    override val noDue = "без даты"
    override val tasksCaps = "ДЕЛА"
    override val makeSummary = "Собрать сводку"
    override fun fromNote(title: String, date: String) = "из идеи «$title» · $date"
    override fun fromChat(date: String) = "из разговора · $date"
    override fun promised(who: String) = "Обещание: ${who.dative()} от вас"
    override fun summaryAt(t: String) = "обновлено $t"
    override val reopened = "Отметка снята"
    override val markedDone = "Сделано"
    override val allDoneToday = "Главное на сегодня сделано"
    override val quickToday = "Сегодня"
    override val quickTomorrow = "Завтра"
    override val quickNextWeek = "След. неделя"
    override val quickNone = "Без даты"
    override val rangeLink = "Период…"
    override val singleLink = "Одна дата"
    override val savedTick = "Сохранено"
    override val toSeparateNote = "в отдельную идею"
    override val devSection = "ДЛЯ РАЗРАБОТЧИКА"
    override val devUnlocked = "Раздел для разработчика открыт"
    override val onDevice = "Распознавать речь на телефоне"
    override val onDeviceHint = "Голос не уходит в интернет."
    override val onDeviceModel = "Модель на телефоне"
    override fun movedTo(d: String) = "Перенесено на $d"
    override fun briefAt(t: String) = "обновлено $t"
    override fun version(v: String) = "Margin $v"
    override val parsingMode = "Разбор сообщений"
    override val modeHybrid = "Гибрид"
    override val modeEconomy = "Экономно"
    override val modePrecise = "Точно"
    override val modeHybridHint = "Простые фразы — Alice Flash, сложные — DeepSeek"
    override val modeEconomyHint = "Всё через Alice Flash: дешевле, но чаще ошибается"
    override val modePreciseHint = "Всё через DeepSeek: точнее, дороже примерно вдвое"
    override fun kindTasks(n: Int) = "$n " + pluralRu(n, "дело", "дела", "дел")
    override val kindChanged = "изменено"
    override val kindNote = "идея"
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
    override val ob4Text = "Уведомления — для напоминаний. Календарь — чтобы не пропустить встречу. Всё можно поменять в настройках."
    override val notifications = "Уведомления"
    override val calendar = "Календарь"
    override fun queueDoneText(n: Int) = "Разобрано: $n"
    override fun remindPerson(name: String) = "Напомнить " + name.dative()
    override fun nudgeText(what: String) = "Привет! Как там «$what»?"
    override fun whisperDownloading(p: Int) = "Загрузка… $p%"
    override fun whisperReady(name: String) = "Готово · $name"
    override val errNoKey = "Добавьте ключ OpenRouter в настройках"
    override val errWeird = "Непонятный ответ OpenRouter"
    override val errNet = "Нет связи с интернетом"
    override val errService = "ИИ сейчас недоступен. Попробуйте чуть позже"
    override val errEmpty = "Не получилось понять фразу. Попробуйте сказать иначе"
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
    override val solvedWord = "Сделано"
    override val gotIt = "Получено"
    override fun archivedTopic(t: String) = "«$t» в архиве"
    override val feedTab = "Лента"
    override val questionsTab = "Дела"
    override val materialsTab = "Материалы"
    override fun topicSince(d: String) = "тема с $d"
    override val questionPrefix = "Дело:"
    override val solvedPrefix = "Сделано:"
    override fun waitPrefix(who: String) = "Ждём от ${who.genitive()}:"
    override fun gotPrefix(who: String) = "Получено от ${who.genitive()}:"
    override val becameTopic = "Дело стало темой"
    override val revivedTopic = "Тема вернулась из архива"
    override fun solvedOn(d: String) = "сделано $d"
    override fun gotOn(d: String) = "получено $d"
    override val allSettled = "Похоже, всё сделано."
    override val archiveQ = "Убрать тему в архив?"
    override fun archiveText(t: String) = "Тема «$t» уйдёт в архив вместе с материалами. Новая запись в ней вернёт её."
    override fun inArchiveSince(d: String) = "В архиве с $d"
    override val bringBack = "Вернуть"
    override val openCaps = "Осталось"
    override val waitingCaps = "Ждём"
    override val solvedCaps = "Сделано"
    override fun inFeed(d: String) = "в ленте $d ›"
    override val noQuestions = "Дел нет. Напишите внизу — появится дело."
    override val newQuestionHint = "Новое дело…"
    override val writeInTopic = "Написать в тему…"
    override val materialsEmpty = "Материалов пока нет. Фото, файлы и голосовые из ленты появятся здесь."
    override val searchMaterials = "Искать в материалах и в тексте на фото"
    override val kindPhotos = "Фото"
    override val kindDocs = "Документы"
    override val kindVoice = "Голосовые"
    override val noMaterialsFound = "Ничего не нашлось"
    override val topicsInWork = "Темы"
    override val simpleTasks = "Дела"
    override val thoughts = "Идеи"
    override val noNextStepCaps = "Без следующего шага"
    override fun nQuestions(n: Int) = "$n " + pluralRu(n, "дело", "дела", "дел")
    override fun waitUntil(d: String) = "ждём до $d"
    override val allDoneShort = "всё сделано"
    override fun nMaterials(n: Int) = "$n " + pluralRu(n, "материал", "материала", "материалов")
    override fun nEntries(n: Int) = "$n " + pluralRu(n, "запись", "записи", "записей")
    override val thoughtWord = "идея"
    override val whatNext = "Что дальше?"
    override val labelWord = "Метка"
    override val casesTitle = "Дела"
    override val materialsTitle = "Материалы"
    override val materialsSub = "все фото, файлы и голосовые из тем"
    override val noLabel = "Без метки"
    override val noDateQ = "без даты"
    override val kindTopic = "запись в теме"
    override val kindThought = "идея"
    override fun lateSince(d: String) = "просрочено с $d"
    override fun doneCountWord(n: Int) = "сделано $n"
    override val freeWord = "свободно"
    override val dealsBadge = "Дела"
    override val topicsBadge = "Темы"
    override fun buildLabel(name: String, code: Int, debug: Boolean) = "Margin · сборка $name ($code)" + if (debug) " · отладка" else ""
    override val backWord = "Назад"
    override val todayShort = "сег."
    override fun progressOf(d: Int, n: Int) = "$d из $n"
    override fun progressSolved(d: Int, n: Int) = "$d из $n сделано"
    override fun movedToTopic(t: String) = "Перенесено в «$t»"
    override val toOtherTopic = "В другую тему"
    override val toTopicPick = "В тему…"
    override val recognizeWord = "Распознать"
    override val tagWait = "жду"
    override val tagPromise = "обещаю"
    override val tagDone = "готово"
    override val tagRecord = "запись"
    override val hashWhat = "В какую тему?"
    override val atWhat = "Кто?"
    override fun doneMarked(t: String) = "Сделано: $t"
    override val doneNotFound = "Такого дела нет"
    override fun intoTopic(t: String) = "в «$t»"
    override fun previewDone(t: String) = "отметить сделанным: $t"
    override val previewRecord = "запись"
    override val recordBadge = "Запись"
    override val openWord = "Открыть"
    override val readTextWord = "Распознать текст"
    override val searchWord = "Поиск"
    override fun nIdeas(n: Int) = "$n " + pluralRu(n, "идея", "идеи", "идей")
    override val overviewWord = "Обзор"
    override val monthWord = "Месяц"
    override val dealsOnlyWord = "только дела"
    override fun moreDeals(n: Int) = "ещё $n " + pluralRu(n, "дело", "дела", "дел")
    override fun summaryDeals(n: Int) = "$n " + pluralRu(n, "дело", "дела", "дел")
    override fun summaryTopics(n: Int) = "$n " + pluralRu(n, "тема", "темы", "тем")
    override fun summaryMeetings(n: Int) = "$n " + pluralRu(n, "встреча", "встречи", "встреч")
    override val parseSection = "РАЗБОР ТЕКСТА"
    override val parseLocal = "Телефон"
    override val parseAuto = "Авто"
    override val parseCloud = "ИИ"
    override val parseLocalHint = "Всё разбирается на телефоне — без интернета и без ИИ. Сложное уточните метками «/» или поправьте в карточке."
    override val parseAutoHint = "Простое — на телефоне, сразу и без интернета; сложное (несколько дел подряд, «созвонились…», «перенеси…») — ИИ, когда есть интернет."
    override val parseCloudHint = "Всё разбирает ИИ. Без интернета — телефон."
    override val previewAi = "уточнит ИИ"
    override val demoSection = "ПРИМЕР ДАННЫХ"
    override val demoAskTitle = "Загрузить пример?"
    override val demoAskText = "Два месяца жизни молодого дизайнера: темы, дела всех видов, идеи, контакты, материалы. Ваши данные сохранятся отдельно — вернуть их можно в Настройках."
    override val demoLoad = "Загрузить пример"
    override val demoLater = "Не сейчас"
    override val demoRestore = "Вернуть мои данные"
    override val demoRestoreHint = "Пример исчезнет, ваши записи вернутся"
    override val demoLoadHint = "Ваши данные сохранятся отдельно"
    override val daysWord = "Дни"
    override val addDay = "день"
    override fun dayOfDays(i: Int, n: Int) = "день $i из $n"
    override val tagDeal = "дело"
    override val tagTopic = "тема"
    override val tagIdea = "идея"
    override val tagWork = "работа"
    override val tagPersonal = "личное"
    override val removedWord = "удалено"
    override val restoreWord2 = "Вернуть"
    override val purgeWord = "Удалить насовсем"
    override val underLine = "Добавить дело"
    override val dueWord = "Дата"
    override val slashWhat = "Что это?"
    override val feedTitle = "Ежедневник"
    override val filterWord = "Показать"
    override val filterAll = "Всё"
    override val spherePersonal = "Личное"
    override val sphereWork = "Работа"
    override val sphereAdd = "личное / работа"
    override val orgsBadge = "Компании"
    override val peopleBadge = "Люди"
    override val markOrg = "Это компания"
    override val markPerson = "Это человек"
    override val stepOne = "дело"
    override val stepHint = "Какое дело добавить?"
    override val peopleSub = "люди и компании из ваших дел и тем"
    override val archiveSub = "завершённые темы — со всеми материалами"
    override val sinceWord = "с"
    override val sortsWhenOnline = "разберу, когда будет связь"
    override val unplacedBadge = "Без места"
    override val photoNoCaption = "Фото без подписи"
    override fun thoughtSince(d: String) = "идея с $d"
    override val thoughtsToday = "Идеи за сегодня"
    override val questionDateQ = "Дата"
}

object En : Strings {
    override val helpTitle = "Help"
    override val helpIntro = "Sections, marks and gestures"
    override val helpSearch = "Search help…"
    override val helpGroups = listOf(
        "How it works" to listOf("How it works", "AI assistant", "Three kinds", "Menu"),
        "Screens" to listOf("Today", "Calendar", "Overview", "Today · Calendar · Overview", "Inside a topic", "Inbox, Materials, Archive"),
        "Writing" to listOf("Marks in the field", "Examples", "Dates, times, repeats", "Lists", "Quick entry and feedback", "Sharing to Margin", "Offline"),
        "People and promises" to listOf("The week, reminders, widget"),
        "Icons" to listOf("Icons", "Badges and colours"),
        "Settings and data" to listOf("Privacy"),
    )
    override val helpSections = listOf(
        HelpSection("How it works", "Margin is a planner you write to like a messenger. One field at the bottom: type or dictate, and your words become to-dos, topics and ideas. It is read right on the phone, without internet; the AI joins only for the hard parts, when online.",
            examples = listOf("Call Marina tomorrow at 3", "Buy cat food", "Idea: a design blog")),
        HelpSection("AI assistant", "The built-in assistant keeps the planner from your words: it turns phrases into to-dos, dates and promises, moves and closes to-dos, keeps topics — and answers questions about your affairs briefly, in two or three sentences. It works online with an invite code; plain phrases are understood without it. It does not write long texts like contracts — «prepare a contract» becomes your to-do.", pairs = listOf(
            "what's urgent this week?" to "late items, near dates, who owes whom — with why",
            "what did I promise Lyosha?" to "your promises to that person",
            "move the dentist to Friday" to "moves the to-do",
            "called Oleg, the layout is approved" to "closes the to-do and writes a record in the topic",
        )),
        HelpSection("Three kinds", "", pairs = listOf(
            "To-do" to "one action. The circle marks it done",
            "Topic" to "a to-do that grew: its own feed of records, files and to-dos. A to-do becomes a topic by itself on its second event",
            "Idea" to "a thought without to-dos; a feed like a topic's",
        )),
        HelpSection("Menu", "Top left: Inbox, Planner, Materials, Contacts, Archive; Settings and Help below.", image = "drawer"),
        HelpSection("Today", "The day's summary (how much, who owes what), to-dos by the hour, «No date», «Tomorrow — N».", image = "today"),
        HelpSection("Calendar", "The month as a grid; the chosen day below.", image = "calendar"),
        HelpSection("Overview", "Topics, To-dos, Ideas; filter Personal / Work.", image = "overview"),
        HelpSection("Today · Calendar · Overview", "", pairs = listOf(
            "Today" to "the day's summary (how much, who owes what), to-dos by the hour (a topic's to-dos as one block under its name), «No date» with their age, «Tomorrow — N»",
            "tap a to-do" to "its card, anywhere on the line; a long press — Done · Date · Time",
            "the card" to "as tall as it needs; the title and a comment are edited in place, the field below takes changes in words; a to-do with a comment shows 💬",
            "Calendar" to "the month as a grid and the chosen day",
            "Overview" to "everything: Topics, To-dos, Ideas; filter Personal / Work",
        )),
        HelpSection("Marks in the field", "Marks tell the app exactly what you meant. Type the sign — a small window offers choices; the chosen one sits over the field. Combine them in any order.", pairs = listOf(
            "/todo  /topic  /idea" to "what it is",
            "/work  /personal" to "the sphere",
            "#topic" to "which topic: words go to its feed, to-dos to its to-dos",
            "@person" to "who: a contact or a company",
            "/wait @Masha" to "we wait from Masha: ←",
            "/promised @Lesha" to "you promised Lesha: →",
            "/done words" to "mark done the to-do with these words; after «:» — a record in its topic",
            "/note" to "only into the feed, no to-dos",
        )),
        HelpSection("Examples", "", examples = listOf("#repair buy grout by Friday", "/wait @Masha keys by Saturday", "/done #coffee packaging: Oleg approved")),
        HelpSection("Dates, times, repeats", "Plain words work without the AI: tomorrow at 3, on Friday, in a week, on Mon and Thu at 7 pm (one to-do on several days), every Tuesday (a repeat)."),
        HelpSection("Inside a topic", "Everything written in a topic goes into its feed: the day on the margin, «RECORD · time» over the words, to-dos in blocks.", image = "topic", pairs = listOf(
            "the circle" to "done",
            "long press a line" to "a to-do under it, edit, due date, delete",
            "tap the summary" to "to-dos only",
            "paperclip" to "materials only",
        )),
        HelpSection("Icons", "The narrow column left of the circle says what kind of to-do it is.", legend = "icons"),
        HelpSection("Badges and colours", "", legend = "badges"),
        HelpSection("Sharing to Margin", "From Telegram, MAX, mail: “Share” or select text → “To Margin”. Or “Copy” — and “Paste what you copied” appears in Margin. Margin reads other people’s words as theirs: what they promise you becomes “waiting”, what they ask — a to-do."),
        HelpSection("Quick entry and feedback", "The “Write” tile in quick settings (add it with the pencil there) and a long press on the app icon — “Write” / “Dictate”. Understood wrong — “Understood wrong” under the result. “Send feedback” in the menu opens an e-mail to the developer with the error logs; your records are not sent."),
        HelpSection("The week, reminders, widget", "On Sundays «Today» shows a quiet card of the week: done, who owes whom, topics that stood still; «×» hides it until next Sunday. When someone's promise is late, «remind» next to it prepares a message (SMS if the phone is known, otherwise Share); you send it. The «Margin · Today» widget shows the day and opens the field or dictation. A whole topic can be shared as text: ⋯ → Share."),
        HelpSection("Privacy", "Settings → Privacy: a fingerprint (or screen lock) when Margin opens, and «Where your data is» — where the records live and what leaves the phone. On the lock screen reminders show without the to-do's words."),
        HelpSection("Contacts", "«Often» on top — who you have most to-dos with this month; then everyone, A–Z or «New». A phone, an e-mail or a link anywhere in your words opens its app (a bare «@name» — Telegram). A person's social pages are one field: paste the link, the network is told by the address."),
        HelpSection("Lists", "Start a line with «1.», «-» or «•» — Enter continues the list, Enter on an empty item ends it."),
        HelpSection("Inbox, Materials, Archive", "Inbox — what was written offline or saved without words; a tap opens it, swipe left — Remove, right — To a topic…; on a photo «Recognize» puts its text under it. Materials — all files by day, with search. Archive — settled topics; news in one brings it back."),
        HelpSection("Offline", "Settings → «Reading your words»: Phone — code only; Auto — hard phrases go to the AI when online; AI — as before. The line over the field shows what was understood before you send."),
    )
    override val tourWriteTitle = "Write as you think"
    override val tourWriteText = "One field at the bottom — type or dictate. Margin sorts it into to-dos, dates and promises, even offline."
    override val tourWriteExample = "Call with Marina tomorrow at 3 pm, she sends the edits by Wednesday"
    override val tourMarks = listOf("#topic", "@person", "/wait", "/promised", "/idea")
    override val tourMarksText = "Marks — when it has to be exact:"
    override val tourTopicTitle = "A to-do becomes a topic"
    override val tourTopicText = "A record, a file or a second to-do — and a topic begins."
    override val tourTopicName = "Bathroom repair"
    override val tourTopicDeals = listOf("Buy the tiles", "Estimate from the builder")
    override val tourTopicWho = "Sergey"
    override val tourGesturesTitle = "Tap to open"
    override val tourGesturesText = "A tap on a to-do opens its card."
    override val tourSwipeDone = "→ right — done"
    override val tourSwipeLater = "← left — put off"
    override val tourHold = "hold — menu"
    override val tourOpened = "In the app, this to-do's card opens"
    override fun pageOf(n: Int, of: Int) = "$n of $of"
    override val tourUndo = "Any change can be undone. If Margin got it wrong — tap “Not quite”."
    override val tourMore = "More in the menu, “Help”."
    override val tourDemoCall = "Call with Marina"
    override val tourDemoEdits = "Get the edits from Marina"
    override val tourDemoName = "Marina"
    override val aiUndoTitle = "Undone after AI"
    override val inTaskWord = "In a to-do"
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
    override val forwardHint = "Margin will sort it: to-dos, promises, ideas"
    override val samplesLine = "These are examples"
    override val ideaBadge = "Idea"
    override val openTopic = "Open the topic"
    override fun aboutDeal(title: String, text: String) = "About the to-do «$title»: $text"
    override val ideaHint = "The idea in words"
    override val openWhole = "Open in full"
    override val ideasBadge = "Ideas"
    override val samplesRemove = "Remove examples"
    override val samplesBack = "Bring examples back"
    override val samplesTitle = "Examples"
    override val askOffline = "The assistant answers questions when there is internet and an invite code."
    override val errInvite = "The invite code did not work — check it in Settings"
    override val inviteTitle = "Invite code"
    override val inviteHint = "Turns on the AI: hard phrases, voice, text on photos. Without it everything is read on the phone."
    override val inviteNone = "not set"
    override val inviteAlpha = "alpha access"
    override val privacyPolicy = "Privacy policy"
    override val forwardSorting = "Sorting…"
    override val forwardResult = "Here is what came of it"
    override val forwardOpen = "Open in Margin"
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
    override val storeRestoredTitle = "The last change may not have been saved"
    override val storeRestoredText = "The file with your records was damaged, so the previous copy was opened. The damaged file is kept inside the app — nothing was deleted."
    override val storeLostTitle = "Couldn’t read your records"
    override val storeLostText = "The file with your records and its copy are damaged. Both are kept inside the app — nothing was deleted. Write to the developer: the records can be brought back."
    override val storeNewerTitle = "Records from a newer version"
    override val storeNewerText = "Your records were made by a newer version of Margin. A copy is kept; update the app so nothing gets lost."
    override val tryExamples = listOf("Call with Oleg tomorrow at 3", "Masha returns 3000 by Friday", "Idea: a podcast about design")
    override val tryHint = "Try one — a tap puts the phrase into the field"
    override val toStart = "↑ to the start"
    override val orderAz = "A–Z"
    override val orderNew = "New"
    override val frequentBadge = "Often"
    override val allBadge = "All"
    override val dateWord = "Date"
    override val toTomorrow = "Move to tomorrow"
    override val privacySection = "Privacy"
    override val lockTitle = "Fingerprint lock"
    override val lockHint = "Margin asks for your fingerprint or screen lock when it opens"
    override val lockOpen = "Open"
    override val whereDataTitle = "Where your data is"
    override val whereDataText = "Your records live on this phone, in the app's own storage — Margin keeps no other copy.\n\n" +
        "Backup: when on, once a day to your Google account (Google Drive), visible only to you.\n\n" +
        "Reading phrases: simple ones are read on the phone and go nowhere. Harder ones, when online, go to Yandex AI Studio (servers in Russia) with the part of your records they need — related to-dos and names. Yandex does not store these requests.\n\n" +
        "Voice: on the phone (Whisper) or Yandex SpeechKit.\n\n" +
        "Error logs reach the developer only in an e-mail you send yourself; your records are not in them.\n\n" +
        "On the lock screen reminders show without the to-do's words."
    override val widgetFree = "Nothing for today"
    override val moreWord = "more:"
    override val exportDone = "Done:"
    override val exportWaiting = "Waiting for:"
    override val exportOpen = "Still to do:"
    override val exportNotes = "Latest records:"
    override val weekTitle = "The week"
    override fun weekLine(done: Int, owed: Int, owe: Int) = "Done: $done  ·  waiting for: $owed  ·  you promised: $owe"
    override val weekStill = "Topics with no news for a week:"
    override val remindLink = "remind"
    override fun remindText(name: String, what: String, date: String?) = "Hi $name! A reminder about $what" + (date?.let { " — we agreed on $it" } ?: "") + "."
    override fun likelyTopic(title: String) = "to «${title.take(28)}»?"
    override val misparsed = "Not quite"
    override val misparsedThanks = "Thanks — the phrase is saved for the developer"
    override val sendFeedback = "Send feedback"
    override fun feedbackSubject(version: String) = "Margin · feedback · $version"
    override fun feedbackBody(version: String, device: String, files: Int) =
        "What happened or what's missing:\n\n\n—\nMargin $version · $device" + if (files > 0) "\nAttached: error and misunderstanding logs — none of your records." else ""
    override val feedbackNoMail = "No e-mail app found"
    override val crashTitle = "Margin closed with an error"
    override val crashText = "Send a description of the error to the developer? Your records are not sent."
    override val crashSend = "Send"
    override val crashLater = "Not now"
    override val searchRecent = "Recent"
    override val searchTry = listOf("people", "topics", "files")
    override val tileWrite = "Write"
    override val shortcutDictate = "Dictate"
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
    override val notesCreated = "Saved to ideas"
    override fun tasksCount(n: Int) = "$n " + if (n == 1) "to-do" else "to-dos"

    override val smart = "LISTS"
    override val inbox = "No date"
    override val overdue = "Overdue"
    override val completed = "Completed"
    override val projects = "PROJECTS"
    override val people = "CONTACTS"
    override val allTasks = "All to-dos"

    override val composerHint = "A to-do, a record or an idea…"
    override val noteHint = "New idea…"
    override val chipNext = "What's next?"
    override val chipPlanDay = "Plan my day"
    override val thinking = "Thinking…"
    override val voiceUnavailable = "Voice input isn't available on this phone"

    override val taskCreated = "To-do created"
    override fun tasksCreated(n: Int) = "$n to-dos created"
    override val taskUpdated = "To-do updated"
    override fun subtasksAdded(n: Int) = "$n " + (if (n == 1) "subtask" else "subtasks") + " added"
    override val noteCreated = "Idea created"
    override val edit = "Edit"
    override val undo = "Undo"
    override val undone = "Undone"
    override val remindFriday = "Remind on Friday"
    override val dismiss = "Dismiss"
    override val noKey = "The AI needs an invite code — enter it in Settings. Plain phrases are read without it."
    override val openSettings = "Open settings"
    override fun movedOverdue(n: Int) = "Moved to today: $n"
    override val nothingOverdue = "Nothing overdue"

    override val emptyToday = "Nothing for today"
    override val emptyWeek = "Your week is clear"
    override val emptyAll = "No to-dos yet"
    override val emptyHint = "Just say or type what you need: a to-do, a record, an idea or a change. For example: “Meeting with Marina tomorrow at 3” or “Watch Nolan’s Odyssey”."
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
    override val madeNote = "Now it’s an idea"
    override val toNote = "Make it an idea"
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
    override fun fillHint(what: String) = "Type or say it"
    override val freeDayHint = "Nothing planned for today. Rest — or take something from “Week”."
    override val askTime = "Set a time?"
    override val askWhen = "Set a date?"
    override fun ideaBecameTask(title: String) = "Now a to-do: $title"
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
    override val commentHint = "Comment"
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
    override val deleteTaskQ = "Delete this to-do?"
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

    override val searchNotes = "Search ideas…"
    override val newNote = "New idea"
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
    override val fastModelHint = "turns your text into to-dos"
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
    override val inboxEmptyHint = "What you write without internet lands here and sorts itself once you’re back online — and so does what you share to Margin."
    override val savedUnsorted = "SAVED UNSORTED"
    override fun queuedLine(n: Int) = "Waiting for internet: $n"
    override fun inboxLine(n: Int) = "In the Inbox: $n — sort out"
    override val nothingFound = "Nothing found"
    override val entryAdded = "Record added"
    override val addOrChange = "What to change?"
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
    override val emptyPeople = "Contacts appear by themselves once you mention a person or a company in to-dos or topics."
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
    override val searchHint = "To-dos, topics, ideas, people…"
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
    override val googleBackupHint = "Once a day to-dos, topics, ideas and settings are saved to your Google account (Google Drive) and come back on a new phone."
    override val googleBackup = "Copy to your Google account"
    override val googleBackupOff = "Off: if the phone is lost, your records won’t come back."
    override val skip = "Skip"
    override val next = "Next"
    override val begin = "Start"
    override val kindTask = "to-do"
    override val freshSummary = "FRESH SUMMARY"
    override val weekSummary = "PROJECT SUMMARY"
    override val toTaskPill = "Make a to-do"
    override val ideasOpen = "IDEAS WITHOUT TO-DOS"
    override fun waitLine(what: String) = "waiting: $what"
    override fun promiseLine(what: String) = "you promised: $what"
    override val ideaMark = "from an idea"
    override val linksTitle = "LINKS"
    override val linkIdea = "Idea"
    override val inTasks = "Already a to-do"
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
    override val fieldSocial = "Social"
    override val socialHint = "Link to the page"
    override val personalTitle = "PERSONAL"
    override val personalHint = "birthday, family, what they like"
    override val orgAboutTitle = "ABOUT THE COMPANY"
    override val fieldOrgKind = "What it does"
    override val orgAboutHint = "website, opening hours, details"
    override val copied = "Copied"
    override val placeHint = "Address or name, e.g. “5 Lenin St”"
    override val historyTitle = "HISTORY"
    override val srcTaskEntry = "from a task record"
    override val srcChat = "from a conversation"
    override val srcNote = "note"
    override val noDue = "no date"
    override val tasksCaps = "TO-DOS"
    override val makeSummary = "Make a summary"
    override fun fromNote(title: String, date: String) = "from note “$title” · $date"
    override fun fromChat(date: String) = "from a conversation · $date"
    override fun promised(who: String) = "Promise: to $who from you"
    override fun summaryAt(t: String) = "updated $t"
    override val reopened = "To-do reopened"
    override val markedDone = "Done"
    override val allDoneToday = "Today's main things are done"
    override val quickToday = "Today"
    override val quickTomorrow = "Tomorrow"
    override val quickNextWeek = "Next week"
    override val quickNone = "No date"
    override val rangeLink = "Range…"
    override val singleLink = "Single date"
    override val savedTick = "Saved"
    override val toSeparateNote = "as a separate idea"
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
    override fun kindTasks(n: Int) = "$n to-dos"
    override val kindChanged = "updated"
    override val kindNote = "idea"
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
    override val ob4Text = "Notifications — for reminders. Calendar — so you don't miss a meeting. You can change this in Settings."
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
    override val solvedWord = "Done"
    override val gotIt = "Received"
    override fun archivedTopic(t: String) = "“$t” archived"
    override val feedTab = "Feed"
    override val questionsTab = "To-dos"
    override val materialsTab = "Materials"
    override fun topicSince(d: String) = "topic since $d"
    override val questionPrefix = "Open item:"
    override val solvedPrefix = "Done:"
    override fun waitPrefix(who: String) = "Waiting for $who:"
    override fun gotPrefix(who: String) = "Received from $who:"
    override val becameTopic = "The to-do became a topic"
    override val revivedTopic = "The topic is back from the archive"
    override fun solvedOn(d: String) = "done $d"
    override fun gotOn(d: String) = "received $d"
    override val allSettled = "Looks like everything is done."
    override val archiveQ = "Is the topic settled?"
    override fun archiveText(t: String) = "“$t” moves to the archive with its materials. Any news in it brings it back."
    override fun inArchiveSince(d: String) = "Archived $d"
    override val bringBack = "Bring back"
    override val openCaps = "Open"
    override val waitingCaps = "Waiting"
    override val solvedCaps = "Done"
    override fun inFeed(d: String) = "in the feed, $d ›"
    override val noQuestions = "No to-dos. Write below — a to-do appears."
    override val newQuestionHint = "New to-do…"
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
    override fun nQuestions(n: Int) = if (n == 1) "1 to-do" else "$n to-dos"
    override fun waitUntil(d: String) = "waiting till $d"
    override val allDoneShort = "all done"
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
    override fun lateSince(d: String) = "overdue since $d"
    override fun doneCountWord(n: Int) = "$n done"
    override val freeWord = "free"
    override val dealsBadge = "To-dos"
    override val topicsBadge = "Topics"
    override fun buildLabel(name: String, code: Int, debug: Boolean) = "Margin · build $name ($code)" + if (debug) " · debug" else ""
    override val backWord = "Back"
    override val todayShort = "today"
    override fun progressOf(d: Int, n: Int) = "$d of $n"
    override fun progressSolved(d: Int, n: Int) = "$d of $n done"
    override fun movedToTopic(t: String) = "Moved to “$t”"
    override val toOtherTopic = "Another topic"
    override val toTopicPick = "To a topic…"
    override val recognizeWord = "Read text"
    override val tagWait = "wait"
    override val tagPromise = "promised"
    override val tagDone = "done"
    override val tagRecord = "note"
    override val hashWhat = "Which topic?"
    override val atWhat = "Who?"
    override fun doneMarked(t: String) = "Done: $t"
    override val doneNotFound = "No such to-do found"
    override fun intoTopic(t: String) = "into «$t»"
    override fun previewDone(t: String) = "mark done: $t"
    override val previewRecord = "record"
    override val recordBadge = "Record"
    override val openWord = "Open"
    override val readTextWord = "Read the text"
    override val searchWord = "Search"
    override fun nIdeas(n: Int) = "$n " + if (n == 1) "idea" else "ideas"
    override val overviewWord = "Overview"
    override val monthWord = "Month"
    override val dealsOnlyWord = "to-dos only"
    override fun moreDeals(n: Int) = "$n more " + if (n == 1) "to-do" else "to-dos"
    override fun summaryDeals(n: Int) = "$n " + if (n == 1) "to-do" else "to-dos"
    override fun summaryTopics(n: Int) = "$n " + if (n == 1) "topic" else "topics"
    override fun summaryMeetings(n: Int) = "$n " + if (n == 1) "meeting" else "meetings"
    override val parseSection = "READING YOUR WORDS"
    override val parseLocal = "Phone"
    override val parseAuto = "Auto"
    override val parseCloud = "AI"
    override val parseLocalHint = "Everything is read on the phone — no internet, no AI. Use «/» tags for the rest or fix it in the card."
    override val parseAutoHint = "Simple phrases on the phone, at once and offline; hard ones (several things at once, reports, edits) by the AI when online."
    override val parseCloudHint = "The AI reads everything, as before. Offline — the phone."
    override val previewAi = "the AI will refine"
    override val demoSection = "SAMPLE DATA"
    override val demoAskTitle = "Load sample data?"
    override val demoAskText = "Two months of a young designer's life: topics, every kind of to-do, ideas, contacts, materials. Your data is kept aside — bring it back in Settings."
    override val demoLoad = "Load sample"
    override val demoLater = "Not now"
    override val demoRestore = "Bring back my data"
    override val demoRestoreHint = "The sample goes away, your records come back as they were"
    override val demoLoadHint = "Your data is kept aside"
    override val daysWord = "Days"
    override val addDay = "day"
    override fun dayOfDays(i: Int, n: Int) = "day $i of $n"
    override val tagDeal = "todo"
    override val tagTopic = "topic"
    override val tagIdea = "idea"
    override val tagWork = "work"
    override val tagPersonal = "personal"
    override val removedWord = "removed"
    override val restoreWord2 = "Bring back"
    override val purgeWord = "Delete for good"
    override val underLine = "A to-do under it"
    override val dueWord = "Due date"
    override val slashWhat = "What is it?"
    override val feedTitle = "Planner"
    override val filterWord = "Show"
    override val filterAll = "Everything"
    override val spherePersonal = "Personal"
    override val sphereWork = "Work"
    override val sphereAdd = "personal or work?"
    override val orgsBadge = "Companies"
    override val peopleBadge = "People"
    override val markOrg = "It’s a company"
    override val markPerson = "It’s a person"
    override val stepOne = "to-do"
    override val stepHint = "Which to-do to add?"
    override val peopleSub = "people and companies from your matters and topics"
    override val archiveSub = "settled topics, with all their materials"
    override val sinceWord = "since"
    override val sortsWhenOnline = "will be sorted once you’re online"
    override val unplacedBadge = "Unplaced"
    override val photoNoCaption = "Photo, no caption"
    override fun thoughtSince(d: String) = "idea since $d"
    override val thoughtsToday = "Ideas today"
    override val questionDateQ = "Due date"
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
