# Domain class types in Kotlin SDK

* **Type**: Design
* **Author(s)**: Ken Gilmer

# Abstract

This design explores the consequences of using various data types to represent input and output structures which
designated purpose is to hold data. (e.g., beans, domain classes, etc.). This document assumes basic familiarity with
the Smithy modeling language and the Kotlin language and standard library.

# Design

This design contrasts generalized classes and [Kotlin data classes](https://kotlinlang.org/docs/data-classes.html) as
implementations for model shapes.

## Initial state example

The Smithy [weather example's](https://awslabs.github.io/smithy/quickstart.html#complete-example) `GetCity` operation is
used as an example model description. The operation takes a `GetCityInput` and returns a `GetCityOutput`. We will focus
on the input type below to show how each approach is expressed and responds to change.

```
structure GetCityInput {
    // "cityId" provides the identifier for the resource and
    // has to be marked as required.
    @required
    cityId: CityId
}
```

### General class definition

```kotlin
class GetCityInput private constructor(builder: BuilderImpl) {
    val cityId: String? = builder.cityId

    companion object {
        operator fun invoke(block: DslBuilder.() -> Unit) = BuilderImpl().apply(block).build()
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    override fun toString(): String {
        return super.toString()
    }

    interface Builder {
        fun build(): GetCityImageInput
        // TODO - Java fill in Java builder
    }

    interface DslBuilder {
        var cityId: String?
    }

    private class BuilderImpl : Builder, DslBuilder {
        override var cityId: String? = null

        override fun build(): GetCityImageInput = GetCityImageInput(this)
    }
}
```

#### Usage

```kotlin
val unit = GetCityImageInput { cityId = "asdf" }
println(unit.cityId)
```

### Data class definition

```kotlin
data class GetCityInput private constructor(val cityId: String?) {
    companion object {
        operator fun invoke(block: DslBuilder.() -> Unit) = BuilderImpl().apply(block).build()
    }

    interface Builder {
        fun build(): GetCityImageInput
        // TODO - Java fill in Java builder
    }

    interface DslBuilder {
        var cityId: String?
    }

    private class BuilderImpl : Builder, DslBuilder {
        override var cityId: String? = null

        override fun build(): GetCityImageInput = GetCityImageInput(cityId = this.cityId)
    }
}
```

#### Usage

```kotlin
val unit = GetCityImageInput { cityId = "asdf" }
println(unit.cityId)
```

Note that usage is the exact same as the general class.

## Service API change example

Suppose the Weather Example service team decides they need to add a required parameter to specify the country code. They
produce this updated description of the `GetCityInput` structure:

```
structure GetCityInput {
    @required @httpLabel
    countryCode: CountryCode,

    @required @httpLabel
    cityId: CityId,
}
```

### Updated general class definition

```kotlin
class GetCityImageInput private constructor(builder: BuilderImpl) {
    val countryCode: String? = builder.countryCode
    val cityId: String? = builder.cityId

    companion object {
        operator fun invoke(block: DslBuilder.() -> Unit) = BuilderImpl().apply(block).build()
    }

    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    override fun toString(): String {
        return super.toString()
    }

    interface Builder {
        fun build(): GetCityImageInput
        // TODO - Java fill in Java builder
    }

    interface DslBuilder {
        var countryCode: String?
        var cityId: String?
    }

    private class BuilderImpl : Builder, DslBuilder {
        override var countryCode: String? = null
        override var cityId: String? = null

        override fun build(): GetCityImageInput = GetCityImageInput(this)
    }
}
```

Note the addition of new `countryCode` fields in the class and builders.

#### Usage

```kotlin
// Legacy
{
    val unit = GetCityImageInput { cityId = "asdf" }
    println(unit.cityId)
}

// New
{
    val unit = GetCityImageInput {
        cityId = "asdf"
        countryCode = "US"
    }
    println(unit.cityId)
}
```

### Updated data class definition

```kotlin
data class GetCityImageInput private constructor(
    val countryCode: String?,
    val cityId: String?
) {

    companion object {
        operator fun invoke(block: DslBuilder.() -> Unit) = BuilderImpl().apply(block).build()
    }

    interface Builder {
        fun build(): GetCityImageInput
        // TODO - Java fill in Java builder
    }

    interface DslBuilder {
        var countryCode: String?
        var cityId: String?
    }

    private class BuilderImpl : Builder, DslBuilder {
        override var cityId: String? = null
        override var countryCode: String? = null

        override fun build(): GetCityImageInput = GetCityImageInput(countryCode = this.countryCode, cityId = this.cityId)
    }
}
```

Note the addition of a new `countryCode` parameter to the constructor and `countryCode` fields in the builders.

#### Usage

```kotlin
// Legacy
{
    val unit = GetCityImageInput { cityId = "asdf" }
    println(unit.cityId)
}

// New
{
    val unit = GetCityImageInput {
        cityId = "asdf"
        countryCode = "US"
    }
    println(unit.cityId)
}
```

## Comparisons

Based on the above code, the upsides of the data class approach are:

1. The data class generated type code is more concise, making it easier to read and understand the generated code
   quickly.
1. The data class generated type code provides more functionality automatically so there's less to implement and test in
   codegen.
1. The data class provides a strong hint that the purpose of the class is solely to hold data.
1. The behavior of the built-in functions of data classes (e.g., `hashCode`, `toString`, etc.) behave consistently with
   all other data class implementations customers may use outside the scope of the Kotlin SDK.

Downsides of data class over general classes:

1. Data classes provide built-in functionality for common domain-type operations, such as creating copies. In Kotlin,
   the built-in copy operation exposes the private constructor. Customers may use object construction via the copy
   operation to specify value overrides for specific fields, but because the constructor is directly exposed, they may
   specify values *positionally rather than by name*. This could cause "invisible" (i.e., without compile-time
   validation) bugs in customer code as the order and number of required constructor fields change during an update to
   the type definition. It does not appear there is a way to sidestep this issue based on the [Kotlin language
   documentation](https://kotlinlang.org/docs/reference/data-classes.html):
   > Providing explicit implementations for the componentN() and copy() functions is not allowed.
1. Due to their built-in behavior and (at least) how JVM collection behavior is coupled to hashCode/equals
   implementations, choosing data classes may be a one-way door as it could result in unresolvable variance in behavior
   if the Kotlin changes makes some fundamental changes to how data classes behave that we do not wish to take into our
   SDK.

   For example, if different versions of Kotlin provide different behaviors for data classes, we will have to deal with
   that variance in our SDKs as well as we cannot control language or stdlib versions that customers use. General
   classes provide us complete control. Additionally, a hypothetical situation in which we would need to migrate from
   data classes to general classes for input/output types would likely result in unspecified variations in customer
   programs. This becomes a problem mainly if data class behavior varies over Kotlin language or stlib versions.

## Conclusion

Tenet #1 of the AWS SDK and Tools team states:

> We ensure accessing AWS services is performant, secure, and reliable for developers. Developers can depend on our
  code, and we will support the success of our service teams.

Due to the construction issues of the exposed copy constructor feature of data classes, they are a poorer choice
relative to general classes, specifically due to *reliability* of customer interaction with those types as they evolve
across service versions.

# Revision history

* 6/1/2021 - Initial upload
