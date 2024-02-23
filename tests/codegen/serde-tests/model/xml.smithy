$version: "2.0"

namespace aws.tests.serde.xml

use aws.serde.protocols#serdeXml
use aws.tests.serde.shared#PrimitiveTypesMixin
use aws.tests.serde.shared#ListTypesMixin
use aws.tests.serde.shared#MapTypesMixin
use aws.tests.serde.shared#PrimitiveTypesUnionMixin
use aws.tests.serde.shared#ListTypesUnionMixin
use aws.tests.serde.shared#MapTypesUnionMixin
use aws.tests.serde.shared#StringMap
use aws.tests.serde.shared#StringListMap
use aws.tests.serde.shared#NestedStringMap
use aws.tests.serde.shared#FooEnumMap
use aws.tests.serde.shared#IntegerList
use aws.tests.serde.shared#StringList
use aws.tests.serde.shared#NestedStringList

@serdeXml
service XmlService {
    version: "2022-07-07",
    operations: [TestOp]
}

@http(uri: "/top", method: "POST")
operation TestOp {
    input: StructType,
    output: StructType,
}

structure StructType with [PrimitiveTypesMixin, ListTypesMixin, MapTypesMixin]  {
    unionField: UnionType,

    recursive: StructType,

    @xmlAttribute
    extra: Long,

    @xmlName("prefix:local")
    renamedWithPrefix: String,

    @xmlFlattened
    @xmlName("flatlist1")
    flatList: StringList,

    @xmlFlattened
    @xmlName("flatlist2")
    secondFlatList: IntegerList

    @xmlFlattened
    @xmlName("flatenummap")
    flatEnumMap: FooEnumMap,

    renamedMemberList: RenamedMemberIntList

    renamedMemberMap: RenamedMap
}

list RenamedMemberIntList {
    @xmlName("item")
    member: String
}

map RenamedMap {
    @xmlName("aKey")
    key: String

    @xmlName("aValue")
    value: String
}

union UnionType with [PrimitiveTypesUnionMixin, ListTypesUnionMixin, MapTypesUnionMixin] {
    @xmlFlattened
    @xmlName("flatmap")
    flatMap: StringMap,

    @xmlFlattened
    @xmlName("flatlist")
    flatList: StringList,

    @xmlName("double")
    fpDouble: Double,

    struct: StructType,
}
