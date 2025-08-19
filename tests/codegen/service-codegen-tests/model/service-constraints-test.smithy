$version: "2.0"

namespace com.constraints

use smithy.protocols#rpcv2Cbor

@rpcv2Cbor
service ServiceConstraintsTest {
    version: "1.0.0"
    operations: [
        RequiredConstraintTest,
        LengthConstraintTest,
        PatternConstraintTest,
        RangeConstraintTest,
        UniqueItemsConstraintTest,
        NestedUniqueItemsConstraintTest,
        DoubleNestedUniqueItemsConstraintTest,
    ]
}

@http(method: "POST", uri: "/required-constraint", code: 201)
operation RequiredConstraintTest {
    input: RequiredConstraintTestInput
    output: RequiredConstraintTestOutput
}

@input
structure RequiredConstraintTestInput {
    @required
    requiredInput: String
    notRequiredInput: String
}

@output
structure RequiredConstraintTestOutput {}

@http(method: "POST", uri: "/length-constraint", code: 201)
operation LengthConstraintTest {
    input: LengthConstraintTestInput
    output: LengthConstraintTestOutput
}

@input
structure LengthConstraintTestInput {
    @length(min: 3)
    greaterLengthInput: String

    @length(max: 3)
    smallerLengthInput: NotUniqueItemsList

    @length(min: 1, max: 2)
    betweenLengthInput: MyMap
}

@output
structure LengthConstraintTestOutput {}

@http(method: "POST", uri: "/pattern-constraint", code: 201)
operation PatternConstraintTest {
    input: PatternConstraintTestInput
    output: PatternConstraintTestOutput
}

@input
structure PatternConstraintTestInput {
    @pattern("^[A-Za-z]+$")
    patternInput1: String

    @pattern("[1-9]+")
    patternInput2: String


}

@output
structure PatternConstraintTestOutput {}


@http(method: "POST", uri: "/range-constraint", code: 201)
operation RangeConstraintTest {
    input: RangeConstraintTestInput
    output: RangeConstraintTestOutput
}

@input
structure RangeConstraintTestInput {
    @range(min: 0, max: 5)
    betweenInput: Integer

    @range(min: -10)
    greaterInput: Double

    @range(max: 9)
    smallerInput: Float
}

@output
structure RangeConstraintTestOutput {}

@http(method: "POST", uri: "/unique-items-constraint", code: 201)
operation UniqueItemsConstraintTest {
    input: UniqueItemsConstraintTestInput
    output: UniqueItemsConstraintTestOutput
}

@input
structure UniqueItemsConstraintTestInput {
    notUniqueItemsListInput: NotUniqueItemsList
    uniqueItemsListInput: UniqueItemsList
}

@output
structure UniqueItemsConstraintTestOutput {}

@http(method: "POST", uri: "/nested-unique-items-constraint", code: 201)
operation NestedUniqueItemsConstraintTest {
    input: NestedUniqueItemsConstraintTestInput
    output: NestedUniqueItemsConstraintTestOutput
}

@input
structure NestedUniqueItemsConstraintTestInput {
    nestedUniqueItemsListInput: UniqueItemsListWrap
}

@output
structure NestedUniqueItemsConstraintTestOutput {}

@http(method: "POST", uri: "/double-nested-unique-items-constraint", code: 201)
operation DoubleNestedUniqueItemsConstraintTest {
    input: DoubleNestedUniqueItemsConstraintTestInput
    output: DoubleNestedUniqueItemsConstraintTestOutput
}

@input
structure DoubleNestedUniqueItemsConstraintTestInput {
    doubleNestedUniqueItemsListInput: UniqueItemsListWrapContainer
}

@output
structure DoubleNestedUniqueItemsConstraintTestOutput {}

list NotUniqueItemsList {
    member: String
}

@uniqueItems
list UniqueItemsList {
    member: String
}

@uniqueItems
list UniqueItemsListWrap {
    member: UniqueItemsList
}

@uniqueItems
list UniqueItemsListWrapContainer {
    member: UniqueItemsListWrap
}

map MyMap {
    key: String
    value: String
}