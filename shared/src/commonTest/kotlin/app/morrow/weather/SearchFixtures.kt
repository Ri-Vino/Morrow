package app.morrow.weather

// Written from Open-Meteo's documented geocoding response shape, not recorded from the live API.

/** A search for "lon": London, which the bundled list has too, London in Ontario, and a hamlet with no population. */
internal val LonSearchJson = """
    {
      "results": [
        {
          "id": 2643743, "name": "London", "latitude": 51.50853, "longitude": -0.12574, "feature_code": "PPLC",
          "country_code": "GB", "timezone": "Europe/London", "population": 8961989, "country": "United Kingdom", "admin1": "England"
        },
        {
          "id": 6058560, "name": "London", "latitude": 42.98339, "longitude": -81.23304, "feature_code": "PPL",
          "country_code": "CA", "timezone": "America/Toronto", "population": 346765, "country": "Canada", "admin1": "Ontario"
        },
        {
          "id": 2644209, "name": "Little London", "latitude": 51.33, "longitude": -1.16, "feature_code": "PPL",
          "country_code": "GB", "timezone": "Europe/London", "country": "United Kingdom", "admin1": "England"
        }
      ],
      "generationtime_ms": 0.5
    }
""".trimIndent()

/** A search for "hebden": a small town the bundled list doesn't have. */
internal val HebdenSearchJson = """
    {
      "results": [
        {
          "id": 2647057, "name": "Hebden Bridge", "latitude": 53.74233, "longitude": -2.01174, "feature_code": "PPL",
          "country_code": "GB", "timezone": "Europe/London", "population": 4086, "country": "United Kingdom", "admin1": "England"
        }
      ],
      "generationtime_ms": 0.4
    }
""".trimIndent()
