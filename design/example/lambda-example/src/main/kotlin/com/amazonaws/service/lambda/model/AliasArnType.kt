package com.amazonaws.service.lambda.model

sealed class AliasArnType
class S3ArnType(val value: String?) : AliasArnType()
class EC2ArnType(val value: Int?) : AliasArnType()
