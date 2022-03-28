$version: "1.0"

namespace aws.benchmarks.countries_states

use aws.benchmarks.protocols#serdeBenchmarkXml

@serdeBenchmarkXml
service CountriesStatesService {
    version: "2019-12-16",
    operations: [GetCountriesAndStates]
}

@http(uri: "/GetCountriesAndStates", method: "POST")
operation GetCountriesAndStates {
    input: GetCountriesAndStatesRequest,
    output: GetCountriesAndStatesResponse
}

structure GetCountriesAndStatesRequest {
    countries_states: CountriesAndStates
}

structure GetCountriesAndStatesResponse {
    countries_states: CountriesAndStates
}

structure CountriesAndStates {
    @xmlFlattened country_state: CountriesStates
}

list CountriesStates {
    member: CountryState
}

structure CountryState {
    name: String,
    iso2: String,
    iso3: String,
    numeric_code: String,
    phone_code: String,
    capital: String,
    currency: String,
    currency_name: String,
    currency_symbol: String,
    tld: String,
    native: String,
    region: String,
    subregion: String,
    @xmlFlattened timezones: TimeZones,
    translations: Translations,
    latitude: Double,
    longitude: Double,
    emoji: String,
    emojiU: String,
    @xmlFlattened states: States
}

structure State {
    id: Integer,
    name: String,
    state_code: String,
    latitude: Double,
    longitude: Double,
    type: String
}

list States {
    member: State
}

structure TimeZone {
    zoneName: String,
    gmtOffset: Integer,
    gmtOffsetName: String,
    abbreviation: String,
    tzName: String
}

list TimeZones {
    member: TimeZone
}

structure Translations {
    br: String,
    cn: String,
    de: String,
    es: String,
    fa: String,
    fr: String,
    hr: String,
    it: String,
    ja: String,
    kr: String,
    nl: String,
    pt: String
}
