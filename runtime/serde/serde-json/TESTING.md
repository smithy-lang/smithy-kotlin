How to run JSONTestSuite against serde-json deserialize
========================================================

When making changes to the lexer it is a good idea to run the
changes against the [JSONTestSuite](https://github.com/nst/JSONTestSuite) and manually examine the test results.

### How to setup the JSONTestSuite

1. Clone the [JSONTestSuite](https://github.com/nst/JSONTestSuite) repository.
2. In `JSONTestSuite/parsers`, create a new Gradle JVM application project named `test_smithy_kotlin`.
3. Add the following `build.gradle.kts` file

```kotlin
plugins {
    kotlin("jvm") version "1.5.30"
    application
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

application {
    mainClass.set("aws.smithy.kotlin.jsontest.MainKt")
}

allprojects {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}


// NOTE: set to whatever locally published version you are working on
val smithyKotlinVersion: String = "0.4.1-kmp-json"
dependencies {
   implementation(kotlin("stdlib"))
   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.0")
   implementation("aws.smithy.kotlin:serde-json:$smithyKotlinVersion")
   implementation("aws.smithy.kotlin:utils:$smithyKotlinVersion")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "aws.smithy.kotlin.jsontest.MainKt"
    }
}
```

4. Add the following code to `src/main/kotlin/Main.kt` with:

```kotlin
package aws.smithy.kotlin.jsontest

import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import aws.smithy.kotlin.runtime.serde.json.JsonToken
import aws.smithy.kotlin.runtime.serde.json.jsonStreamReader
import aws.smithy.kotlin.runtime.util.InternalApi


@OptIn(InternalApi::class)
suspend fun isValidJson(bytes: ByteArray):Boolean {
    val lexer = jsonStreamReader(bytes)
    println(lexer::class.qualifiedName)
    return try {
        val tokens = mutableListOf<JsonToken>()
        do {
            val token = lexer.nextToken()
            tokens.add(token)
        }while(token != JsonToken.EndDocument)

        // The test suite includes incomplete objects and arrays (e.g. "[null,")
        // These are completely valid for this parser since it's just a tokenizer
        // and doesn't attempt to make semantic meaning from the input.
        // We'll just pretend to fail to satisfy the test suite
        val pruned = if (tokens.last() == JsonToken.EndDocument) tokens.dropLast(1) else tokens
        if (pruned.first() == JsonToken.BeginArray && pruned.last() != JsonToken.EndArray) {
            return false
        }
        if (pruned.first() == JsonToken.BeginObject && pruned.last() != JsonToken.EndObject) {
            return false
        }

        tokens.isNotEmpty()
    }catch(ex: Exception) {
        println(ex)
        false
    }
}

fun main(args: Array<String>): Unit = runBlocking {
    if(args.isEmpty()) {
        println("Usage: java TestJSONParsing file.json")
        exitProcess(2)
    }

    try {
        val data = Files.readAllBytes(Paths.get(args[0]))
        if(isValidJson(data)) {
            println("valid");
            exitProcess(0);
        }
        println("invalid");
        exitProcess(1);
    } catch (ex: IOException) {
        println(ex)
        println("not found");
        exitProcess(2);
    }
}
```

5. Compile this program with `./gradlew build`. 
   NOTE: Be sure to publish all of `smithy-kotlin` "runtime" to maven local. It is helpful to just choose a unique version
   to be sure that everything is wired up correctly.
6. Modify `JSONTestSuite/run_tests.py` so that the `programs` dictionary only contains this one entry:

```
programs = {
   "SmithyKotlin":
       {
           "url":"",
           "commands":["java" , "-jar", os.path.join(PARSERS_DIR, "test_smithy_kotlin/build/libs/test_smithy_kotlin-all.jar")]
       }
}
```

7. Run `run_tests.py` and examine the output with a web browser by opening `JSONTestSuite/results/parsing.html`.

### Examining the results

When looking at `JSONTestSuite/results/parsing.html`, there is a matrix of test cases against their
results with a legend at the top.

Any test result marked with blue or light blue is for a test case where correct behavior isn't specified,
so use your best judgement to decide if it should have succeeded or failed.

The other colors are bad and should be carefully examined. At time of writing, the following test cases
succeed when they should fail, and we intentionally left it that way since we're not currently concerned
about being more lenient in the number parsing:

```

n_number_-01.json                               [-01]
n_number_-2..json                               [-2.]
n_number_.2e-3.json                             [.2e-3]
n_number_0.3e+.json                             [0.3e+]
n_number_0.3e.json                              [0.3e]
n_number_0.e1.json                              [0.e1]
n_number_0_capital_E+.json                      [0E+]
n_number_0_capital_E.json                       [0E]
n_number_0e+.json                               [0e+]
n_number_0e.json                                [0e]
n_number_1.0e+.json                             [1.0e+]
n_number_1.0e-.json                             [1.0e-]
n_number_1.0e.json                              [1.0e]
n_number_2.e+3.json                             [2.e+3]
n_number_2.e-3.json                             [2.e-3]
n_number_2.e3.json                              [2.e3]
n_number_9.e+.json                              [9.e+]
n_number_neg_int_starting_with_zero.json        [-012]
n_number_neg_real_without_int_part.json         [-.123]
n_number_real_without_fractional_part.json      [1.]
n_number_starting_with_dot.json                 [.123]
n_number_with_leading_zero.json                 [012]
```



This test case succeeds with our parser and that's OK since we're
a token streaming parser (multiple values are allowed):
```
n_array_just_minus.json                         [-]
n_structure_double_array.json                   [][]
n_structure_whitespace_formfeed.json            [0C] <=> []
```