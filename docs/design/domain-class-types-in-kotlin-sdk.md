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
on the input type below to show how normal and data classes would be generated and respond to model updates.

```
structure GetCityInput {
    // "cityId" provides the identifier for the resource and
    // has to be marked as required.
    @required
    cityId: CityId
}
```

### Option - Class Sample

NOTE: Some ancillary generated functions have been removed from the following snippets to keep focus.

```kotlin
class GetCityRequest private constructor(builder: BuilderImpl) {
   val cityId: String? = builder.cityId

   companion object {
      @JvmStatic
      fun fluentBuilder(): FluentBuilder = BuilderImpl()

      fun builder(): DslBuilder = BuilderImpl()

      operator fun invoke(block: DslBuilder.() -> kotlin.Unit): GetCityRequest = BuilderImpl().apply(block).build()

   }

   fun copy(block: DslBuilder.() -> kotlin.Unit = {}): GetCityRequest = BuilderImpl(this).apply(block).build()

   interface FluentBuilder {
      fun build(): GetCityRequest
      fun cityId(cityId: String): FluentBuilder
   }

   interface DslBuilder {
      var cityId: String?

      fun build(): GetCityRequest
   }

   private class BuilderImpl() : FluentBuilder, DslBuilder {
      override var cityId: String? = null

      constructor(x: GetCityRequest) : this() {
         this.cityId = x.cityId
      }

      override fun build(): GetCityRequest = GetCityRequest(this)
      override fun cityId(cityId: String): FluentBuilder = apply { this.cityId = cityId }
   }
}
```

#### Usage

```kotlin
val request = GetCityRequest { cityId = "Shimabara" }
```

### Option - Data Class Sample

```kotlin
data class GetCityRequest private constructor(val cityId: String?) {

   companion object {
      @JvmStatic
      fun fluentBuilder(): FluentBuilder = BuilderImpl()

      fun builder(): DslBuilder = BuilderImpl()

      operator fun invoke(block: DslBuilder.() -> kotlin.Unit): GetCityRequest = BuilderImpl().apply(block).build()

   }

   interface FluentBuilder {
      fun build(): GetCityRequest
      fun cityId(cityId: String): FluentBuilder
   }

   interface DslBuilder {
      var cityId: String?

      fun build(): GetCityRequest
   }

   private class BuilderImpl() : FluentBuilder, DslBuilder {
      override var cityId: String? = null

      constructor(x: GetCityRequest) : this() {
         this.cityId = x.cityId
      }

      override fun build(): GetCityRequest = GetCityRequest(cityId = this.cityId)
      override fun cityId(cityId: String): FluentBuilder = apply { this.cityId = cityId }
   }
}
```

#### Usage

```kotlin
val request = GetCityRequest { cityId = "Kuchinotsu" }
```

Note that usage is the exact same as the general class.

## Service API Change

Suppose the Weather Example service team decides they need to add another parameter to specify the country code. They
produce this updated version of the `GetCityInput` structure:

```
string CountryCode

structure GetCityInput {
  @required @httpLabel
  countryCode: CountryCode,

  @required cityId: CityId
}
```

### Updated Class Sample

```kotlin
class GetCityRequest private constructor(builder: BuilderImpl) {
   val cityId: String? = builder.cityId
   val countryCode: String? = builder.countryCode

   companion object {
      @JvmStatic
      fun fluentBuilder(): FluentBuilder = BuilderImpl()

      fun builder(): DslBuilder = BuilderImpl()

      operator fun invoke(block: DslBuilder.() -> kotlin.Unit): GetCityRequest = BuilderImpl().apply(block).build()

   }

   fun copy(block: DslBuilder.() -> kotlin.Unit = {}): GetCityRequest = BuilderImpl(this).apply(block).build()

   interface FluentBuilder {
      fun build(): GetCityRequest
      fun cityId(cityId: String): FluentBuilder
      fun countryCode(countryCode: String): FluentBuilder
   }

   interface DslBuilder {
      var cityId: String?
      var countryCode: String?

      fun build(): GetCityRequest
   }

   private class BuilderImpl() : FluentBuilder, DslBuilder {
      override var cityId: String? = null
      override var countryCode: String? = null

      constructor(x: GetCityRequest) : this() {
         this.cityId = x.cityId
         this.countryCode = x.countryCode
      }

      override fun build(): GetCityRequest = GetCityRequest(this)
      override fun cityId(cityId: String): FluentBuilder = apply { this.cityId = cityId }
      override fun countryCode(countryCode: String): FluentBuilder = apply { this.countryCode = countryCode }
   }
}
```

Note the addition of new `countryCode` fields in the class and builders.

#### Usage

```kotlin
// Legacy
{
    val request = GetCityRequest { cityId = "Shimabara" }
}

// New
{
    val request = GetCityRequest {
        cityId = "Shimabara"
        countryCode = "JP"
    }
    val request2 = request.copy { 
       cityId = "Taira" 
    }
}
```

### Updated Data Class Sample

```kotlin
data class GetCityRequest private constructor(val cityId: String?, val countryCode: String?) {

   companion object {
      @JvmStatic
      fun fluentBuilder(): FluentBuilder = BuilderImpl()

      fun builder(): DslBuilder = BuilderImpl()

      operator fun invoke(block: DslBuilder.() -> kotlin.Unit): GetCityRequest = BuilderImpl().apply(block).build()

   }

   interface FluentBuilder {
      fun build(): GetCityRequest
      fun cityId(cityId: String): FluentBuilder
      fun countryCode(countryCode: String): FluentBuilder
   }

   interface DslBuilder {
      var cityId: String?
      var countryCode: String?

      fun build(): GetCityRequest
   }

   private class BuilderImpl() : FluentBuilder, DslBuilder {
      override var cityId: String? = null
      override var countryCode: String? = null

      constructor(x: GetCityRequest) : this() {
         this.cityId = x.cityId
         this.countryCode = x.countryCode
      }

      override fun build(): GetCityRequest = GetCityRequest(cityId = this.cityId, countryCode = this.countryCode)
      override fun cityId(cityId: String): FluentBuilder = apply { this.cityId = cityId }
      override fun countryCode(countryCode: String): FluentBuilder = apply { this.countryCode = countryCode }
   }
}
```

Note the addition of a new `countryCode` parameter to the constructor and `countryCode` fields in the builders.

#### Usage

```kotlin
// Legacy
{
    val request = GetCityRequest { cityId = "Kuchinotsu" }
}

// New
{
    val request = GetCityRequest {
        cityId = "Kuchinotsu"
        countryCode = "JP"
    }
    val request2 = request.copy("Taira")
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

* 9/17/2021 - Updates and Fixes to code snippets
* 6/1/2021 - Initial upload
