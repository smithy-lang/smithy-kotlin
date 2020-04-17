package com.xyzcorp.services.weather.model

import com.xyzcorp.services.weather.model.nested.more.Baz

class OtherStructure

enum class SimpleYesNo(val value: String) {
    YES("YES"),
    NO("NO")
}

enum class TypedYesNo(val value: String) {
    YES("Yes"),
    NO("No")
}

sealed class Precipitation

data class Rain(val value: Boolean) : Precipitation()

data class Sleet(val value: Boolean) : Precipitation()

data class Hail(val value: Map<String, String>) : Precipitation()

data class Snow(val value: SimpleYesNo) : Precipitation()

data class Mixed(val value: TypedYesNo) : Precipitation()

data class Other(val value: OtherStructure) : Precipitation()

data class Blob(val value: ByteArray) : Precipitation()

// FIXME - should we generate a nested package for nested namespaces
data class Baz(val value: Baz) : Precipitation()
