package app.morrow.ui.firstrun

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import app.morrow.data.MorrowSettings
import app.morrow.places.City
import app.morrow.places.CityRepository
import app.morrow.places.CitySearch
import app.morrow.ui.world.AddCityRoute
import app.morrow.ui.world.CityPickPurpose
import app.morrow.weather.WeatherPlaceMode
import kotlinx.coroutines.launch

/**
 * First run's last step, "Where's home?": the Add a city search, whose pick is saved as the home city and makes Home's
 * weather follow it. [onFinished] hears once a city has been picked, or after Skip, which leaves no home city, and ends first run.
 * [onWeatherChanged] hears that the weather's place has changed, to fetch it; [onPicked] hears about the city first.
 * Back goes [onBack] to the step before.
 */
@Composable
fun HomeTownRoute(
    search: CitySearch,
    cities: CityRepository,
    settings: MorrowSettings,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    onPicked: (City) -> Unit = {},
    onWeatherChanged: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    AddCityRoute(
        search, cities,
        onBack = onBack,
        modifier = modifier,
        purpose = CityPickPurpose.HOME,
        onPicked = onPicked,
        firstRun = true,
        onSkip = {
            scope.launch {
                // The phone's own city, saved as home when the app first started, isn't one the user chose.
                cities.clearHome()
                onFinished()
            }
        },
        onChosen = {
            scope.launch {
                settings.setWeatherSource(WeatherPlaceMode.HOME)
                onWeatherChanged()
                onFinished()
            }
        },
    )
}
