# BREAKING: Map key changes

An upcoming release of the **AWS SDK for Kotlin** will change the key type for several maps from strings into specific enumerations.

# Release date

This feature will ship with the **v1.0.80** release planned for **3/18/2024**.

# What's changing

Several members of service request/response types are maps which incorrectly use `String` as the key type. These types are being replaced to use a member-specific `enum class` as the key type.

For example, `SqsClient` has an operation `setQueueAttributes` whose request object has a map member named `attributes`. Previously this member was of type `Map<String, String>?` but now is of type `Map<QueueAttributeName, String>?` to more accurately reflect the expected key values. See the **Full list of updated maps** section below for a detailed list of all changes.

# How to migrate

If you are using any of the map members listed in the **Full list of updated maps** section below, you will need to update your code to change affected map keys from strings to a new enum type.

For example, a prior call to SQS's `setQueueAttributes` operation may have looked like this:

```kotlin
sqs.setQueueAttributes {
    queueUrl = "..."
    attributes = mapOf(
        "MaximumMessageSize" to "1024", // 1KB
        "VisibilityTimeout" to "3600", // 1 hour
    )
}
```

After the breaking change, the equivalent code would be:

```kotlin
sqs.setQueueAttributes {
    queueUrl = "..."
    attributes = mapOf(
        QueueAttributeName.MaximumMessageSize to "1024", // 1KB
        QueueAttributeName.VisibilityTimeout to "3600", // 1 hour
    )
}
```

## Handling unmodeled key values

If your code was using a string constant which is not represented as an enum element, you will need to update your code to use the `SdkUnknown` enum variant. Take for instance prior code which interacted with a map like this:

```kotlin
mapOf(
    "Foo" to 1,
    "Bar" to 2,
    "SomeOtherName" to 999,
)
```

If the string key is replaced with a new enum type `Keys` which has elements `Foo` and `Bar` but not `SomeOtherName` then the equivalent code would be:

```kotlin
mapOf(
    Keys.Foo to 1,
    Keys.Bar to 2,
    Keys.SdkUnknown("SomeOtherName") to 999,
)
```

This is a rare use case. Unexpected key values may already be ignored by the service or cause an exception.

# Full list of updated maps

The following services have request/response types in which a map has changed type:

## AppConfig

* `CreateExtensionRequest.actions`
    * Old type: `Map<String, List<Action>>`
    * New type: `Map<ActionPoint, List<Action>>`
    * Operation: `createExtension`
* `Extension.actions`
    * Old type: `Map<String, List<Action>>`
    * New type: `Map<ActionPoint, List<Action>>`
    * Operations:
        * `createExtension`
        * `getExtension`
        * `updateExtension`
* `UpdateExtensionRequest.actions`
    * Old type: `Map<String, List<Action>>`
    * New type: `Map<ActionPoint, List<Action>>`
    * Operation: `updateExtension`

## Appflow

* `DescribeConnectorsResponse.connectorConfigurations`
    * Old type: `Map<String, ConnectorConfiguration>`
    * New type: `Map<ConnectorType, ConnectorConfiguration>`
    * Operation: `describeConnectors`
* `Task.taskProperties`
    * Old type: `Map<String, String>`
    * New type: `Map<OperatorPropertiesKeys, String>`
    * Operations:
        * `createFlow`
        * `describeFlow`
        * `updateFlow`

## Application Insights

* `ApplicationComponent.detectedWorkload`
    * Old type: `Map<String, Map<String, String>>`
    * New type: `Map<Tier, Map<String, String>>`
    * Operations:
        * `listComponents`
        * `describeComponent`
* `Problem.feedback`
    * Old type: `Map<String, FeedbackValue>`
    * New type: `Map<FeedbackKey, FeedbackValue>`
    * Operations:
        * `describeProblem`
        * `listProblems`

## Backup

* `CopyJob.childJobsInState`
    * Old type: `Map<String, Long>`
    * New type: `Map<CopyJobState, Long>`
    * Operations:
        * `listCopyJobs`
        * `describeCopyJob`
* `DescribeBackupJobOutput.childJobsInState`
    * Old type: `Map<String, Long>`
    * New type: `Map<BackupJobState, Long>`
    * Operation: `describeBackupJob`

## CodeDeploy

* `ListDeploymentTargetsInput.targetFilters`
    * Old type: `Map<String, List<String>>`
    * New type: `Map<TargetFilterName, List<String>>`
    * Operation: `listDeploymentTargets`

## CodeGuruProfiler

* `AgentConfiguration.agentParameters`
    * Old type: `Map<String, String>`
    * New type: `Map<AgentParameterField, String>`
    * Operation: `configureAgent`
* `ConfigureAgentRequest.metadata`
    * Old type: `Map<String, String>`
    * New type: `Map<MetadataField, String>`
    * Operation: `configureAgent`

## Codeartifact

* `AssetSummary.hashes`
    * Old type: `Map<String, String>`
    * New type: `Map<HashAlgorithm, String>`
    * Operations:
        * `listPackageVersionAssets`
        * `publishPackageVersion`

## Connect

* `UserData.activeSlotsByChannel`
    * Old type: `Map<String, Int>`
    * New type: `Map<Channel, Int>`
    * Operations:
        * `getCurrentUserData`
        * `getCurrentUserData`
        * `getCurrentUserData`
* `UserData.availableSlotsByChannel`
    * Old type: `Map<String, Int>`
    * New type: `Map<Channel, Int>`
    * Operations:
        * `getCurrentUserData`
        * `getCurrentUserData`
        * `getCurrentUserData`
* `UserData.maxSlotsByChannel`
    * Old type: `Map<String, Int>`
    * New type: `Map<Channel, Int>`
    * Operations:
        * `getCurrentUserData`
        * `getCurrentUserData`
        * `getCurrentUserData`

## Customer Profiles

* `Task.taskProperties`
    * Old type: `Map<String, String>`
    * New type: `Map<OperatorPropertiesKeys, String>`
    * Operations:
        * `createIntegrationWorkflow`
        * `putIntegration`

## DataSync

* `DescribeStorageSystemResourcesRequest.filter`
    * Old type: `Map<String, List<String>>`
    * New type: `Map<DiscoveryResourceFilter, List<String>>`
    * Operation: `describeStorageSystemResources`

## Detective

* `DatasourcePackageIngestDetail.lastIngestStateChange`
    * Old type: `Map<String, TimestampForCollection>`
    * New type: `Map<DatasourcePackageIngestState, TimestampForCollection>`
    * Operation: `listDatasourcePackages`
* `ListDatasourcePackagesResponse.datasourcePackages`
    * Old type: `Map<String, StringIngestDetail>`
    * New type: `Map<DatasourcePackage, DatasourcePackageIngestDetail>`
    * Operation: `listDatasourcePackages`
* `MemberDetail.datasourcePackageIngestStates`
    * Old type: `Map<String, StringIngestState>`
    * New type: `Map<DatasourcePackage, DatasourcePackageIngestState>`
    * Operations:
        * `createMembers`
        * `getMembers`
        * `listInvitations`
        * `listMembers`
* `MemberDetail.volumeUsageByDatasourcePackage`
    * Old type: `Map<String, StringUsageInfo>`
    * New type: `Map<DatasourcePackage, DatasourcePackageUsageInfo>`
    * Operations:
        * `createMembers`
        * `getMembers`
        * `listInvitations`
        * `listMembers`
* `MembershipDatasources.datasourcePackageIngestHistory`
    * Old type: `Map<DatasourcePackage, Map<String, TimestampForCollection>>`
    * New type: `Map<DatasourcePackage, Map<DatasourcePackageIngestState, TimestampForCollection>>`
    * Operations:
        * `batchGetGraphMemberDatasources`
        * `batchGetMembershipDatasources`
* `MembershipDatasources.datasourcePackageIngestHistory`
    * Old type: `Map<String, Map<StringIngestState, TimestampForCollection>>`
    * New type: `Map<DatasourcePackage, Map<DatasourcePackageIngestState, TimestampForCollection>>`
    * Operations:
        * `batchGetGraphMemberDatasources`
        * `batchGetMembershipDatasources`

## Device Farm

* `AccountSettings.unmeteredDevices`
    * Old type: `Map<String, Int>`
    * New type: `Map<DevicePlatform, Int>`
    * Operations:
        * `getAccountSettings`
        * `getAccountSettings`
* `AccountSettings.unmeteredRemoteAccessDevices`
    * Old type: `Map<String, Int>`
    * New type: `Map<DevicePlatform, Int>`
    * Operations:
        * `getAccountSettings`
        * `getAccountSettings`
* `ListUniqueProblemsResult.uniqueProblems`
    * Old type: `Map<String, List<UniqueProblem>>`
    * New type: `Map<ExecutionResult, List<UniqueProblem>>`
    * Operation: `listUniqueProblems`

## ECR

* `ImageScanFindings.findingSeverityCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<FindingSeverity, Int>`
    * Operation: `describeImageScanFindings`
* `ImageScanFindingsSummary.findingSeverityCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<FindingSeverity, Int>`
    * Operation: `describeImages`

## Elasticsearch Service

* `CreateElasticsearchDomainRequest.logPublishingOptions`
    * Old type: `Map<String, LogPublishingOption>`
    * New type: `Map<LogType, LogPublishingOption>`
    * Operation: `createElasticsearchDomain`
* `ElasticsearchDomainStatus.logPublishingOptions`
    * Old type: `Map<String, LogPublishingOption>`
    * New type: `Map<LogType, LogPublishingOption>`
    * Operations:
        * `createElasticsearchDomain`
        * `deleteElasticsearchDomain`
        * `describeElasticsearchDomain`
        * `describeElasticsearchDomains`
* `LogPublishingOptionsStatus.options`
    * Old type: `Map<String, LogPublishingOption>`
    * New type: `Map<LogType, LogPublishingOption>`
    * Operations:
        * `describeElasticsearchDomainConfig`
        * `updateElasticsearchDomainConfig`
* `UpdateElasticsearchDomainConfigRequest.logPublishingOptions`
    * Old type: `Map<String, LogPublishingOption>`
    * New type: `Map<LogType, LogPublishingOption>`
    * Operation: `updateElasticsearchDomainConfig`

## FMS

* `Policy.excludeMap`
    * Old type: `Map<String, List<String>>`
    * New type: `Map<CustomerPolicyScopeIdType, List<String>>`
    * Operations:
        * `getPolicy`
        * `putPolicy`
        * `putPolicy`
        * `getPolicy`
        * `putPolicy`
        * `putPolicy`
* `Policy.includeMap`
    * Old type: `Map<String, List<String>>`
    * New type: `Map<CustomerPolicyScopeIdType, List<String>>`
    * Operations:
        * `getPolicy`
        * `putPolicy`
        * `putPolicy`
        * `getPolicy`
        * `putPolicy`
        * `putPolicy`
* `PolicyComplianceDetail.issueInfoMap`
    * Old type: `Map<String, String>`
    * New type: `Map<DependentServiceName, String>`
    * Operation: `getComplianceDetail`
* `PolicyComplianceStatus.issueInfoMap`
    * Old type: `Map<String, String>`
    * New type: `Map<DependentServiceName, String>`
    * Operation: `listComplianceStatus`

## Glue

* `Connection.connectionProperties`
    * Old type: `Map<String, String>`
    * New type: `Map<ConnectionPropertyKey, String>`
    * Operations:
        * `getConnections`
        * `getConnection`
* `ConnectionInput.connectionProperties`
    * Old type: `Map<String, String>`
    * New type: `Map<ConnectionPropertyKey, String>`
    * Operations:
        * `createConnection`
        * `updateConnection`
* `EvaluateDataQualityMultiFrame.additionalOptions`
    * Old type: `Map<String, String>`
    * New type: `Map<AdditionalOptionKeys, String>`
    * Operations:
        * `createJob`
        * `getJob`
        * `batchGetJobs`
        * `getJobs`
        * `updateJob`
* `JdbcConnectorOptions.dataTypeMapping`
    * Old type: `Map<String, GlueRecordType>`
    * New type: `Map<JdbcDataType, GlueRecordType>`
    * Operations:
        * `createJob`
        * `getJob`
        * `batchGetJobs`
        * `getJobs`
        * `updateJob`

## GuardDuty

* `CoverageStatistics.countByCoverageStatus`
    * Old type: `Map<String, Long>`
    * New type: `Map<CoverageStatus, Long>`
    * Operation: `getCoverageStatistics`
* `CoverageStatistics.countByResourceType`
    * Old type: `Map<String, Long>`
    * New type: `Map<ResourceType, Long>`
    * Operation: `getCoverageStatistics`
* `ScanResourceCriteria.exclude`
    * Old type: `Map<String, ScanCondition>`
    * New type: `Map<ScanCriterionKey, ScanCondition>`
    * Operations:
        * `getMalwareScanSettings`
        * `updateMalwareScanSettings`
        * `getMalwareScanSettings`
        * `updateMalwareScanSettings`
* `ScanResourceCriteria.include`
    * Old type: `Map<String, ScanCondition>`
    * New type: `Map<ScanCriterionKey, ScanCondition>`
    * Operations:
        * `getMalwareScanSettings`
        * `updateMalwareScanSettings`
        * `getMalwareScanSettings`
        * `updateMalwareScanSettings`

## Health

* `AccountEntityAggregate.statuses`
    * Old type: `Map<String, Int>`
    * New type: `Map<EntityStatusCode, Int>`
    * Operation: `describeEntityAggregatesForOrganization`
* `EntityAggregate.statuses`
    * Old type: `Map<String, Int>`
    * New type: `Map<EntityStatusCode, Int>`
    * Operation: `describeEntityAggregates`
* `OrganizationEntityAggregate.statuses`
    * Old type: `Map<String, Int>`
    * New type: `Map<EntityStatusCode, Int>`
    * Operation: `describeEntityAggregatesForOrganization`

## IAM

* `GetAccountSummaryResponse.summaryMap`
    * Old type: `Map<String, Int>`
    * New type: `Map<SummaryKeyType, Int>`
    * Operation: `getAccountSummary`

## Inspector

* `AssessmentRun.findingCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<Severity, Int>`
    * Operation: `describeAssessmentRuns`

## IoT

* `CreateSecurityProfileRequest.alertTargets`
    * Old type: `Map<String, AlertTarget>`
    * New type: `Map<AlertTargetType, AlertTarget>`
    * Operation: `createSecurityProfile`
* `DescribeAccountAuditConfigurationResponse.auditNotificationTargetConfigurations`
    * Old type: `Map<String, AuditNotificationTarget>`
    * New type: `Map<AuditNotificationType, AuditNotificationTarget>`
    * Operation: `describeAccountAuditConfiguration`
* `DescribeEventConfigurationsResponse.eventConfigurations`
    * Old type: `Map<String, Configuration>`
    * New type: `Map<EventType, Configuration>`
    * Operation: `describeEventConfigurations`
* `DescribeSecurityProfileResponse.alertTargets`
    * Old type: `Map<String, AlertTarget>`
    * New type: `Map<AlertTargetType, AlertTarget>`
    * Operation: `describeSecurityProfile`
* `UpdateAccountAuditConfigurationRequest.auditNotificationTargetConfigurations`
    * Old type: `Map<String, AuditNotificationTarget>`
    * New type: `Map<AuditNotificationType, AuditNotificationTarget>`
    * Operation: `updateAccountAuditConfiguration`
* `UpdateEventConfigurationsRequest.eventConfigurations`
    * Old type: `Map<String, Configuration>`
    * New type: `Map<EventType, Configuration>`
    * Operation: `updateEventConfigurations`
* `UpdateSecurityProfileRequest.alertTargets`
    * Old type: `Map<String, AlertTarget>`
    * New type: `Map<AlertTargetType, AlertTarget>`
    * Operation: `updateSecurityProfile`
* `UpdateSecurityProfileResponse.alertTargets`
    * Old type: `Map<String, AlertTarget>`
    * New type: `Map<AlertTargetType, AlertTarget>`
    * Operation: `updateSecurityProfile`

## Kinesis Video

* `ImageGenerationConfiguration.formatConfig`
    * Old type: `Map<String, String>`
    * New type: `Map<FormatConfigKey, String>`
    * Operations:
        * `describeImageGenerationConfiguration`
        * `updateImageGenerationConfiguration`

## Kinesis Video Archived Media

* `GetImagesInput.formatConfig`
    * Old type: `Map<String, String>`
    * New type: `Map<FormatConfigKey, String>`
    * Operation: `getImages`

## LakeFormation

* `UpdateTableStorageOptimizerRequest.storageOptimizerConfig`
    * Old type: `Map<String, Map<String, String>>`
    * New type: `Map<OptimizerType, Map<String, String>>`
    * Operation: `updateTableStorageOptimizer`

## Lambda

* `SelfManagedEventSource.endpoints`
    * Old type: `Map<String, List<String>>`
    * New type: `Map<EndPointType, List<String>>`
    * Operations:
        * `createEventSourceMapping`
        * `createEventSourceMapping`
        * `deleteEventSourceMapping`
        * `listEventSourceMappings`
        * `getEventSourceMapping`
        * `updateEventSourceMapping`

## Lex Models V2

* `IntentClassificationTestResultItemCounts.intentMatchResultCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<TestResultMatchStatus, Int>`
    * Operations:
        * `listTestExecutionResultItems`
        * `listTestExecutionResultItems`
* `IntentClassificationTestResultItemCounts.speechTranscriptionResultCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<TestResultMatchStatus, Int>`
    * Operations:
        * `listTestExecutionResultItems`
        * `listTestExecutionResultItems`
* `OverallTestResultItem.endToEndResultCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<TestResultMatchStatus, Int>`
    * Operations:
        * `listTestExecutionResultItems`
        * `listTestExecutionResultItems`
* `OverallTestResultItem.speechTranscriptionResultCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<TestResultMatchStatus, Int>`
    * Operations:
        * `listTestExecutionResultItems`
        * `listTestExecutionResultItems`
* `PromptSpecification.promptAttemptsSpecification`
    * Old type: `Map<String, StringSpecification>`
    * New type: `Map<PromptAttempt, PromptAttemptSpecification>`
    * Operations:
        * `createIntent`
        * `createIntent`
        * `describeIntent`
        * `updateIntent`
        * `updateIntent`
        * `listSlots`
        * `createSlot`
        * `createSlot`
        * `describeSlot`
        * `updateSlot`
        * `updateSlot`
        * `createSlot`
        * `createSlot`
        * `describeSlot`
        * `updateSlot`
        * `updateSlot`
* `SlotResolutionTestResultItemCounts.slotMatchResultCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<TestResultMatchStatus, Int>`
    * Operations:
        * `listTestExecutionResultItems`
        * `listTestExecutionResultItems`
* `SlotResolutionTestResultItemCounts.speechTranscriptionResultCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<TestResultMatchStatus, Int>`
    * Operations:
        * `listTestExecutionResultItems`
        * `listTestExecutionResultItems`

## Lightsail

* `LoadBalancer.configurationOptions`
    * Old type: `Map<String, String>`
    * New type: `Map<LoadBalancerAttributeName, String>`
    * Operations:
        * `getLoadBalancer`
        * `getLoadBalancers`

## Machine Learning

* `Prediction.details`
    * Old type: `Map<String, String>`
    * New type: `Map<DetailsAttributes, String>`
    * Operation: `predict`

## Marketplace Entitlement Service

* `GetEntitlementsRequest.filter`
    * Old type: `Map<String, List<String>>`
    * New type: `Map<GetEntitlementFilterName, List<String>>`
    * Operation: `getEntitlements`

## Omics

* `TsvStoreOptions.formatToHeader`
    * Old type: `Map<String, String>`
    * New type: `Map<FormatToHeaderKey, String>`
    * Operations:
        * `createAnnotationStore`
        * `createAnnotationStore`
        * `getAnnotationStore`
        * `updateAnnotationStore`
* `TsvVersionOptions.formatToHeader`
    * Old type: `Map<String, String>`
    * New type: `Map<FormatToHeaderKey, String>`
    * Operations:
        * `createAnnotationStoreVersion`
        * `createAnnotationStoreVersion`
        * `getAnnotationStoreVersion`

## OpenSearch

* `CreateDomainRequest.logPublishingOptions`
    * Old type: `Map<String, LogPublishingOption>`
    * New type: `Map<LogType, LogPublishingOption>`
    * Operation: `createDomain`
* `DomainStatus.logPublishingOptions`
    * Old type: `Map<String, LogPublishingOption>`
    * New type: `Map<LogType, LogPublishingOption>`
    * Operations:
        * `createDomain`
        * `deleteDomain`
        * `describeDomain`
        * `describeDryRunProgress`
        * `describeDomains`
* `LogPublishingOptionsStatus.options`
    * Old type: `Map<String, LogPublishingOption>`
    * New type: `Map<LogType, LogPublishingOption>`
    * Operations:
        * `describeDomainConfig`
        * `updateDomainConfig`
* `UpdateDomainConfigRequest.logPublishingOptions`
    * Old type: `Map<String, LogPublishingOption>`
    * New type: `Map<LogType, LogPublishingOption>`
    * Operation: `updateDomainConfig`

## OpsWorks

* `App.attributes`
    * Old type: `Map<String, String>`
    * New type: `Map<AppAttributesKeys, String>`
    * Operation: `describeApps`
* `CloneStackRequest.attributes`
    * Old type: `Map<String, String>`
    * New type: `Map<StackAttributesKeys, String>`
    * Operation: `cloneStack`
* `CreateAppRequest.attributes`
    * Old type: `Map<String, String>`
    * New type: `Map<AppAttributesKeys, String>`
    * Operation: `createApp`
* `CreateLayerRequest.attributes`
    * Old type: `Map<String, String>`
    * New type: `Map<LayerAttributesKeys, String>`
    * Operation: `createLayer`
* `CreateStackRequest.attributes`
    * Old type: `Map<String, String>`
    * New type: `Map<StackAttributesKeys, String>`
    * Operation: `createStack`
* `Layer.attributes`
    * Old type: `Map<String, String>`
    * New type: `Map<LayerAttributesKeys, String>`
    * Operation: `describeLayers`
* `Stack.attributes`
    * Old type: `Map<String, String>`
    * New type: `Map<StackAttributesKeys, String>`
    * Operation: `describeStacks`
* `UpdateAppRequest.attributes`
    * Old type: `Map<String, String>`
    * New type: `Map<AppAttributesKeys, String>`
    * Operation: `updateApp`
* `UpdateLayerRequest.attributes`
    * Old type: `Map<String, String>`
    * New type: `Map<LayerAttributesKeys, String>`
    * Operation: `updateLayer`
* `UpdateStackRequest.attributes`
    * Old type: `Map<String, String>`
    * New type: `Map<StackAttributesKeys, String>`
    * Operation: `updateStack`

## Outposts

* `OrderSummary.lineItemCountsByStatus`
    * Old type: `Map<String, Int>`
    * New type: `Map<LineItemStatus, Int>`
    * Operation: `listOrders`

## Pinpoint

* `OpenHours.custom`
    * Old type: `Map<String, List<OpenHoursRule>>`
    * New type: `Map<DayOfWeek, List<OpenHoursRule>>`
    * Operations:
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
* `OpenHours.email`
    * Old type: `Map<String, List<OpenHoursRule>>`
    * New type: `Map<DayOfWeek, List<OpenHoursRule>>`
    * Operations:
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
* `OpenHours.push`
    * Old type: `Map<String, List<OpenHoursRule>>`
    * New type: `Map<DayOfWeek, List<OpenHoursRule>>`
    * Operations:
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
* `OpenHours.sms`
    * Old type: `Map<String, List<OpenHoursRule>>`
    * New type: `Map<DayOfWeek, List<OpenHoursRule>>`
    * Operations:
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
* `OpenHours.voice`
    * Old type: `Map<String, List<OpenHoursRule>>`
    * New type: `Map<DayOfWeek, List<OpenHoursRule>>`
    * Operations:
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`
        * `createJourney`
        * `deleteJourney`
        * `getJourney`
        * `listJourneys`
        * `updateJourney`
        * `updateJourneyState`
        * `createJourney`
        * `updateJourney`

## Pinpoint SMS Voice V2

* `SendDestinationNumberVerificationCodeRequest.destinationCountryParameters`
    * Old type: `Map<String, String>`
    * New type: `Map<DestinationCountryParameterKey, String>`
    * Operation: `sendDestinationNumberVerificationCode`
* `SendTextMessageRequest.destinationCountryParameters`
    * Old type: `Map<String, String>`
    * New type: `Map<DestinationCountryParameterKey, String>`
    * Operation: `sendTextMessage`

## PrivateNetworks

* `ListDeviceIdentifiersRequest.filters`
    * Old type: `Map<String, List<String>>`
    * New type: `Map<DeviceIdentifierFilterKeys, List<String>>`
    * Operation: `listDeviceIdentifiers`
* `ListNetworkResourcesRequest.filters`
    * Old type: `Map<String, List<String>>`
    * New type: `Map<NetworkResourceFilterKeys, List<String>>`
    * Operation: `listNetworkResources`
* `ListNetworkSitesRequest.filters`
    * Old type: `Map<String, List<String>>`
    * New type: `Map<NetworkSiteFilterKeys, List<String>>`
    * Operation: `listNetworkSites`
* `ListNetworksRequest.filters`
    * Old type: `Map<String, List<String>>`
    * New type: `Map<NetworkFilterKeys, List<String>>`
    * Operation: `listNetworks`
* `ListOrdersRequest.filters`
    * Old type: `Map<String, List<String>>`
    * New type: `Map<OrderFilterKeys, List<String>>`
    * Operation: `listOrders`

## Resiliencehub

* `AppAssessment.compliance`
    * Old type: `Map<String, DisruptionCompliance>`
    * New type: `Map<DisruptionType, DisruptionCompliance>`
    * Operations:
        * `describeAppAssessment`
        * `startAppAssessment`
* `AppComponentCompliance.compliance`
    * Old type: `Map<String, DisruptionCompliance>`
    * New type: `Map<DisruptionType, DisruptionCompliance>`
    * Operation: `listAppComponentCompliances`
* `ComplianceDrift.actualValue`
    * Old type: `Map<String, DisruptionCompliance>`
    * New type: `Map<DisruptionType, DisruptionCompliance>`
    * Operations:
        * `listAppAssessmentComplianceDrifts`
        * `listAppAssessmentComplianceDrifts`
* `ComplianceDrift.expectedValue`
    * Old type: `Map<String, DisruptionCompliance>`
    * New type: `Map<DisruptionType, DisruptionCompliance>`
    * Operations:
        * `listAppAssessmentComplianceDrifts`
        * `listAppAssessmentComplianceDrifts`
* `ConfigRecommendation.compliance`
    * Old type: `Map<String, DisruptionCompliance>`
    * New type: `Map<DisruptionType, DisruptionCompliance>`
    * Operation: `listAppComponentRecommendations`
* `ConfigRecommendation.recommendationCompliance`
    * Old type: `Map<String, RecommendationDisruptionCompliance>`
    * New type: `Map<DisruptionType, RecommendationDisruptionCompliance>`
    * Operation: `listAppComponentRecommendations`
* `CreateResiliencyPolicyRequest.policy`
    * Old type: `Map<String, FailurePolicy>`
    * New type: `Map<DisruptionType, FailurePolicy>`
    * Operation: `createResiliencyPolicy`
* `ResiliencyPolicy.policy`
    * Old type: `Map<String, FailurePolicy>`
    * New type: `Map<DisruptionType, FailurePolicy>`
    * Operations:
        * `describeAppAssessment`
        * `startAppAssessment`
        * `createResiliencyPolicy`
        * `describeResiliencyPolicy`
        * `listResiliencyPolicies`
        * `listSuggestedResiliencyPolicies`
        * `updateResiliencyPolicy`
* `ResiliencyScore.componentScore`
    * Old type: `Map<String, ScoringComponentResiliencyScore>`
    * New type: `Map<ResiliencyScoreType, ScoringComponentResiliencyScore>`
    * Operations:
        * `describeAppAssessment`
        * `startAppAssessment`
        * `listAppComponentCompliances`
* `ResiliencyScore.disruptionScore`
    * Old type: `Map<String, Double>`
    * New type: `Map<DisruptionType, Double>`
    * Operations:
        * `describeAppAssessment`
        * `startAppAssessment`
        * `listAppComponentCompliances`
* `UpdateResiliencyPolicyRequest.policy`
    * Old type: `Map<String, FailurePolicy>`
    * New type: `Map<DisruptionType, FailurePolicy>`
    * Operation: `updateResiliencyPolicy`

## SESv2

* `BatchGetMetricDataQuery.dimensions`
    * Old type: `Map<String, String>`
    * New type: `Map<MetricDimensionName, String>`
    * Operation: `batchGetMetricData`
* `ListRecommendationsRequest.filter`
    * Old type: `Map<String, String>`
    * New type: `Map<ListRecommendationsFilterKey, String>`
    * Operation: `listRecommendations`
* `MetricsDataSource.dimensions`
    * Old type: `Map<String, List<String>>`
    * New type: `Map<MetricDimensionName, List<String>>`
    * Operations:
        * `createExportJob`
        * `getExportJob`

## SQS

* `CreateQueueRequest.attributes`
    * Old type: `Map<String, String>`
    * New type: `Map<QueueAttributeName, String>`
    * Operation: `createQueue`
* `GetQueueAttributesResult.attributes`
    * Old type: `Map<String, String>`
    * New type: `Map<QueueAttributeName, String>`
    * Operation: `getQueueAttributes`
* `Message.attributes`
    * Old type: `Map<String, String>`
    * New type: `Map<MessageSystemAttributeName, String>`
    * Operation: `receiveMessage`
* `SendMessageBatchRequestEntry.messageSystemAttributes`
    * Old type: `Map<String, MessageSystemAttributeValue>`
    * New type: `Map<MessageSystemAttributeNameForSends, MessageSystemAttributeValue>`
    * Operation: `sendMessageBatch`
* `SendMessageRequest.messageSystemAttributes`
    * Old type: `Map<String, MessageSystemAttributeValue>`
    * New type: `Map<MessageSystemAttributeNameForSends, MessageSystemAttributeValue>`
    * Operation: `sendMessage`
* `SetQueueAttributesRequest.attributes`
    * Old type: `Map<String, String>`
    * New type: `Map<QueueAttributeName, String>`
    * Operation: `setQueueAttributes`

## SSM Contacts

* `RecurrenceSettings.shiftCoverages`
    * Old type: `Map<String, List<CoverageTime>>`
    * New type: `Map<DayOfWeek, List<CoverageTime>>`
    * Operations:
        * `createRotation`
        * `getRotation`
        * `listPreviewRotationShifts`
        * `listRotations`
        * `updateRotation`

## SageMaker

* `AutoMlCandidate.inferenceContainerDefinitions`
    * Old type: `Map<String, List<AutoMlContainerDefinition>>`
    * New type: `Map<AutoMlProcessingUnit, List<AutoMlContainerDefinition>>`
    * Operations:
        * `listCandidatesForAutoMlJob`
        * `describeAutoMlJob`
        * `describeAutoMlJobV2`
* `TimeSeriesTransformations.filling`
    * Old type: `Map<String, Map<String, String>>`
    * New type: `Map<String, Map<FillingType, String>>`
    * Operations:
        * `createAutoMlJobV2`
        * `describeAutoMlJobV2`

## Service Catalog

* `CopyProductInput.sourceProvisioningArtifactIdentifiers`
    * Old type: `List<Map<String, String>>`
    * New type: `List<Map<ProvisioningArtifactPropertyName, String>>`
    * Operation: `copyProduct`
* `CreateServiceActionInput.definition`
    * Old type: `Map<String, String>`
    * New type: `Map<ServiceActionDefinitionKey, String>`
    * Operation: `createServiceAction`
* `SearchProductsAsAdminInput.filters`
    * Old type: `Map<String, List<String>>`
    * New type: `Map<ProductViewFilterBy, List<String>>`
    * Operation: `searchProductsAsAdmin`
* `SearchProductsInput.filters`
    * Old type: `Map<String, List<String>>`
    * New type: `Map<ProductViewFilterBy, List<String>>`
    * Operation: `searchProducts`
* `SearchProvisionedProductsInput.filters`
    * Old type: `Map<String, List<String>>`
    * New type: `Map<ProvisionedProductViewFilterBy, List<String>>`
    * Operation: `searchProvisionedProducts`
* `ServiceActionDetail.definition`
    * Old type: `Map<String, String>`
    * New type: `Map<ServiceActionDefinitionKey, String>`
    * Operations:
        * `createServiceAction`
        * `describeServiceAction`
        * `updateServiceAction`
* `UpdateProvisionedProductPropertiesInput.provisionedProductProperties`
    * Old type: `Map<String, String>`
    * New type: `Map<PropertyKey, String>`
    * Operation: `updateProvisionedProductProperties`
* `UpdateProvisionedProductPropertiesOutput.provisionedProductProperties`
    * Old type: `Map<String, String>`
    * New type: `Map<PropertyKey, String>`
    * Operation: `updateProvisionedProductProperties`
* `UpdateServiceActionInput.definition`
    * Old type: `Map<String, String>`
    * New type: `Map<ServiceActionDefinitionKey, String>`
    * Operation: `updateServiceAction`

## ServiceDiscovery

* `Operation.targets`
    * Old type: `Map<String, String>`
    * New type: `Map<OperationTargetType, String>`
    * Operation: `getOperation`

## Transcribe

* `CallAnalyticsJobSettings.languageIdSettings`
    * Old type: `Map<String, LanguageIdSettings>`
    * New type: `Map<LanguageCode, LanguageIdSettings>`
    * Operations:
        * `getCallAnalyticsJob`
        * `startCallAnalyticsJob`
        * `startCallAnalyticsJob`
* `StartTranscriptionJobRequest.languageIdSettings`
    * Old type: `Map<String, LanguageIdSettings>`
    * New type: `Map<LanguageCode, LanguageIdSettings>`
    * Operation: `startTranscriptionJob`
* `TranscriptionJob.languageIdSettings`
    * Old type: `Map<String, LanguageIdSettings>`
    * New type: `Map<LanguageCode, LanguageIdSettings>`
    * Operations:
        * `getTranscriptionJob`
        * `startTranscriptionJob`

## WAFV2

* `AssociationConfig.requestBody`
    * Old type: `Map<String, RequestBodyStringConfig>`
    * New type: `Map<AssociatedResourceType, RequestBodyAssociatedResourceTypeConfig>`
    * Operations:
        * `createWebAcl`
        * `updateWebAcl`
        * `getWebAclForResource`
        * `getWebAcl`

## WellArchitected

* `CheckSummary.accountSummary`
    * Old type: `Map<String, Int>`
    * New type: `Map<CheckStatus, Int>`
    * Operation: `listCheckSummaries`
* `ConsolidatedReportMetric.riskCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<Risk, Int>`
    * Operation: `getConsolidatedReport`
* `LensMetric.riskCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<Risk, Int>`
    * Operation: `getConsolidatedReport`
* `LensReview.prioritizedRiskCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<Risk, Int>`
    * Operations:
        * `getLensReview`
        * `updateLensReview`
        * `getLensReview`
        * `updateLensReview`
* `LensReview.riskCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<Risk, Int>`
    * Operations:
        * `getLensReview`
        * `updateLensReview`
        * `getLensReview`
        * `updateLensReview`
* `LensReviewSummary.prioritizedRiskCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<Risk, Int>`
    * Operations:
        * `listLensReviews`
        * `listLensReviews`
* `LensReviewSummary.riskCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<Risk, Int>`
    * Operations:
        * `listLensReviews`
        * `listLensReviews`
* `PillarMetric.riskCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<Risk, Int>`
    * Operation: `getConsolidatedReport`
* `PillarReviewSummary.prioritizedRiskCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<Risk, Int>`
    * Operations:
        * `getLensReview`
        * `updateLensReview`
        * `getLensReview`
        * `updateLensReview`
* `PillarReviewSummary.riskCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<Risk, Int>`
    * Operations:
        * `getLensReview`
        * `updateLensReview`
        * `getLensReview`
        * `updateLensReview`
* `ReviewTemplate.questionCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<Question, Int>`
    * Operations:
        * `getReviewTemplate`
        * `updateReviewTemplate`
* `ReviewTemplateLensReview.questionCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<Question, Int>`
    * Operations:
        * `getReviewTemplateLensReview`
        * `updateReviewTemplateLensReview`
* `ReviewTemplatePillarReviewSummary.questionCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<Question, Int>`
    * Operations:
        * `getReviewTemplateLensReview`
        * `updateReviewTemplateLensReview`
* `Workload.prioritizedRiskCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<Risk, Int>`
    * Operations:
        * `getWorkload`
        * `getMilestone`
        * `updateWorkload`
        * `getWorkload`
        * `getMilestone`
        * `updateWorkload`
* `Workload.riskCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<Risk, Int>`
    * Operations:
        * `getWorkload`
        * `getMilestone`
        * `updateWorkload`
        * `getWorkload`
        * `getMilestone`
        * `updateWorkload`
* `WorkloadSummary.prioritizedRiskCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<Risk, Int>`
    * Operations:
        * `listMilestones`
        * `listWorkloads`
        * `listMilestones`
        * `listWorkloads`
* `WorkloadSummary.riskCounts`
    * Old type: `Map<String, Int>`
    * New type: `Map<Risk, Int>`
    * Operations:
        * `listMilestones`
        * `listWorkloads`
        * `listMilestones`
        * `listWorkloads`

## WorkDocs

* `DocumentVersionMetadata.source`
    * Old type: `Map<String, String>`
    * New type: `Map<DocumentSourceType, String>`
    * Operations:
        * `describeFolderContents`
        * `getResources`
        * `getDocument`
        * `initiateDocumentVersionUpload`
        * `searchResources`
        * `describeDocumentVersions`
        * `getDocumentVersion`
        * `searchResources`
* `DocumentVersionMetadata.thumbnail`
    * Old type: `Map<String, String>`
    * New type: `Map<DocumentThumbnailType, String>`
    * Operations:
        * `describeFolderContents`
        * `getResources`
        * `getDocument`
        * `initiateDocumentVersionUpload`
        * `searchResources`
        * `describeDocumentVersions`
        * `getDocumentVersion`
        * `searchResources`

# Feedback

If you have any questions concerning this change, please feel free to engage with us in this discussion. If you encounter a bug with these changes, please [file an issue](https://github.com/awslabs/aws-sdk-kotlin/issues/new/choose).
