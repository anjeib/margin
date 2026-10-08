package ru.anjeib.margin.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * «Пример данных» to try every part of the app: two months of a young designer in Moscow — work topics with briefs,
 * moodboards and waits, personal plans, ideas, deals of every kind (late, today, repeating, on several days, promised,
 * without a date and getting old), people and businesses, the inbox. Dates count from today, so «сегодня» is always live.
 * Pictures and PDFs are drawn on the phone — nothing is shipped in the APK.
 */
object Demo {
    private const val INBOX = "Входящие"

    fun build(ctx: Context): AppData {
        val today = LocalDate.now()
        fun d(n: Long) = today.plusDays(n).toString()
        fun at(n: Long, hm: String) = "${today.plusDays(n)}T$hm"
        val dir = Files.dir(ctx)

        // ---- files: drawn here ----
        fun jpeg(name: String, draw: (Canvas, Int, Int) -> Unit): Attachment {
            val w = 1200; val h = 900
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            draw(Canvas(b), w, h)
            val f = File(dir, "demo_$name.jpg")
            f.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 86, it) }
            return Attachment(kind = "photo", file = f.name, name = f.name, mime = "image/jpeg", size = f.length())
        }
        fun pdf(name: String, title: String, lines: List<String>): Attachment {
            val doc = PdfDocument()
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(26, 26, 26); textSize = 22f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
            page.canvas.drawText(title, 48f, 80f, p)
            p.textSize = 13f; p.typeface = Typeface.DEFAULT
            lines.forEachIndexed { i, l -> page.canvas.drawText(l, 48f, 120f + i * 22f, p) }
            doc.finishPage(page)
            val f = File(dir, "demo_$name.pdf")
            f.outputStream().use { doc.writeTo(it) }
            doc.close()
            return Attachment(kind = "file", file = f.name, name = "$name.pdf", mime = "application/pdf", size = f.length())
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val moodboard = jpeg("moodboard") { c, w, h ->
            c.drawColor(Color.rgb(242, 234, 222))
            val cols = listOf(Color.rgb(200, 100, 59), Color.rgb(122, 86, 60), Color.rgb(217, 190, 150), Color.rgb(60, 52, 46), Color.rgb(160, 168, 130), Color.rgb(236, 220, 196))
            cols.forEachIndexed { i, col -> paint.color = col; val x = 40f + (i % 3) * 380f; val y = 40f + (i / 3) * 420f; c.drawRoundRect(RectF(x, y, x + 350f, y + 390f), 24f, 24f, paint) }
            paint.color = Color.rgb(242, 234, 222); c.drawCircle(w * 0.5f, h * 0.5f, 120f, paint)
        }
        val logos = jpeg("logos") { c, w, h ->
            c.drawColor(Color.rgb(246, 240, 230))
            paint.color = Color.rgb(60, 40, 30)
            listOf(Typeface.SERIF to Typeface.BOLD, Typeface.SANS_SERIF to Typeface.BOLD, Typeface.SERIF to Typeface.ITALIC).forEachIndexed { i, (tf, st) ->
                paint.typeface = Typeface.create(tf, st); paint.textSize = 96f
                c.drawText("ЗЕРНО", 70f, 220f + i * 260f, paint)
                paint.color = Color.rgb(200, 100, 59); c.drawOval(RectF(860f, 120f + i * 260f, 980f, 250f + i * 260f), paint); paint.color = Color.rgb(60, 40, 30)
            }
        }
        val screens = jpeg("screens") { c, w, h ->
            c.drawColor(Color.rgb(235, 238, 242))
            repeat(3) { i ->
                val x = 70f + i * 370f
                paint.color = Color.WHITE; c.drawRoundRect(RectF(x, 60f, x + 320f, 840f), 36f, 36f, paint)
                paint.color = Color.rgb(28, 100, 128); c.drawRoundRect(RectF(x + 30f, 720f, x + 290f, 800f), 20f, 20f, paint)
                paint.color = Color.rgb(210, 214, 220); repeat(5) { r -> c.drawRoundRect(RectF(x + 30f, 120f + r * 110f, x + 290f, 200f + r * 110f), 16f, 16f, paint) }
            }
        }
        val flat = jpeg("flat") { c, w, h ->
            paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.rgb(250, 236, 214), Color.rgb(196, 170, 140), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint); paint.shader = null
            paint.color = Color.rgb(255, 250, 240); c.drawRect(380f, 120f, 820f, 560f, paint)
            paint.color = Color.rgb(120, 92, 70); c.drawRect(0f, 700f, w.toFloat(), h.toFloat(), paint)
        }
        val board = jpeg("board") { c, w, h ->
            c.drawColor(Color.rgb(250, 250, 248))
            paint.color = Color.rgb(40, 60, 140); paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL); paint.textSize = 54f
            listOf("Дизайн-система:", "— сетка 8 pt", "— кнопки: 3 размера", "— цвета: 6 + серые", "дедлайн → пт").forEachIndexed { i, l -> c.drawText(l, 80f, 150f + i * 120f, paint) }
        }
        val brief = pdf("Бриф_Зерно", "Бриф: кофейня «Зерно»", listOf("Логотип, вывеска, упаковка для зерна 250 г и 1 кг.", "Тон: тёплый, ремесленный, без хипстерских клише.", "Бюджет: 120 000 ₽. Срок: конец октября.", "Контакт: Олег, владелец."))
        val tickets = pdf("Билеты_Сапсан", "Электронный билет", listOf("Москва → Санкт-Петербург", "Поезд 752А «Сапсан»", "2 пассажира, вагон 5"))
        val contract = pdf("Договор_Зерно", "Договор оказания услуг", listOf("Исполнитель: самозанятый дизайнер", "Заказчик: ООО «Зерно»", "Предмет: разработка фирменного стиля"))

        fun ev(n: Long, hm: String, ref: String, wait: Boolean = false, entry: String? = null) = TopicEvent(at = at(n, hm), kind = if (wait) "wait" else "q", ref = ref, entry = entry)

        // ---- work topics ----
        val coffee = Task(
            id = "demo-coffee", title = "Айдентика для кофейни «Зерно»", topic = true, sphere = "work", createdAt = d(-52), people = listOf("Олег"),
            entries = listOf(
                Entry(id = "c-e1", at = at(-52, "11:20"), text = "Новый клиент — кофейня «Зерно» на Покровке. Нужны логотип, вывеска и упаковка для зерна. Бюджет 120 тыс., срок — конец октября.", attachments = listOf(brief)),
                Entry(id = "c-e2", at = at(-48, "16:05"), text = "Собрала мудборд: тёплый крафт, терракота, рукописные элементы.", attachments = listOf(moodboard.copy(caption = "Мудборд"))),
                Entry(id = "c-e3", at = at(-30, "19:40"), text = "Олег выбрал второй вариант, просит сделать зерно чуть круглее.", attachments = listOf(logos.copy(caption = "Три варианта логотипа"))),
                Entry(id = "c-e4", at = at(-6, "12:10"), text = "Созвонились: упаковку печатает типография «Оттиск», им нужен макет до пятницы."),
                Entry(id = "c-e5", at = at(-1, "21:15"), text = "Финальная версия знака готова, осталось показать."),
            ),
            subtasks = listOf(
                Subtask(id = "c1", title = "Собрать мудборд", done = true, doneAt = d(-48)),
                Subtask(id = "c2", title = "Сделать три варианта логотипа", done = true, doneAt = d(-31)),
                Subtask(id = "c3", title = "Показать финальный логотип Олегу", due = d(0), time = "15:00"),
                Subtask(id = "c4", title = "Фидбек по упаковке", from = "Олег", due = d(1)),
                Subtask(id = "c5", title = "Макет упаковки для типографии", due = d(2), parent = "c-e4"),
                Subtask(id = "c6", title = "Подготовить брендбук", due = d(6)),
                Subtask(id = "c7", title = "Типографика", parent = "c6"),
                Subtask(id = "c8", title = "Цвета и паттерны", parent = "c6"),
                Subtask(id = "c9", title = "Фотосессия интерьера", slots = listOf(Slot(date = d(2), time = "10:00"), Slot(date = d(4), time = "10:00"))).syncSlots(),
            ),
            removed = listOf(Subtask(id = "c10", title = "Придумать маскота")),
            events = listOf(ev(-52, "11:25", "c1", entry = "c-e1"), ev(-48, "16:10", "c2"), ev(-40, "10:00", "c10"), ev(-1, "21:16", "c3", entry = "c-e5"),
                ev(-6, "12:12", "c4", wait = true, entry = "c-e4"), ev(-6, "12:13", "c5"), ev(-5, "09:30", "c6"), ev(-5, "09:31", "c7"), ev(-5, "09:32", "c8"), ev(-3, "18:00", "c9")),
        )
        val app = Task(
            id = "demo-app", title = "Редизайн приложения доставки", topic = true, sphere = "work", createdAt = d(-20), people = listOf("Лёша", "Дима"),
            entries = listOf(
                Entry(id = "a-e1", at = at(-20, "10:00"), text = "Студия взяла редизайн приложения доставки продуктов. Я веду корзину и оформление заказа."),
                Entry(id = "a-e2", at = at(-9, "18:30"), text = "Лёша: кнопки мелковаты, цену в корзине сделать крупнее."),
                Entry(id = "a-e3", at = at(-2, "14:20"), text = "", attachments = listOf(screens.copy(caption = "Корзина, вторая версия"))),
            ),
            subtasks = listOf(
                Subtask(id = "a1", title = "Экраны корзины", done = true, doneAt = d(-8)),
                Subtask(id = "a2", title = "Оформление заказа — первая версия", done = true, doneAt = d(-3)),
                Subtask(id = "a3", title = "Ревью макетов с Лёшей", due = d(2), time = "12:00", assignee = "Лёша"),
                Subtask(id = "a4", title = "Спецификация API", from = "Дима", due = d(-1)),
                Subtask(id = "a5", title = "Сделать цену в корзине крупнее", due = d(0)),
                Subtask(id = "a6", title = "Анимация добавления в корзину"),
            ),
            events = listOf(ev(-20, "10:05", "a1"), ev(-12, "11:00", "a2"), ev(-4, "16:00", "a3"), ev(-7, "13:00", "a4", wait = true), ev(-9, "18:32", "a5", entry = "a-e2"), ev(-2, "14:25", "a6")),
        )
        val behance = Task(
            id = "demo-behance", title = "Портфолио на Behance", topic = true, sphere = "work", createdAt = d(-40),
            entries = listOf(Entry(id = "b-e1", at = at(-40, "22:10"), text = "Пора обновить портфолио: последний кейс там двухлетней давности."),
                Entry(id = "b-e2", at = at(-11, "23:05"), text = "Опубликовала. 140 просмотров за первый день.")),
            subtasks = listOf(Subtask(id = "b1", title = "Выбрать четыре проекта", done = true, doneAt = d(-35)),
                Subtask(id = "b2", title = "Сверстать обложки кейсов", done = true, doneAt = d(-18)),
                Subtask(id = "b3", title = "Опубликовать", done = true, doneAt = d(-11))),
            events = listOf(ev(-40, "22:12", "b1", entry = "b-e1"), ev(-35, "20:00", "b2"), ev(-18, "21:00", "b3")),
        )

        // ---- personal topics ----
        val piter = Task(
            id = "demo-piter", title = "Поездка в Питер на выходные", topic = true, sphere = "personal", createdAt = d(-14), people = listOf("Маша"),
            entries = listOf(Entry(id = "p-e1", at = at(-14, "20:40"), text = "Едем с Машей в конце месяца, на три дня."),
                Entry(id = "p-e2", at = at(-10, "13:15"), text = "Билеты на «Сапсан» купила, 7 400 ₽ на двоих.", attachments = listOf(tickets))),
            subtasks = listOf(Subtask(id = "p1", title = "Купить билеты", done = true, doneAt = d(-10)),
                Subtask(id = "p2", title = "Отель на Петроградке", from = "Маша", due = d(3)),
                Subtask(id = "p3", title = "Составить маршрут"),
                Subtask(id = "p4", title = "Забронировать стол в «Кококо»", due = d(9))),
            events = listOf(ev(-14, "20:42", "p1", entry = "p-e1"), ev(-10, "13:20", "p2", wait = true), ev(-10, "13:21", "p3"), ev(-8, "09:00", "p4")),
        )
        val flatTopic = Task(
            id = "demo-flat", title = "Квартира поближе к студии", topic = true, sphere = "personal", createdAt = d(-35), people = listOf("Ирина"),
            entries = listOf(Entry(id = "f-e1", at = at(-35, "08:50"), text = "Час до студии каждый день — хватит. Ищу однушку у Бауманской до 65 тыс."),
                Entry(id = "f-e2", at = at(-3, "19:30"), text = "Ирина прислала вариант на Спартаковской: светлая, но кухня маленькая.", attachments = listOf(flat.copy(caption = "Спартаковская, 9")))),
            subtasks = listOf(Subtask(id = "f1", title = "Посмотреть квартиру на Спартаковской", due = d(1), time = "19:00"),
                Subtask(id = "f2", title = "Ещё варианты", from = "Ирина", due = d(2)),
                Subtask(id = "f3", title = "Посчитать бюджет на переезд")),
            events = listOf(ev(-3, "19:32", "f1", entry = "f-e2"), ev(-3, "19:33", "f2", wait = true), ev(-30, "09:00", "f3")),
        )
        val bike = Task(
            id = "demo-bike", title = "Ремонт велосипеда", topic = true, sphere = "personal", createdAt = d(-25), done = true, doneAt = d(-12),
            entries = listOf(Entry(id = "k-e1", at = at(-25, "18:00"), text = "Скрипит каретка и спустило заднее колесо.")),
            subtasks = listOf(Subtask(id = "k1", title = "Отвезти в мастерскую", done = true, doneAt = d(-22)), Subtask(id = "k2", title = "Забрать велосипед", done = true, doneAt = d(-12))),
            events = listOf(ev(-25, "18:02", "k1", entry = "k-e1"), ev(-22, "10:00", "k2")),
        )

        // ---- ideas ----
        fun idea(id: String, title: String, n: Long, vararg words: String) = Task(id = id, title = title, topic = true, thought = true, sphere = "personal", createdAt = d(n),
            entries = words.mapIndexed { i, w -> Entry(at = at(n + i * 3, "23:${10 + i}"), text = w) })
        val ideas = listOf(
            idea("demo-i1", "Пет-проект: трекер привычек для дизайнеров", -33, "Каждый день — одна маленькая визуальная задача, как в Daily UI, но с напоминаниями.", "Посмотреть, как сделано в Streaks."),
            Task(id = "demo-i2", title = "Темы для постов в телеграм-канал", topic = true, thought = true, sphere = "work", createdAt = d(-16),
                entries = listOf(Entry(at = at(-16, "22:40"), isList = true, items = listOf(Subtask(title = "Как я собираю мудборды"), Subtask(title = "Ошибки в брифах", done = true), Subtask(title = "Кейс «Зерно» до и после"))))),
            idea("demo-i3", "Посмотреть выставку в «Гараже»", -4, "Катя говорит, новая экспозиция про советский дизайн."),
            idea("demo-i4", "Книги", -27, "«Дизайн привычных вещей» Нормана — перечитать.", "«Грейпфрут» Йоко Оно — для вдохновения."),
        )

        // ---- deals ----
        fun next(vararg days: DayOfWeek): String = (0L..7L).map { today.plusDays(it) }.first { it.dayOfWeek in days }.toString()
        val npd = today.withDayOfMonth(28).let { if (it.isBefore(today)) it.plusMonths(1) else it }.toString()
        val deals = listOf(
            Task(id = "demo-d1", title = "Продлить подписку Figma", due = d(-2), sphere = "work", createdAt = d(-9)),
            Task(id = "demo-d2", title = "Купить молоко и хлеб", due = d(0), time = "19:30", sphere = "personal", createdAt = d(0)),
            Task(id = "demo-d3", title = "Позвонить маме", due = d(0), time = "20:00", people = listOf("Мама"), sphere = "personal", createdAt = d(-1)),
            Task(id = "demo-d4", title = "Отправить студии счёт за сентябрь", due = d(0), sphere = "work", createdAt = d(-3)),
            Task(id = "demo-d5", title = "Спортзал", due = next(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), time = "08:00", place = "Покровка, 17",
                repeat = Repeat("weekly", days = listOf(1, 3, 5)), sphere = "personal", createdAt = d(-50)),
            Task(id = "demo-d6", title = "Английский с репетитором", sphere = "personal", createdAt = d(-6),
                slots = listOf(Slot(date = d(1), time = "19:00"), Slot(date = d(3), time = "19:00"))).syncSlots(),
            Task(id = "demo-d7", title = "Стоматолог", due = d(1), time = "11:00", place = "Тверская, 12", sphere = "personal", createdAt = d(-12)),
            Task(id = "demo-d8", title = "Скинуть Лёше исходники баннеров", promisedTo = "Лёша", due = d(1), sphere = "work", createdAt = d(-2)),
            Task(id = "demo-d9", title = "Маша вернёт 3 000 ₽", waitingFor = "Маша", due = d(4), sphere = "personal", createdAt = d(-5)),
            Task(id = "demo-d10", title = "Подарок Кате на день рождения", due = d(3), people = listOf("Катя"), sphere = "personal", createdAt = d(-7)),
            Task(id = "demo-d11", title = "Оплатить налог НПД", due = npd, repeat = Repeat("monthly"), sphere = "work", createdAt = d(-45)),
            Task(id = "demo-d12", title = "Вебинар по дизайн-системам", due = d(5), time = "18:00", sphere = "work", createdAt = d(-4)),
            Task(id = "demo-d13", title = "Оплатить аренду", due = d(6), repeat = Repeat("monthly"), sphere = "personal", createdAt = d(-55)),
            // Without a date, of different ages.
            Task(id = "demo-u1", title = "Разобрать фото с отпуска", sphere = "personal", createdAt = d(-21)),
            Task(id = "demo-u2", title = "Обновить резюме", sphere = "work", createdAt = d(-15)),
            Task(id = "demo-u3", title = "Купить лампу в коридор", sphere = "personal", createdAt = d(-5)),
            Task(id = "demo-u4", title = "Записаться к барберу", sphere = "personal", createdAt = d(-2)),
            // Done and not done.
            Task(id = "demo-x1", title = "Сдать макеты лендинга", due = d(-3), done = true, doneAt = d(-3), sphere = "work", createdAt = d(-10)),
            Task(id = "demo-x2", title = "Купить кроссовки", due = d(-10), done = true, doneAt = d(-10), sphere = "personal", createdAt = d(-14)),
            Task(id = "demo-x3", title = "Забрать посылку с Ozon", due = d(-1), done = true, doneAt = d(-1), sphere = "personal", createdAt = d(-2)),
            Task(id = "demo-x4", title = "Курс по 3D в Blender", done = true, dropped = true, doneAt = d(-7), sphere = "work", createdAt = d(-30)),
        )

        // ---- people and businesses ----
        val persons = listOf(
            Person(name = "Олег", about = "владелец кофейни", company = "Кофейня «Зерно»", phone = "+7 916 555-12-40", kept = true),
            Person(name = "Лёша", about = "арт-директор", company = "Студия «Пиксель»", telegram = "@lesha_art", kept = true),
            Person(name = "Дима", about = "разработчик", company = "Студия «Пиксель»", kept = true),
            Person(name = "Маша", about = "подруга", personal = listOf("День рождения 3 марта", "Не ест мясо"), kept = true),
            Person(name = "Катя", about = "сестра", kept = true),
            Person(name = "Мама", phone = "+7 903 111-22-33", kept = true),
            Person(name = "Ирина", about = "риелтор", phone = "+7 925 444-08-17", kept = true),
            Person(name = "Студия «Пиксель»", org = true, address = "Бауманская, 11", kept = true),
            Person(name = "Кофейня «Зерно»", org = true, address = "Покровка, 21", kept = true),
            Person(name = "Типография «Оттиск»", org = true, email = "zakaz@ottisk.ru", kept = true),
            Person(name = "Клиника на Тверской", org = true, address = "Тверская, 12", phone = "+7 495 600-00-00", kept = true),
        )

        // ---- the inbox: saved without words ----
        val inbox = Note(title = INBOX, updatedAt = at(-1, "09:40"), entries = listOf(
            Entry(at = at(-4, "17:20"), attachments = listOf(contract)),
            Entry(at = at(-1, "09:40"), attachments = listOf(board)),
        ))

        return AppData(
            tasks = listOf(coffee, app, behance, piter, flatTopic, bike) + ideas + deals,
            notes = listOf(inbox),
            persons = persons,
        )
    }

    // ---- loading it over the user's data, and back ----

    private fun mine(ctx: Context) = File(ctx.filesDir, "margin.mine.json")
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("demo", Context.MODE_PRIVATE)

    /** The user's own data, kept aside while the example is on. */
    fun hasMine(ctx: Context) = mine(ctx).exists()

    /** Asked once per install whether to load the example. */
    fun asked(ctx: Context) = prefs(ctx).getBoolean("asked", false)
    fun markAsked(ctx: Context) = prefs(ctx).edit().putBoolean("asked", true).apply()

    /** Puts the user's data aside (only the first time — a second load must not overwrite it with the example). */
    fun keepMine(ctx: Context, current: AppData) {
        if (!mine(ctx).exists()) mine(ctx).writeText(AppJson.encodeToString(AppData.serializer(), current))
    }

    fun takeMine(ctx: Context): AppData? = runCatching {
        AppJson.decodeFromString(AppData.serializer(), mine(ctx).readText()).migrated()
    }.getOrNull()?.also { mine(ctx).delete() }

    /** Files drawn for the example, removed when the user's own data comes back. */
    fun dropFiles(ctx: Context) = Files.dir(ctx).listFiles()?.filter { it.name.startsWith("demo_") }?.forEach { it.delete() }
}
