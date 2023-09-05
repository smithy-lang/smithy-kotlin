$version: "2"
namespace com.test

service WaitersTestService {
    operations: [
        GetPrimitive,
        GetBooleanLogic,
        GetListOperation,
        GetStringEquals,
        GetMultiSelectList,
        GetMultiSelectHash,
        GetFunctionContains,
        GetFunctionLength,
        GetFunctionAbs,
        GetFunctionFloor,
        GetFunctionCeil,
        GetSubFieldProjection,
        GetFunctionSumEquals,
        GetFunctionAvgEquals,
        GetFunctionJoinEquals,
        GetFunctionStartsWithEquals,
        GetFunctionEndsWithEquals,
        GetFunctionKeysEquals,
        GetFunctionValuesEquals,
    ]
}
