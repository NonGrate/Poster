package com.example.poster.model

/**
 * The tags the app starts with.
 *
 * Seeded once, then owned by the admin panel: a tag that already exists is left
 * alone, so a label edited there survives every redeploy. Adding a tag does not
 * need a deploy at all — this list is a starting point, not the source of truth.
 *
 * The id is what is stored on a post and what a filter matches. It never
 * changes. The labels are what a person reads, and can be corrected freely.
 *
 * ## Replace these with your own
 *
 * The set below is a deliberately generic starter for a "posts with tags" app,
 * filed into the seven [TAG_GROUPS]. Edit both lists together: [group] must be
 * the id of a [TagGroup], or the tag lands under "Other" in the picker.
 *
 * Keep to roughly ten tags per group. The picker shows one group's tags at a
 * time, and ten is what one screen holds without becoming a wall of chips.
 * The instrumented tests and scripts/seed-demo-data.sh reference a few of these
 * ids (health, wellbeing, sleep, family, work, debt, books, ...) — grep before removing one.
 */
data class CuratedTag(
    val id: String,
    val english: String,
    val russian: String,
    val group: String,
)

val CURATED_TAGS: List<CuratedTag> = listOf(
    // Health and wellbeing
    CuratedTag("health", "Health", "Здоровье", "health_wellbeing"),
    CuratedTag("wellbeing", "Wellbeing", "Самочувствие", "health_wellbeing"),
    CuratedTag("fitness", "Fitness", "Спорт", "health_wellbeing"),
    CuratedTag("food", "Food", "Еда", "health_wellbeing"),
    CuratedTag("sleep", "Sleep", "Сон", "health_wellbeing"),
    CuratedTag("habits", "Habits", "Привычки", "health_wellbeing"),
    CuratedTag("self_care", "Self-care", "Забота о себе", "health_wellbeing"),
    CuratedTag("recovery", "Recovery", "Восстановление", "health_wellbeing"),
    CuratedTag("everyday", "Everyday", "Повседневное", "health_wellbeing"),
    CuratedTag("celebration", "Celebration", "Праздник", "health_wellbeing"),

    // Family and people
    CuratedTag("family", "Family", "Семья", "people"),
    CuratedTag("friends", "Friends", "Друзья", "people"),
    CuratedTag("kids", "Kids", "Дети", "people"),
    CuratedTag("parents", "Parents", "Родители", "people"),
    CuratedTag("relationships", "Relationships", "Отношения", "people"),
    CuratedTag("neighbours", "Neighbours", "Соседи", "people"),
    CuratedTag("pets", "Pets", "Питомцы", "people"),
    CuratedTag("home", "Home", "Дом", "people"),
    CuratedTag("volunteering", "Volunteering", "Волонтёрство", "people"),
    CuratedTag("get_together", "Get-together", "Встреча с близкими", "people"),

    // Work and study
    CuratedTag("work", "Work", "Работа", "work_study"),
    CuratedTag("career", "Career", "Карьера", "work_study"),
    CuratedTag("job_search", "Job search", "Поиск работы", "work_study"),
    CuratedTag("studies", "Studies", "Учёба", "work_study"),
    CuratedTag("exams", "Exams", "Экзамены", "work_study"),
    CuratedTag("business", "Business", "Бизнес", "work_study"),
    CuratedTag("finances", "Finances", "Финансы", "work_study"),
    CuratedTag("debt", "Debt", "Долги", "work_study"),
    CuratedTag("productivity", "Productivity", "Продуктивность", "work_study"),
    CuratedTag("side_project", "Side project", "Свой проект", "work_study"),

    // Ideas and questions
    CuratedTag("question", "Question", "Вопрос", "ideas"),
    CuratedTag("idea", "Idea", "Идея", "ideas"),
    CuratedTag("advice", "Advice", "Совет", "ideas"),
    CuratedTag("recommendation", "Recommendation", "Рекомендация", "ideas"),
    CuratedTag("discussion", "Discussion", "Обсуждение", "ideas"),
    CuratedTag("announcement", "Announcement", "Объявление", "ideas"),
    CuratedTag("feedback", "Feedback", "Отзыв", "ideas"),
    CuratedTag("poll", "Poll", "Опрос", "ideas"),
    CuratedTag("tip", "Tip", "Подсказка", "ideas"),
    CuratedTag("help_wanted", "Help wanted", "Нужна помощь", "ideas"),

    // Hobbies
    CuratedTag("books", "Books", "Книги", "hobbies"),
    CuratedTag("music", "Music", "Музыка", "hobbies"),
    CuratedTag("movies", "Movies", "Кино", "hobbies"),
    CuratedTag("games", "Games", "Игры", "hobbies"),
    CuratedTag("art", "Art", "Искусство", "hobbies"),
    CuratedTag("photography", "Photography", "Фотография", "hobbies"),
    CuratedTag("cooking", "Cooking", "Готовка", "hobbies"),
    CuratedTag("gardening", "Gardening", "Сад и огород", "hobbies"),
    CuratedTag("crafts", "Crafts", "Рукоделие", "hobbies"),
    CuratedTag("outdoors", "Outdoors", "На природе", "hobbies"),

    // Places and events
    CuratedTag("travel", "Travel", "Путешествия", "places"),
    CuratedTag("city", "Our city", "Наш город", "places"),
    CuratedTag("neighbourhood", "Neighbourhood", "Район", "places"),
    CuratedTag("moving", "Moving", "Переезд", "places"),
    CuratedTag("events", "Events", "События", "places"),
    CuratedTag("meetup", "Meetup", "Встреча", "places"),
    CuratedTag("transport", "Transport", "Транспорт", "places"),
    CuratedTag("weather", "Weather", "Погода", "places"),
    CuratedTag("local_news", "Local news", "Местные новости", "places"),
    CuratedTag("lost_and_found", "Lost and found", "Потеряно и найдено", "places"),

    // Tech
    CuratedTag("tech", "Tech", "Технологии", "tech"),
    CuratedTag("programming", "Programming", "Программирование", "tech"),
    CuratedTag("mobile", "Mobile", "Мобильное", "tech"),
    CuratedTag("web", "Web", "Веб", "tech"),
    CuratedTag("ai", "AI", "ИИ", "tech"),
    CuratedTag("gadgets", "Gadgets", "Гаджеты", "tech"),
    CuratedTag("security", "Security", "Безопасность", "tech"),
    CuratedTag("open_source", "Open source", "Open source", "tech"),
    CuratedTag("tutorial", "Tutorial", "Инструкция", "tech"),
    CuratedTag("release", "Release", "Релиз", "tech"),
)
