package app.morrow.domain

import kotlinx.datetime.TimeZone

/**
 * The time zone for an IANA [id], under its old or new name if this phone only knows the other one. Older Android
 * versions ship time zone data from before renames such as Europe/Kiev → Europe/Kyiv, and the city list uses the new
 * names. Null when the phone knows the zone under no name.
 */
fun timeZoneOrNull(id: String): TimeZone? =
    sequenceOf(id, ZoneRenames[id], OldZoneNames[id], NewZoneStandIns[id])
        .filterNotNull()
        .firstNotNullOfOrNull { runCatching { TimeZone.of(it) }.getOrNull() }

/** Old zone names some phones still report, and the names the city list uses for them. */
val ZoneRenames: Map<String, String> = mapOf(
    "Africa/Asmera" to "Africa/Asmara",
    "America/Buenos_Aires" to "America/Argentina/Buenos_Aires",
    "America/Godthab" to "America/Nuuk",
    "America/Indianapolis" to "America/Indiana/Indianapolis",
    "America/Montreal" to "America/Toronto",
    "Asia/Calcutta" to "Asia/Kolkata",
    "Asia/Dacca" to "Asia/Dhaka",
    "Asia/Katmandu" to "Asia/Kathmandu",
    "Asia/Rangoon" to "Asia/Yangon",
    "Asia/Saigon" to "Asia/Ho_Chi_Minh",
    "Asia/Ulan_Bator" to "Asia/Ulaanbaatar",
    "Atlantic/Faeroe" to "Atlantic/Faroe",
    "Europe/Kiev" to "Europe/Kyiv",
    "Pacific/Ponape" to "Pacific/Pohnpei",
    "Pacific/Truk" to "Pacific/Chuuk",
)

private val OldZoneNames: Map<String, String> = ZoneRenames.entries.associate { (old, new) -> new to old }

/** Zones too new to have an old name, and an older zone that keeps the same clock today. */
private val NewZoneStandIns = mapOf(
    "America/Ciudad_Juarez" to "America/Denver",
)
