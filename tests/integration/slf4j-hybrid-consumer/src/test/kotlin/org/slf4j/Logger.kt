/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.slf4j

// Override Logger interface to emulate having a slf4j1x interface be classloaded
interface Logger {
    fun getName(): String?
    fun isTraceEnabled(): Boolean
    fun trace(var1: String?)
    fun trace(var1: String?, var2: Any?)
    fun trace(var1: String?, var2: Any?, var3: Any?)
    fun trace(var1: String?, vararg var2: Any?)
    fun trace(var1: String?, var2: Throwable?)
    fun isTraceEnabled(var1: Marker?): Boolean
    fun trace(var1: Marker?, var2: String?)
    fun trace(var1: Marker?, var2: String?, var3: Any?)
    fun trace(var1: Marker?, var2: String?, var3: Any?, var4: Any?)
    fun trace(var1: Marker?, var2: String?, vararg var3: Any?)
    fun trace(var1: Marker?, var2: String?, var3: Throwable?)
    fun isDebugEnabled(): Boolean
    fun debug(var1: String?)
    fun debug(var1: String?, var2: Any?)
    fun debug(var1: String?, var2: Any?, var3: Any?)
    fun debug(var1: String?, vararg var2: Any?)
    fun debug(var1: String?, var2: Throwable?)
    fun isDebugEnabled(var1: Marker?): Boolean
    fun debug(var1: Marker?, var2: String?)
    fun debug(var1: Marker?, var2: String?, var3: Any?)
    fun debug(var1: Marker?, var2: String?, var3: Any?, var4: Any?)
    fun debug(var1: Marker?, var2: String?, vararg var3: Any?)
    fun debug(var1: Marker?, var2: String?, var3: Throwable?)
    fun isInfoEnabled(): Boolean
    fun info(var1: String?)
    fun info(var1: String?, var2: Any?)
    fun info(var1: String?, var2: Any?, var3: Any?)
    fun info(var1: String?, vararg var2: Any?)
    fun info(var1: String?, var2: Throwable?)
    fun isInfoEnabled(var1: Marker?): Boolean
    fun info(var1: Marker?, var2: String?)
    fun info(var1: Marker?, var2: String?, var3: Any?)
    fun info(var1: Marker?, var2: String?, var3: Any?, var4: Any?)
    fun info(var1: Marker?, var2: String?, vararg var3: Any?)
    fun info(var1: Marker?, var2: String?, var3: Throwable?)
    fun isWarnEnabled(): Boolean
    fun warn(var1: String?)
    fun warn(var1: String?, var2: Any?)
    fun warn(var1: String?, vararg var2: Any?)
    fun warn(var1: String?, var2: Any?, var3: Any?)
    fun warn(var1: String?, var2: Throwable?)
    fun isWarnEnabled(var1: Marker?): Boolean
    fun warn(var1: Marker?, var2: String?)
    fun warn(var1: Marker?, var2: String?, var3: Any?)
    fun warn(var1: Marker?, var2: String?, var3: Any?, var4: Any?)
    fun warn(var1: Marker?, var2: String?, vararg var3: Any?)
    fun warn(var1: Marker?, var2: String?, var3: Throwable?)
    fun isErrorEnabled(): Boolean
    fun error(var1: String?)
    fun error(var1: String?, var2: Any?)
    fun error(var1: String?, var2: Any?, var3: Any?)
    fun error(var1: String?, vararg var2: Any?)
    fun error(var1: String?, var2: Throwable?)
    fun isErrorEnabled(var1: Marker?): Boolean
    fun error(var1: Marker?, var2: String?)
    fun error(var1: Marker?, var2: String?, var3: Any?)
    fun error(var1: Marker?, var2: String?, var3: Any?, var4: Any?)
    fun error(var1: Marker?, var2: String?, vararg var3: Any?)
    fun error(var1: Marker?, var2: String?, var3: Throwable?)
}
