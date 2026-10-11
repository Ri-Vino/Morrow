"""Makes the city list Morrow ships, for the World tab and city search without the network.

Download cities15000.zip, admin1CodesASCII.txt and countryInfo.txt from https://download.geonames.org/export/dump/
into one folder (outside the repo; they aren't committed), then run from the repo root:

    python tools/make_cities.py <folder with the GeoNames files>

Writes shared/src/commonMain/composeResources/files/cities.tsv: UTF-8, no header, one city per line, with the
tab-separated columns

    id  name  asciiName  region  country  latitude  longitude  zone  population

where id is the GeoNames id, asciiName is empty when it's the same as name, region is the admin1 name (empty when
GeoNames has none), country is the country's English name, latitude and longitude have 3 decimals (about 100 m)
and zone is an IANA time zone id. Lines are grouped by country and region, most populous first within each,
because that packs about a quarter smaller in the APK than one list by population; the app ranks by population
itself. Needs only the standard library.

City data © GeoNames (https://www.geonames.org), CC BY 4.0.
"""

import sys
import zipfile
from pathlib import Path

OUT = Path(__file__).resolve().parent.parent / "shared" / "src" / "commonMain" / "composeResources" / "files" / "cities.tsv"


def clean(text):
    return " ".join(text.replace("\t", " ").replace("\r", " ").replace("\n", " ").split())


def read_lines(path):
    with open(path, encoding="utf-8") as file:
        for line in file:
            if line.strip() and not line.startswith("#"):
                yield line.rstrip("\n").split("\t")


def countries(folder):
    return {columns[0]: clean(columns[4]) for columns in read_lines(folder / "countryInfo.txt")}


def regions(folder):
    return {columns[0]: clean(columns[1]) for columns in read_lines(folder / "admin1CodesASCII.txt")}


def cities(folder):
    with zipfile.ZipFile(folder / "cities15000.zip") as archive:
        with archive.open("cities15000.txt") as file:
            for raw in file:
                yield raw.decode("utf-8").rstrip("\n").split("\t")


def main():
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    folder = Path(sys.argv[1])
    country_names = countries(folder)
    region_names = regions(folder)

    rows = []
    for columns in cities(folder):
        geoname_id, name, ascii_name = columns[0], clean(columns[1]), clean(columns[2])
        latitude, longitude = float(columns[4]), float(columns[5])
        country_code, admin1 = columns[8], columns[10]
        population, zone = int(columns[14] or 0), columns[17].strip()
        country = country_names.get(country_code)
        if not name or not zone or not country:
            continue
        region = region_names.get(f"{country_code}.{admin1}", "")
        rows.append((
            (country, region, -population, int(geoname_id)),
            "\t".join([
                geoname_id,
                name,
                "" if ascii_name == name else ascii_name,
                region,
                country,
                f"{latitude:.3f}",
                f"{longitude:.3f}",
                zone,
                str(population),
            ]),
        ))
    rows.sort()

    OUT.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as file:
        for _, line in rows:
            file.write(line + "\n")
    print(f"Wrote {len(rows)} cities, {OUT.stat().st_size} bytes, to {OUT}")


if __name__ == "__main__":
    main()
