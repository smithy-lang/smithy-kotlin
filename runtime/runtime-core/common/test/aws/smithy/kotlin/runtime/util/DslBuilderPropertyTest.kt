/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.ClientException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertIs

private const val DEFAULT_COLOR = "(unknown)"
private const val DEFAULT_DOORS = 4
private const val DEFAULT_GEARS = 1

class DslBuilderPropertyTest {
    @Test
    fun testSetInstance() {
        val prop = vehicleProp()

        prop.instance = BlueCoupe
        assertEquals(BlueCoupe, prop.supply())

        prop.instance = OrangeMtnBike
        assertEquals(OrangeMtnBike, prop.supply())

        // Test reset back to default factory
        prop.instance = null
        val actual = prop.supply()
        assertIs<Car>(actual)
        assertEquals(DEFAULT_COLOR, actual.config.color)
        assertEquals(DEFAULT_DOORS, actual.config.doors)
    }

    @Test
    fun testSetDsl() {
        val prop = vehicleProp()

        prop.dsl(Bike) { color = "yellow" }
        var actual = prop.supply()
        assertIs<Bike>(actual)
        assertEquals("yellow", actual.config.color)
        assertEquals(DEFAULT_GEARS, actual.config.gears)

        prop.dsl(Bike) { gears = 5 }
        actual = prop.supply()
        assertIs<Bike>(actual)
        assertEquals("yellow", actual.config.color)
        assertEquals(5, actual.config.gears)

        prop.dsl(Bike) { color = "purple" }
        actual = prop.supply()
        assertIs<Bike>(actual)
        assertEquals("purple", actual.config.color)
        assertEquals(5, actual.config.gears)
    }

    @Test
    fun testSetDslAfterInitialInstance() {
        val prop = vehicleProp()

        prop.instance = BlueCoupe
        assertEquals(BlueCoupe, prop.supply())

        prop.dsl(Car) {
            assertEquals(BlueCoupe.config.color, color)
            assertEquals(BlueCoupe.config.doors, doors)

            doors = 4
        }

        val actual = prop.supply()
        assertIs<Car>(actual)
        assertEquals(BlueCoupe.config.color, actual.config.color)
        assertEquals(4, actual.config.doors)
    }

    @Test
    fun testSetDslAfterInitialInstanceAndChangeType() {
        val prop = vehicleProp()

        prop.instance = BlueCoupe
        assertEquals(BlueCoupe, prop.supply())

        prop.dsl(Bike) {
            assertEquals(BlueCoupe.config.color, color)

            gears = 10
        }

        val actual = prop.supply()
        assertIs<Bike>(actual)
        assertEquals(BlueCoupe.config.color, actual.config.color)
        assertEquals(10, actual.config.gears)
    }

    @Test
    fun testStateWithInstanceFirst() {
        val prop = vehicleProp() // SupplierState.NOT_INITIALIZED

        prop.instance = BlueCoupe // SupplierState.INITIALIZED

        prop.instance = OrangeMtnBike // SupplierState.EXPLICIT_INSTANCE

        assertThrows<ClientException> { // Cannot switch from EXPLICIT_INSTANCE to EXPLICIT_CONFIG
            prop.dsl(Bike) { }
        }
    }

    @Test
    fun testStateWithConfigFirst() {
        val prop = vehicleProp() // SupplierState.NOT_INITIALIZED

        prop.instance = BlueCoupe // SupplierState.INITIALIZED

        prop.dsl(Bike) { } // SupplierState.EXPLICIT_CONFIG

        prop.instance = OrangeMtnBike // SupplierState.EXPLICIT_INSTANCE

        assertThrows<ClientException> { // Cannot switch from EXPLICIT_INSTANCE to EXPLICIT_CONFIG
            prop.dsl(Car) { }
        }
    }
}

private fun vehicleProp(managedTransform: (Vehicle) -> Vehicle = { it }) =
    DslBuilderProperty<Vehicle.Config.Builder, Vehicle>(Car, Vehicle::toBuilderApplicator, managedTransform)

// Super interfaces for a DSL
private interface Vehicle {
    fun toBuilderApplicator(): (Config.Builder.() -> Unit)

    interface Config {
        val color: String
        interface Builder {
            var color: String?
        }
    }
}

// Subtypes for the DSL
private class Car(val config: Config) : Vehicle {
    companion object : DslFactory<Config.Builder, Car> {
        override fun invoke(block: Config.Builder.() -> Unit) = Car(Config(Config.Builder().apply(block)))
    }

    override fun toBuilderApplicator(): Vehicle.Config.Builder.() -> Unit = {
        color = config.color
        if (this is Config.Builder) {
            doors = config.doors
        }
    }

    class Config(builder: Builder) : Vehicle.Config {
        override val color: String = builder.color ?: DEFAULT_COLOR
        val doors: Int = builder.doors ?: DEFAULT_DOORS

        class Builder : Vehicle.Config.Builder {
            override var color: String? = null
            var doors: Int? = null
        }
    }
}
private val BlueCoupe = Car { color = "blue"; doors = 2 }

private class Bike(val config: Config) : Vehicle {
    companion object : DslFactory<Config.Builder, Bike> {
        override fun invoke(block: Config.Builder.() -> Unit) = Bike(Config(Config.Builder().apply(block)))
    }

    override fun toBuilderApplicator(): Vehicle.Config.Builder.() -> Unit = {
        color = config.color
        if (this is Config.Builder) {
            gears = config.gears
        }
    }

    class Config(builder: Builder) : Vehicle.Config {
        override val color: String = builder.color ?: DEFAULT_COLOR
        val gears: Int = builder.gears ?: DEFAULT_GEARS

        class Builder : Vehicle.Config.Builder {
            override var color: String? = null
            var gears: Int? = null
        }
    }
}
private val OrangeMtnBike = Bike { color = "orange"; gears = 9 }
