package ee.minu.kellraadio.ui

// Avalik funktsioon, mida saavad kasutada kõik UI failid
fun getFlagEmoji(countryCode: String): String {
    if (countryCode.length != 2) return ""
    val firstLetter = Character.codePointAt(countryCode.uppercase(), 0) - 0x41 + 0x1F1E6
    val secondLetter = Character.codePointAt(countryCode.uppercase(), 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(firstLetter)) + String(Character.toChars(secondLetter))
}