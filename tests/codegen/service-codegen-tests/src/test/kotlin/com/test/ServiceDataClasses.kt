package com.test

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val code: Int, val message: String)

@Serializable
data class MalformedPostTestRequest(val input1: Int, val input2: String)

@Serializable
data class PostTestRequest(val input1: String, val input2: Int)

@Serializable
data class PostTestResponse(val output1: String? = null, val output2: Int? = null)

@Serializable
data class AuthTestRequest(val input1: String)

@Serializable
data class ErrorTestRequest(val input1: String)

@Serializable
data class HttpError(val msg: String, val num: Int)

@Serializable
data class RequiredConstraintTestRequest(val requiredInput: String? = null, val notRequiredInput: String? = null)

@Serializable
data class LengthConstraintTestRequest(
    val greaterLengthInput: String,
    val smallerLengthInput: List<String>,
    val betweenLengthInput: Map<String, String>,
)

@Serializable
data class PatternConstraintTestRequest(val patternInput1: String, val patternInput2: String)

@Serializable
data class RangeConstraintTestRequest(val betweenInput: Int, val greaterInput: Double, val smallerInput: Float)

@Serializable
data class UniqueItemsConstraintTestRequest(val notUniqueItemsListInput: List<String>, val uniqueItemsListInput: List<String>)

@Serializable
data class NestedUniqueItemsConstraintTestRequest(val nestedUniqueItemsListInput: List<List<String>>)

@Serializable
data class DoubleNestedUniqueItemsConstraintTestRequest(val doubleNestedUniqueItemsListInput: List<List<List<String>>>)

@Serializable
data class HttpLabelTestOutputResponse(val output: String)

@Serializable
data class HttpQueryTestOutputResponse(val output: String)

