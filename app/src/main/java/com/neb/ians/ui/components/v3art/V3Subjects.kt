package com.neb.ians.ui.components.v3art

/** Generated from concepts/art-v3/v3-thumb.js (A.thumbTables). Same tables as js/v3-thumb.js. */
internal object V3Subjects {
    class Sub(val palette: String, val objects: List<String>)

    val SUB: Map<String, Sub> = mapOf(
        "Physics" to Sub("brand", listOf("atom", "magnet", "telescope")),
        "Chemistry" to Sub("aqua", listOf("flask", "atom", "leaf")),
        "Biology" to Sub("emerald", listOf("dna", "sprout", "leaf")),
        "Mathematics" to Sub("cobalt", listOf("compass", "abacus", "chart")),
        "Computer Science" to Sub("sky", listOf("laptop", "gear", "bulb")),
        "English" to Sub("coral", listOf("books", "pencil", "notebook")),
        "Nepali" to Sub("rose", listOf("pencil", "notebook", "pagoda")),
        "Accountancy" to Sub("teal", listOf("calculator", "chart", "abacus")),
        "Economics" to Sub("amber", listOf("chart", "scales", "calculator")),
        "Business Studies" to Sub("marigold", listOf("chart", "backpack", "bulb")),
        "Social Studies" to Sub("sky", listOf("globe", "books", "pagoda")),
        "Geography" to Sub("moss", listOf("globe", "compass", "summit")),
        "History" to Sub("terracotta", listOf("stupa", "pagoda", "doc")),
        "Environment" to Sub("mint", listOf("leaf", "sprout", "plant")),
        "Hotel Management" to Sub("marigold", listOf("bell", "notebook", "plant")),
        "Law" to Sub("slate", listOf("scales", "shield", "doc")),
        "Political Science" to Sub("brand", listOf("scales", "doc", "globe")),
        "Sociology" to Sub("sky", listOf("globe", "backpack", "books")),
        "Psychology" to Sub("rose", listOf("bulb", "pulse", "notebook")),
        "Education" to Sub("denim", listOf("cap", "books", "backpack")),
        "Music" to Sub("coral", listOf("music", "bell", "palette")),
        "Health & Nursing" to Sub("crimson", listOf("pulse", "flask", "shield")),
        "Statistics" to Sub("teal", listOf("chart", "calculator", "compass")),
        "Engineering" to Sub("terracotta", listOf("gear", "compass", "laptop")),
        "Agriculture" to Sub("lime", listOf("sprout", "plant", "leaf")),
        "Space & Astronomy" to Sub("cobalt", listOf("rocket", "planet", "telescope")),
        "BCA & Software" to Sub("brand", listOf("laptop", "gear", "bulb")),
        "Medicine & Entrance" to Sub("emerald", listOf("pulse", "flask", "dna")),
        "Architecture" to Sub("amber", listOf("pagoda", "compass", "gear")),
        "Nepal Heritage" to Sub("sand", listOf("stupa", "pagoda", "summit")),
        "Art & Design" to Sub("coral", listOf("palette", "pencil", "plane")),
        "Career Pathways" to Sub("pine", listOf("plane", "summit", "backpack")),
        "Study Skills" to Sub("denim", listOf("clock", "notebook", "bulb")),
        "Exam Prep" to Sub("marigold", listOf("trophy", "clock", "pencil"))
    )

    class Kw(val pool: List<String>, val words: List<String>)

    val KW: List<Kw> = listOf(
        Kw(listOf("Health & Nursing"), listOf("health", "nursing", "physical education", "phy. edu", "hpe", "nutrition", "yoga", "sport", "fitness", "medical", "स्वास्थ्य")),
        Kw(listOf("Medicine & Entrance"), listOf("medicine", "mbbs", "entrance", "pharmac")),
        Kw(listOf("Physics"), listOf("physics", "भौतिक")),
        Kw(listOf("Chemistry"), listOf("chemistry", "chemical", "रसायन")),
        Kw(listOf("Biology"), listOf("biology", "botany", "zoology", "microbio", "anatomy", "genetic", "जीव")),
        Kw(listOf("Statistics"), listOf("statistic")),
        Kw(listOf("Mathematics"), listOf("math", "algebra", "geometry", "trigonometry", "calculus", "गणित")),
        Kw(listOf("BCA & Software"), listOf("bca", "software")),
        Kw(listOf("Computer Science"), listOf("computer", "programming", "informatics", "information tech", "digital", "coding", "algorithm", "database", "web dev")),
        Kw(listOf("Engineering"), listOf("engineering")),
        Kw(listOf("Architecture"), listOf("architect")),
        Kw(listOf("Agriculture"), listOf("agricultur", "farm")),
        Kw(listOf("Space & Astronomy"), listOf("astronom", "space")),
        Kw(listOf("Accountancy"), listOf("account", "book keeping", "bookkeeping", "audit")),
        Kw(listOf("Economics"), listOf("economic", "अर्थ")),
        Kw(listOf("Business Studies"), listOf("business", "finance", "commerce", "marketing", "banking", "management")),
        Kw(listOf("Hotel Management"), listOf("hotel", "hospitality", "tourism")),
        Kw(listOf("Geography"), listOf("geograph", "population")),
        Kw(listOf("History"), listOf("history")),
        Kw(listOf("Political Science"), listOf("political")),
        Kw(listOf("Sociology"), listOf("sociolog")),
        Kw(listOf("Psychology"), listOf("psycholog")),
        Kw(listOf("Social Studies"), listOf("social", "civic", "culture", "सामाजिक", "अध्ययन")),
        Kw(listOf("Environment"), listOf("environment", "ecolog")),
        Kw(listOf("Law"), listOf("legal", "constitution", "jurisprudence")),
        Kw(listOf("Music"), listOf("music")),
        Kw(listOf("Art & Design"), listOf("design", "drawing", "painting")),
        Kw(listOf("Nepali"), listOf("nepali", "sanskrit", "hindi", "maithili", "newari", "नेपाली")),
        Kw(listOf("English"), listOf("english", "grammar", "literature", "writing", "अंग्रेजी", "साहित्य")),
        Kw(listOf("Education"), listOf("education", "teaching", "pedagog")),
        Kw(listOf("Physics", "Chemistry", "Biology"), listOf("science", "laboratory", "विज्ञान")),
        Kw(listOf("Exam Prep"), listOf("exam", "model paper", "model set", "past paper", "question bank", "mock", "revision", "pyq")),
        Kw(listOf("Study Skills"), listOf("study", "skill", "tips"))
    )

    val GENERIC = listOf("Education", "Study Skills", "Exam Prep", "English", "Mathematics", "Social Studies")
    val FILL = listOf("books", "notebook", "bulb", "pencil", "clock", "chart")
    val STYLES = listOf("disc", "plate", "arch", "pattern", "disc", "plate")
    val NO_OBJ = setOf("pagoda", "stupa", "summit")
}
