$version: "1.0"

namespace com.test

use smithy.protocols#rpcv2Cbor

@rpcv2Cbor
@httpBearerAuth
service ServiceGeneratorTest {
    version: "1.0.0"
    operations: [
        PostTest,
        AuthTest,
        ErrorTest,

        RequiredConstraintTest,
        LengthConstraintTest,
        PatternConstraintTest,
        RangeConstraintTest,
        UniqueItemsConstraintTest,
        NestedUniqueItemsConstraintTest,
    ]
}


@http(method: "POST", uri: "/post", code: 201)
@auth([])
operation PostTest {
    input: PostTestInput
    output: PostTestOutput
}

@input
structure PostTestInput {
    input1: String
    input2: Integer
}

@output
structure PostTestOutput {
    output1: String
    output2: Integer
}

@http(method: "POST", uri: "/auth", code: 201)
operation AuthTest {
    input: AuthTestInput
    output: AuthTestOutput
}

@input
structure AuthTestInput {
    input1: String
}

@output
structure AuthTestOutput {
    output1: String
}

@http(method: "POST", uri: "/error", code: 200)
operation ErrorTest {
    input: ErrorTestInput
    output: ErrorTestOutput
}

@input
structure ErrorTestInput {
    input1: String
}

@output
structure ErrorTestOutput {
    output1: String
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

map MyMap {
    key: String
    value: String
}

