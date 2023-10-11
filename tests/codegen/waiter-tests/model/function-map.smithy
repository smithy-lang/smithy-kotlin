$version: "2"
namespace com.test

use smithy.waiters#waitable

@suppress(["WaitableTraitJmespathProblem"])
@waitable(
    MapStructEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "map(&string, lists.structs)",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/map/{name}", code: 200)
operation GetFunctionMapEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}
