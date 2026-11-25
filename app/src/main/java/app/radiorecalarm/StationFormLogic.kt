package app.radiorecalarm

import java.util.Locale

/**
 * Puhtad abifunktsioonid jaama lisamise ja muutmise vormi valideerimiseks,
 * URL-ide normaliseerimiseks ning riikide ja alamgruppide sorteerimiseks.
 */
object StationFormLogic {

    fun isValidStreamUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return false
        val lower = trimmed.lowercase(Locale.ROOT)
        val afterScheme = when {
            lower.startsWith("https://") -> trimmed.substring(8)
            lower.startsWith("http://") -> trimmed.substring(7)
            else -> return false
        }
        if (afterScheme.isEmpty()) return false
        val authority = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }
        if (authority.isEmpty()) return false
        val host = authority.substringBefore(':')
        return host.isNotBlank()
    }

    fun shouldShowMissingSchemeQuickFix(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.length < 3 || trimmed.any { it.isWhitespace() }) return false
        val lower = trimmed.lowercase(Locale.ROOT)
        return !lower.startsWith("http://") && !lower.startsWith("https://")
    }

    fun ensureHttpsPrefix(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        val lower = trimmed.lowercase(Locale.ROOT)
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return trimmed
        }
        val cleaned = trimmed.removePrefix("://").trimStart('/')
        return if (cleaned.isEmpty()) "" else "https://$cleaned"
    }

    fun normalizeStreamUrlForComparison(url: String): String {
        return url.trim().trimEnd('/').lowercase(Locale.ROOT)
    }

    fun findDuplicateStationByUrl(
        url: String,
        existingStations: List<RadioStation>,
        editingStationId: Int? = null
    ): RadioStation? {
        if (!isValidStreamUrl(url)) return null
        val target = normalizeStreamUrlForComparison(url)
        return existingStations.firstOrNull { station ->
            station.id != editingStationId &&
                normalizeStreamUrlForComparison(station.url) == target
        }
    }

    fun buildCountryPickerItems(
        remoteCountries: List<RadioFilterItem>,
        existingStations: List<RadioStation>,
        includeAllIsoFallback: Boolean = true,
        displayNameForCode: (String) -> String = ::getCachedCountryDisplayName
    ): List<RadioFilterItem> {
        val remoteByCode = linkedMapOf<String, RadioFilterItem>()
        remoteCountries.forEach { item ->
            val code = item.isoCode?.trim()?.uppercase(Locale.ROOT).orEmpty()
            if (code.length == 2 && code != "RU" && !remoteByCode.containsKey(code)) {
                remoteByCode[code] = item
            }
        }

        val localCounts = existingStations
            .map { it.countryCode.trim().uppercase(Locale.ROOT) }
            .filter { it.length == 2 }
            .groupingBy { it }
            .eachCount()

        val allCodes = linkedSetOf<String>()
        allCodes.addAll(remoteByCode.keys)
        allCodes.addAll(localCounts.keys)
        if (includeAllIsoFallback) {
            Locale.getISOCountries()
                .map { it.uppercase(Locale.ROOT) }
                .filter { it.length == 2 && it != "RU" }
                .forEach { allCodes.add(it) }
        }

        val preferredOrder = AppConfig.UI.PREFERRED_COUNTRY_ORDER
        return allCodes.map { code ->
            val remote = remoteByCode[code]
            val localizedName = displayNameForCode(code).trim()
            val resolvedName = localizedName.ifBlank { remote?.name?.trim().orEmpty().ifBlank { code } }
            val count = remote?.stationCount ?: (localCounts[code] ?: 0)
            RadioFilterItem(
                name = resolvedName,
                stationCount = count,
                isoCode = code
            )
        }.sortedWith(
            compareBy<RadioFilterItem> { item ->
                val idx = preferredOrder.indexOf(item.isoCode)
                if (idx != -1) idx else Int.MAX_VALUE
            }.thenBy { item ->
                if (localCounts.containsKey(item.isoCode)) 0 else 1
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        )
    }

    fun sortSubgroupsForCountry(
        allSubgroups: List<String>,
        countryCode: String,
        existingStations: List<RadioStation>
    ): List<String> {
        val normalizedCountry = countryCode.trim().uppercase(Locale.ROOT)
        val countrySpecific = if (normalizedCountry.isNotEmpty()) {
            val raw = existingStations
                .filter { station ->
                    station.countryCode.trim().uppercase(Locale.ROOT) == normalizedCountry &&
                        !(station.isUserStation && station.category.equals(StationListFilter.CATEGORY_MY, ignoreCase = true))
                }
                .map { it.category.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            val customOrder = if (normalizedCountry == "EE") AppConfig.UI.ESTONIAN_SUB_CATEGORY_ORDER else emptyList()
            raw.sortedWith(
                compareBy<String> { sub ->
                    val idx = customOrder.indexOf(sub)
                    if (idx != -1) idx else Int.MAX_VALUE
                }.thenBy(String.CASE_INSENSITIVE_ORDER) { it }
            )
        } else {
            emptyList()
        }

        val seen = countrySpecific.map { it.lowercase(Locale.ROOT) }.toMutableSet()
        val remaining = allSubgroups
            .map { it.trim() }
            .filter {
                it.isNotBlank() &&
                    !it.equals(StationListFilter.CATEGORY_MY, ignoreCase = true) &&
                    seen.add(it.lowercase(Locale.ROOT))
            }
            .sortedWith(String.CASE_INSENSITIVE_ORDER)

        return countrySpecific + remaining
    }

    fun filterSubgroups(sortedSubgroups: List<String>, query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return sortedSubgroups
        return sortedSubgroups.filter { it.contains(trimmed, ignoreCase = true) }
    }
}
