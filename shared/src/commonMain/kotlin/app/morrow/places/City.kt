package app.morrow.places

/** A place with its own clock. [id] is the GeoNames id, shared by the bundled list and the online geocoder. */
data class City(
    val id: Long,
    val name: String,
    /** The state, province or similar, when GeoNames has one. */
    val region: String?,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    /** An IANA zone id, such as "Europe/London". */
    val timeZone: String,
    val population: Long,
)

/** A city on the World tab. Exactly one saved city is home, once there are any. */
data class SavedCity(val city: City, val isHome: Boolean) {
    val id: Long get() = city.id
}
