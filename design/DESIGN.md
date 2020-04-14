
# Kotlin Smithy SDK 

Reference the Smithy [Core Spec](https://awslabs.github.io/smithy/spec/core.html)

## Identifiers and Naming
Kotlin keywords can be found [here](https://kotlinlang.org/docs/reference/keyword-reference.html). Kotlin has both hard keywords and soft keywords (context sensitive).

The list of hard keywords that can never be identifiers in Kotlin is:
* `as`
* `as?`
* `break`
* `class`
* `continue`
* `do`
* `else`
* `false`
* `for`
* `fun`
* `if`
* `in`
* `!in`
* `interface`
* `is`
* `!is`
* `null`
* `object`
* `package`
* `return`
* `super`
* `this`
* `throw`
* `true`
* `try`
* `typealias`
* `typeof`
* `val`
* `var`
* `when`
* `while`

## Simple Shapes


|Smithy Type| Description                                                       | Kotlin Type
|-----------|-------------------------------------------------------------------|------------------------
|blob       | Uninterpreted binary data                                         | ByteArray
|boolean    | Boolean value type                                                | Boolean
|string     | UTF-8 encoded string                                              | String
|byte       | 8-bit signed integer ranging from -128 to 127 (inclusive)         | Byte
|short      | 16-bit signed integer ranging from -32,768 to 32,767 (inclusive)  | Short
|integer    | 32-bit signed integer ranging from -2^31 to (2^31)-1 (inclusive)  | Int
|long       | 64-bit signed integer ranging from -2^63 to (2^63)-1 (inclusive)  | Long
|float      | Single precision IEEE-754 floating point number                   | Float
|double     | Double precision IEEE-754 floating point number                   | Double
|bigInteger | Arbitrarily large signed integer                                  | **TBD**
|bigDecimal | Arbitrary precision signed decimal number                         | **TBD**
|timestamp  | Represents an instant in time with no UTC offset or timezone. The serialization of a timestamp is determined by a protocol. | TBD (Kotlin 1.4 should bring a Datetime lib that is MPP compatible, if we can wait for that we should)
|document   | Unstable Represents an untyped JSON-like value that can take on one of the following types: null, boolean, string, byte, short, integer, long, float, double, an array of these types, or a map of these types where the key is string. | *Unsupported

**QUESTION**: We should support the `document` type but perhaps we can wait until it's stable?
**QUESTION**: The JVM has big number support but it is not MP (multi-platform) compatible. This is probably ok depending on how we structure the relationship between the generated SDK and the client runtime which will support MP.

### `document` Type

The `document` type is an  untyped JSON-like value that can take on the following types: null, boolean, string, byte, short, integer, long, float, double, an array of these types, or a map of these types where the key is string.


**TODO** Finish the design of document. This type is probably best represented as a Sum type (sealed class is closest we can get in Kotlin). See the [JSON type](https://github.com/Kotlin/kotlinx.serialization/blob/master/runtime/commonMain/src/kotlinx/serialization/json/JsonElement.kt) from the kotlinx.serialization lib for an example.


```kotlin

sealed class Document


object SmithyNull : Document()
data class SmithyInt(val value: Int): Document()
data class SmithyByte(val value: Byte): Document()
data class SmithyShort(val value: Short): Document()
data class SmithyLong(val value: Long): Document()
data class SmithyFloat(val value: Float): Document()
data class SmithyDouble(val value: Double): Document()
data class SmithyArray(val values: List<Document>): Document()
data class SmithyMap(val values: Map<String, Document>): Document()

fun Int.toDocument(): Document = SmithyInt(this)
fun Byte.toDocument(): Document = SmithyByte(this)
fun Short.toDocument(): Document = SmithyShort(this)
fun Long.toDocument(): Document = SmithyLong(this)
fun Float.toDocument(): Document = SmithyFloat(this)
fun Double.toDocument(): Document = SmithyDouble(this)

// TODO - define a Document builder DSL for easy creation of more complex document types. See [JSON builders](https://github.com/Kotlin/kotlinx.serialization/blob/master/runtime/commonMain/src/kotlinx/serialization/json/JsonElementBuilders.kt)

```



## Aggregate types


| Smithy Type | Kotlin Type
|-------------|-------------
| list        | List
| set         | Set
| map         | Map
| structure   | *Class
| union       | *sealed class


### Structure

A [structure](https://awslabs.github.io/smithy/spec/core.html#structure) type represents a fixed set of named heterogeneous members. In Kotlin this can be represented
as either a normal class or a data class. 

Non boxed member values will be defaulted according to the spec: `The default value of a byte, short, integer, long, float, and double shape that is not boxed is zero`

```
list MyList {
    member: String
}

structure Foo {
    bar: String,
    baz: Integer,
    quux: MyList
}
```


```kotlin
class Foo {
    var bar: String? = null
    var baz: Int = 0
    var quux: List<String>? = null
}
```

### Union

A [union](https://awslabs.github.io/smithy/spec/core.html#union) is a fixed set of types where only one type is used at any one time. In Kotlin this maps well to a [sealed class](https://kotlinlang.org/docs/reference/sealed-classes.html)

Example

```
# smithy

union MyUnion {
    bar: Integer,
    foo: String
}
```

```kotlin
sealed class MyUnion

data class Bar(val bar: Int) : MyUnion()
data class Foo(val foo: String): MyUnion()
```


## Service types


## Resource types


## Traits

### Type Refinement Traits

#### `box` trait

Indicates that a shape is boxed which means the member may or may not contain a value and that the member has no default value.

NOTE: all shapes other than primitives are always considered boxed in the Smithy spec

```
structure Foo {
    @box
    bar: integer

}
```


```kotlin
class Foo {
    var bar: Int? = null
}
```

**QUESTION**: If all non-primitive types (e.g. String, Structure, List, etc) are considered boxed should they all be generated as nullable in Kotlin?
e.g.

```
structure Baz {
    quux: integer
}

structure Foo {
    bar: String,
    baz: Baz
}

```

```kotlin
class Baz(var quux: Int = 0)

class Foo {
    var bar: String? = null
    var baz: Baz? = null
}

```


#### `deprecated` trait

Will generate the equivalent code for the shape annotated with Kotlin's `@Deprecated` annotation.

```
@deprecated
structure Foo

@deprecated(message: "no longer used", since: "1.3")
```

```kotlin
@Deprecated
class Foo

@Deprecated(message = "no longer used; since 1.3")
class Foo
```

#### `error` trait

**TODO**

### Constraint traits

#### `enum` trait

Kotlin has first class support for enums and the SDK should make use of them to provide a type safe interface.

When no `name` is provided the enum name will be the same as the value, otherwise the Kotlin SDK will use the provided enum name.

The value will be stored as an additional property on the enum and passed in the constructor. This allows enums to be applied to other types in the future if Smithy evolves to allow it.


```
@enum("YES": {}, "NO": {})
string SimpleYesNo

@enum("Yes": {name: "YES"}, "No": {name: "NO"})
string TypedYesNo
```

```kotlin
enum class SimpleYesNo(val value: String) {
    YES("YES")
    NO("NO")
}

enum class TypedYesNo(val value: String) {
    YES("Yes")
    NO("No")
}
```


```
@enum(
    t2.nano: {
        name: "T2_NANO",
        documentation: """
            T2 instances are Burstable Performance
            Instances that provide a baseline level of CPU
            performance with the ability to burst above the
            baseline.""",
        tags: ["ebsOnly"]
    },
    t2.micro: {
        name: "T2_MICRO",
        documentation: """
            T2 instances are Burstable Performance
            Instances that provide a baseline level of CPU
            performance with the ability to burst above the
            baseline.""",
        tags: ["ebsOnly"]
    },
    m256.mega: {
        name: "M256_MEGA",
        deprecated: true
    }
)
string MyString
```


```kotlin
enum class MyString(val value: String) {

    /**
     * T2 instances are Burstable Performance Instances that provide a baseline level of CPU performance with the ability to burst above the baseline.
     */
    T2_NANO("t2.nano"),

    /**
     * T2 instances are Burstable Performance Instances that provide a baseline level of CPU performance with the ability to burst above the baseline.
     */
    T2_MICRO("t2.micro"),

    @Deprecated
    M256_MEGA("m256.mega"),
}
```


#### Considerations

**Serialization**


Serialization will need to make use of the given `value` of the enum rather than the name of the enum constant. 

See the following for examples using kotlinx.serialization:
* https://ahsensaeed.com/enum-class-serialization-kotlinx-serialization-library/
* https://github.com/Kotlin/kotlinx.serialization/issues/31#issuecomment-488572539

**Unknown Enum Names**

The Smithy core spec indicates that unknown enum values need to be handled as well. 

```
Consumers that choose to represent enums as constants SHOULD ensure that unknown enum names returned from a service do not cause runtime failures.
```

There isn't a great way to do that automatically yet. 
See: https://github.com/Kotlin/kotlinx.serialization/issues/90


The way to do that currently would be to always generate enums with an `SDK_UNKNOWN` enum constant and set the value to that during deserialization if one of the values fails to match.

**QUESTION**: Is it important to capture what the unrecognized value was? If so we need to rethink the enum representation probably.


#### `idRef` trait
Not processed

#### `length` trait
**TODO**

#### `pattern` trait
**TODO**

#### `private` trait
Not processed 

#### `range` trait
**TODO**

#### `required` trait

```
struct Foo {
    @required
    bar: String
}
```

##### ALTERNATIVE 1

All members marked `required` could show up in the constructor of the class and not be treated as nullable

```kotlin
class Foo(@Required var bar: String) {
}
```

##### ALTERNATIVE 2

Generate the code normally but mark it with the kotlinx.serialization `@Required` annotation.

```kotlin
class Foo {
    @Required
    var bar: String? = null
}
```


NOTE: Regardless of which way we go we should mark the field with the `@Required` annotation. This forces serialization to be mandatory and always present/expected in serialized form.
See [kotlinx.serialization annotations](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/examples.md#annotations) 

#### `uniqueItems` trait
**TODO**

### Behavior traits

#### `idempotentcyToken` trait
**TODO** The spec states that `clients MAY automatically provide a value`. This could be interpreted to provide a default UUID and allow it to be overridden.

#### `idempotent` trait

Not processed

**FUTURE** It may be worthwhile generating documentation that indicates the operation is idempotent.

#### `readonly` trait

Not processed

#### `retryable` trait


#### `paginated` trait



