package app.morrow.places

import app.cash.turbine.test
import app.morrow.data.FakeSavedCityDao
import app.morrow.data.toEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CityRepositoryTest {

    private val catalog = CityCatalog {
        """
        2643743	London		England	United Kingdom	51.5085	-0.1257	Europe/London	8961989
        2655603	Birmingham		England	United Kingdom	52.4814	-1.8998	Europe/London	984333
        5128581	New York City		New York	United States	40.7143	-74.0060	America/New_York	8804190
        1850147	Tokyo		Tokyo	Japan	35.6895	139.6917	Asia/Tokyo	9733276
        """.trimIndent()
    }
    private val dao = FakeSavedCityDao()
    private var phoneZone = "Europe/London"
    private val repository = CityRepository(dao, catalog) { phoneZone }

    private val london = City(2643743, "London", "England", "United Kingdom", 51.5085, -0.1257, "Europe/London", 8961989)
    private val newYork = City(5128581, "New York City", "New York", "United States", 40.7143, -74.0060, "America/New_York", 8804190)
    private val tokyo = City(1850147, "Tokyo", "Tokyo", "Japan", 35.6895, 139.6917, "Asia/Tokyo", 9733276)

    // Not in the bundled list, as if from the online geocoder.
    private val tromso = City(3133880, "Tromsø", "Troms", "Norway", 69.6496, 18.957, "Europe/Oslo", 41915)

    @Test
    fun seedsThePhonesCityAsHome() = runTest {
        repository.ensureSeeded()
        assertEquals(listOf(SavedCity(london, isHome = true)), repository.cities.first())
        assertEquals(SavedCity(london, isHome = true), repository.home())
    }

    @Test
    fun seedsOnlyWhenThereAreNoCities() = runTest {
        repository.add(tokyo)
        repository.ensureSeeded()
        assertEquals(listOf(tokyo), repository.cities.first().map { it.city })

        // Seeding twice doesn't add London twice.
        val fresh = CityRepository(FakeSavedCityDao(), catalog) { "Europe/London" }
        fresh.ensureSeeded()
        fresh.ensureSeeded()
        assertEquals(listOf(london), fresh.cities.first().map { it.city })
    }

    @Test
    fun seedsNothingForAZoneWithoutACity() = runTest {
        phoneZone = "UTC"
        repository.ensureSeeded()
        assertEquals(emptyList(), repository.cities.first())
        assertNull(repository.home())
    }

    @Test
    fun addsAtTheEndOnce() = runTest {
        repository.ensureSeeded()
        repository.add(newYork)
        repository.add(tromso)
        repository.add(newYork)
        assertEquals(
            listOf(SavedCity(london, true), SavedCity(newYork, false), SavedCity(tromso, false)),
            repository.cities.first(),
        )
    }

    @Test
    fun aCityAddedIsNeverHomeUntilMadeSo() = runTest {
        phoneZone = "UTC"
        repository.ensureSeeded()
        repository.add(tokyo)
        repository.add(newYork)
        assertNull(repository.home())
        repository.makeHome(tokyo.id)
        assertEquals(tokyo, repository.home()?.city)
    }

    @Test
    fun aCityAddedAfterClearingHomeStaysNotHome() = runTest {
        repository.ensureSeeded()
        repository.clearHome()
        repository.add(tokyo)
        assertNull(repository.home())
        assertEquals(listOf(london, tokyo), repository.cities.first().map { it.city })
    }

    @Test
    fun clearHomeLeavesEveryCitySavedButNoneHome() = runTest {
        repository.ensureSeeded()
        repository.add(newYork)
        repository.clearHome()
        assertNull(repository.home())
        assertEquals(listOf(london, newYork), repository.cities.first().map { it.city })
        // Nothing to clear is fine, and a city can be made home again.
        repository.clearHome()
        repository.makeHome(newYork.id)
        assertEquals(newYork, repository.home()?.city)
    }

    @Test
    fun removesAnyCityButHome() = runTest {
        repository.ensureSeeded()
        repository.add(newYork)
        repository.remove(london.id)
        repository.remove(newYork.id)
        repository.remove(42)
        assertEquals(listOf(london), repository.cities.first().map { it.city })
    }

    @Test
    fun makeHomeMovesTheFlag() = runTest {
        repository.ensureSeeded()
        repository.add(newYork)
        repository.makeHome(newYork.id)
        // The new home goes to the top.
        assertEquals(listOf(SavedCity(newYork, true), SavedCity(london, false)), repository.cities.first())
        assertEquals(newYork, repository.home()?.city)

        // The old home can go now.
        repository.remove(london.id)
        assertEquals(listOf(newYork), repository.cities.first().map { it.city })

        // A city that isn't saved can't be home.
        repository.makeHome(tokyo.id)
        assertEquals(newYork, repository.home()?.city)
    }

    @Test
    fun moveReordersBelowHomeAndStopsAtTheEnds() = runTest {
        repository.ensureSeeded()
        repository.add(newYork)
        repository.add(tokyo)
        repository.add(tromso)

        repository.move(tokyo.id, -1)
        assertEquals(listOf(london, tokyo, newYork, tromso), repository.cities.first().map { it.city })
        // Nothing goes above home, and home itself stays put.
        repository.move(tokyo.id, -5)
        repository.move(london.id, 2)
        assertEquals(listOf(london, tokyo, newYork, tromso), repository.cities.first().map { it.city })
        repository.move(tromso.id, -10)
        assertEquals(listOf(london, tromso, tokyo, newYork), repository.cities.first().map { it.city })
        repository.move(tromso.id, 10)
        assertEquals(listOf(london, tokyo, newYork, tromso), repository.cities.first().map { it.city })
        repository.move(42, 1)
        assertEquals(listOf(london, tokyo, newYork, tromso), repository.cities.first().map { it.city })
    }

    @Test
    fun aHomeSavedLowerDownStillComesFirst() = runTest {
        // As an older version could leave it, after making a later city home.
        dao.upsert(
            listOf(
                london.toEntity(isHome = false, position = 0),
                tokyo.toEntity(isHome = true, position = 1),
                newYork.toEntity(isHome = false, position = 2),
            ),
        )
        assertEquals(listOf(tokyo, london, newYork), repository.cities.first().map { it.city })

        repository.move(newYork.id, -1)
        assertEquals(listOf(tokyo, newYork, london), repository.cities.first().map { it.city })
    }

    @Test
    fun addAfterRemovingStillAppends() = runTest {
        repository.ensureSeeded()
        repository.add(newYork)
        repository.add(tokyo)
        repository.remove(newYork.id)
        repository.add(tromso)
        repository.add(newYork)
        assertEquals(listOf(london, tokyo, tromso, newYork), repository.cities.first().map { it.city })
    }

    @Test
    fun citiesFollowChanges() = runTest {
        repository.cities.test {
            assertEquals(emptyList(), awaitItem())
            repository.ensureSeeded()
            assertEquals(listOf(london), awaitItem().map { it.city })
            repository.add(tokyo)
            assertEquals(listOf(london, tokyo), awaitItem().map { it.city })
        }
    }
}
