package com.amazonaws.service.lambda.model

sealed class AliasType
class ExpiringAliasType(val value: Long?) : AliasType()
class RemoteAliasType(val value: String?) : AliasType()
class MultiAliasType(val value: List<String>): AliasType()