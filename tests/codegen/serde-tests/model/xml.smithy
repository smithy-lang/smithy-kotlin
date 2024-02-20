$version: "1.0"

namespace aws.tests.serde.xml

use aws.serde.protocols#serdeXml
use aws.tests.serde.shared#Top

@serdeXml
service XmlService {
    version: "2022-07-07",
    operations: [TestOp]
}

@http(uri: "/top", method: "POST")
operation TestOp {
    input: Top,
    output: Top,
}
