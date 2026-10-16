# Margin UI audit (ui/*.kt, read-only)

Scope: about 30 composables can't be reached. They are TaskCard, PromisesCard, MarginPromises, WeekStrip, EventRow, QueuedRow, ToggleText, MarginTaskRow, TopicDayRow, CompactLine, QuestionsPage, QuestionRow, QuestionsPill, LineTools, DatePill, PromiseBadge, PersonBadge, Countdown, Prop, MetaChip, BigChip, EntryView, SectionRow, ProjectsScreen, NotesList and MarginDay. TaskList's WEEK/DONE/project branches are also unreachable (App.kt:206 sends WEEK to CalendarScreen), and so is DayBlock's folded state. Everything below is live code.

## 1. Typography
Theme.kt defines 9 styles, but live code uses **22 distinct sizes** (number of call sites in brackets):
- **Labels:** 9.5 (1), 10 (2), 10.5 (6), 11 (5), 11.5 (9)
- **Meta:** 12 (14), 12.5 (35), 13 (40), 13.5 (41), 14 (48), 14.5 (10)
- **Body:** 15 (25), 15.5 (17), 16 (63), 17 (9), 20 (1)
- **Serif titles:** 19 (23), 26 (2), 32 (3), 36 (2), 38 (1), 40 (5)
- `Type.task` is used only in the dead TaskCard; `Type.italic` is never used.

Near-duplicate sizes for the same role:
- **Row title:**
  - 15.5: Today DealRow (TasksScreen.kt:1032), topic feed (TopicSheet.kt:730), Inbox (InboxScreen.kt:168)
  - 15: day tiles (TasksScreen.kt:1407,1421), «Связи» (TaskSheet.kt:291)
  - 16: Overview (TasksScreen.kt:1184,1232), Search (Search.kt:159,174)
  - 14.5: nested matters, file blocks (Blocks.kt:249)
- **Meta line under a row:**
  - 13.5 on Today (Components.kt:300)
  - 13 for the "same row" in the topic feed (TopicSheet.kt:755), Overview (TasksScreen.kt:1192), Inbox (:174)
  - 12.5 topic line (TasksScreen.kt:1104), NoteCard (NotesScreen.kt:139)
  - Settings hints are 12.5 (:340) next to 13 (:150).
- **User's own text:** 15.5 in the feed and comments (TopicSheet.kt:647, Comments.kt:36), 15 in captions (Blocks.kt:392), 14 in Overview records (TasksScreen.kt:1207).
- **Links:** 12.5 (TasksScreen.kt:1159), 13 (:1163), 13.5 (:1255), 14 (PaperLink, Margin.kt:426), 14.5 (CardTop.kt:49).
- **Caps block headings on Today:** GroupBadge is 10.5 (Margin.kt:389), but «ОБЕЩАНИЯ» and «БЕЗ ДАТЫ» are bare 11.5 labels (TasksScreen.kt:1139,1432).

Serif (Playfair: display/title/cardTitle) is used for page titles 40, topic 32, note 32, person 36, dialog titles and some list names 19. Inconsistent spots:
- **Drawer:** main rows are serif 19, Settings/Help/Feedback rows are sans 15 — same NavRow (App.kt:488).
- **Person names:** serif 19 in Contacts and Archive (PeopleScreen.kt:128, Sections.kt:240), sans 16 in Search (Search.kt:174) and the merge dialog (PeopleScreen.kt:310).
- **Ideas:** serif 19 in NoteCard (NotesScreen.kt:134), sans 16 in Overview (TasksScreen.kt:1232).
- **Matter titles:** sans everywhere except the result bubble, which is serif 17 (Tray.kt:254).
- **32sp headlines:** two different serif styles, `display` (TopicSheet.kt:278) and `title` (NotesScreen.kt:191).

## 2. Touch targets under 40dp
Nothing in ui/ uses IconButton or minimumInteractiveComponentSize.
1. **3dp:** the progress bar is the «только дела» toggle (TopicSheet.kt:404).
2. **26×24dp main «done» bagel:** CheckCircle asks for 34dp but is boxed to 26×24 inside a row whose tap opens the card (TasksScreen.kt:1025). Same at TopicSheet.kt:720 (22×24 when nested), TaskSheet.kt:218 and DealSheet.kt:157.
3. **18dp bare text:** «Вернуть» (TopicSheet.kt:303), «↳ идея» (TaskSheet.kt:248), reminder/place (NotesScreen.kt:204,211), «+ подпись» (Blocks.kt:393). «Читать дальше» is about 20dp (Tray.kt:343).
4. **20dp:** remove-attachment × (Input.kt:426).
5. **22dp:**
   - «…ещё N дел ▾» (TasksScreen.kt:1106), also shifted −4dp into the row above
   - «напомнить» (:1160)
   - «N записей ▾» (:1193, :1235)
   - SphereBadge toggle (Margin.kt:439)
   - «+ поле» links (NotesScreen.kt:229, PeopleScreen.kt:239)
6. **23dp per line:** Overview topic/idea title rows have no padding (TasksScreen.kt:1181, :1229).
7. **26dp:**
   - card MetaLine values: date/time/repeat/sphere/people (CardTop.kt:41)
   - summary filters (TopicSheet.kt:389)
   - sphere pills (TasksScreen.kt:425)
   - tag chips (Input.kt:270)
8. **28–34dp:**
   - day chips (TaskSheet.kt:488)
   - PaperLink (Margin.kt:428)
   - dismiss buttons (Tray.kt:110, App.kt:274)
   - Pill/QuietLink, including onboarding «Далее» (Onboarding.kt:139)

## 3. Accessibility
- **Icon-only controls with contentDescription = null:**
  - topic ⋯ (TopicSheet.kt:260)
  - note back and ⋯ (NotesScreen.kt:173, 178)
  - person back and ⋯ (PeopleScreen.kt:169, 174)
  - calendar ‹ › (Sections.kt:145, 148)
  - contact Call/SMS (Blocks.kt:465)
  - remove × (Input.kt:428)
  - WeekCard close, which is `Text("×")` (TasksScreen.kt:1493)
- **Canvas glyph buttons:**
  - Composer «+», mic, send and record-done (Input.kt:303, 361, 363, 341) have only onClickLabel — no contentDescription or Role.
  - TaskRow «получено» arrow (TasksScreen.kt:680) and the progress toggle (TopicSheet.kt:404) have no semantics at all.
- **CheckCircle:** has Role.Checkbox but no label (Components.kt:126). With a long press it has no checked state (TaskSheet.kt:219, DealSheet.kt:158).
- **Focusable controls that do nothing:**
  - `CheckCircle {}` at TasksScreen.kt:1253, TopicSheet.kt:1031, Help.kt:179–180
  - the date box at TasksScreen.kt:1351 (`onToggle = {}` in every live caller)
- Filter toggles don't expose a selected state.

## 4. Contrast
`c.faint` is 2.75:1 on bg and 2.92 on card in light theme, 4.22/3.87 in dark. CLAUDE.md reserves faint for removed items, but it carries meaningful text:
- **Dates/labels:** «5 ОКТ · 10:00» (Blocks.kt:202), «ФОТО» (Blocks.kt:222), calendar counts and weekend headers (Sections.kt:133, 159), version (App.kt:469).
- **Status/actions:** «Читаю текст на фото…» (Blocks.kt:355), «Распознаю…» (Blocks.kt:434), «+ подпись» (Blocks.kt:393).
- **Content:**
  - last-entry preview (TasksScreen.kt:695)
  - OCR snippet (TopicSheet.kt:1047)
  - link host (Blocks.kt:480)
  - history source (PeopleScreen.kt:263)
  - «Что это?/Кто?» popup header (Input.kt:236)
  - subtask numbers (Components.kt:420)
- **Placeholders:** «Комментарий» (Comments.kt:60), and also TopicSheet.kt:459, NotesScreen.kt:190, 239.
- **Removed items (by design):** TopicSheet.kt:647, 655, 758.
- **Low alpha on the bubble (dark-theme contrast):**
  - time at α0.5, 11sp — 3.35:1 (Tray.kt:151)
  - kind at α0.55 — 3.90:1 (Tray.kt:266)
  - meta at α0.6 — 4.56:1 (Tray.kt:255)
- **Other light-theme colours under 4.5:1 on small text:**
  - sage `c.ok` 3.29:1: «✓ Сохранено» (TaskSheet.kt:155)
  - terracotta `c.accent` 3.55:1: late dates (Margin.kt:99, TasksScreen.kt:1034)
  - «СЕГОДНЯ» 9.5sp 3.21:1 (Margin.kt:474)

## 5. Hardcoded strings
- **Language mixing:** «10 окт» and «с 6 окт» appear in the English UI (Help.kt:215–216).
- About 20 inline `t("ru","en")` pairs bypass Strings.kt (Help.kt:179–223).
- **Placeholders:**
  - "vuzburo" is live via note ⋯ → Проект (NotesScreen.kt:258); the same in TaskSheet.kt:409 and "дом" in TopicSheet.kt:354, both in dialogs that can't be opened.
  - "@username" shows in the Russian UI (PeopleScreen.kt:292).
- **Mixed scripts:** "Дзен" in English (Reach.kt:52); "Яндекс" vs "Yandex SpeechKit" (SettingsScreen.kt:217, 270, developer section only).
- **«» guillemets hardcoded for English:** Links.kt:54, 76 and TopicSheet.kt:334, 1047 (Help.kt:231 handles quotes correctly).
- Fine: brand names ("Margin", Telegram, MAX) and «Русский/English». No contentDescription literals.

## 6. Duplication worth unifying
- **Filter chips, 6 live variants:**
  - SubTabs (Margin.kt:485)
  - sphere pills (TasksScreen.kt:420)
  - Search kinds (Search.kt:115)
  - two Materials kind rows with the same options but different styles (TopicSheet.kt:464 vs 1007)
  - Contacts order (PeopleScreen.kt:102)
  - Settings Segmented
- **The matter row:**
  - DealRow (TasksScreen.kt:999)
  - DealLine re-implemented in the topic feed (TopicSheet.kt:681)
  - the old TaskRow, still used in Person and Search (TasksScreen.kt:672)
- **Block headings:**
  - GroupBadge
  - inline copies (TopicSheet.kt:708, 637)
  - bare labels (TasksScreen.kt:1139, 1432)
  - SectionLabel (TaskSheet.kt:348, SettingsScreen.kt:325)
- **Items inside SoftMenu:** SoftMenuItem, ToolItem (TopicSheet.kt:580) and raw M3 DropdownMenuItem (Blocks.kt:166, NotesScreen.kt:181, Input.kt:387).
- **«+ поле» links:** PaperLink in cards vs Text with 2dp padding (NotesScreen.kt:228, PeopleScreen.kt:238).
- **Date stamps:** DayMargin, MarginText, Stamp (time in graphite, Comments.kt:77) and faint caps (Blocks.kt:202).
- Delete the dead code first so its variants don't get fixed by mistake.
</agent-message>