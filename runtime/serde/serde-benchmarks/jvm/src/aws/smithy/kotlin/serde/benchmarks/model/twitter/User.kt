/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

// Code generated by smithy-kotlin-codegen. DO NOT EDIT!

package aws.smithy.kotlin.serde.benchmarks.model.twitter



class User private constructor(builder: BuilderImpl) {
    val contributorsEnabled: Boolean? = builder.contributorsEnabled
    val createdAt: String? = builder.createdAt
    val defaultProfile: Boolean? = builder.defaultProfile
    val defaultProfileImage: Boolean? = builder.defaultProfileImage
    val description: String? = builder.description
    val favouritesCount: Int? = builder.favouritesCount
    val followRequestSent: Boolean? = builder.followRequestSent
    val followersCount: Int? = builder.followersCount
    val following: Boolean? = builder.following
    val friendsCount: Int? = builder.friendsCount
    val geoEnabled: Boolean? = builder.geoEnabled
    val id: Int? = builder.id
    val idStr: String? = builder.idStr
    val isTranslator: Boolean? = builder.isTranslator
    val lang: String? = builder.lang
    val listedCount: Int? = builder.listedCount
    val location: String? = builder.location
    val name: String? = builder.name
    val notifications: Boolean? = builder.notifications
    val profileBackgroundColor: String? = builder.profileBackgroundColor
    val profileBackgroundImageUrl: String? = builder.profileBackgroundImageUrl
    val profileBackgroundImageUrlHttps: String? = builder.profileBackgroundImageUrlHttps
    val profileBackgroundTile: Boolean? = builder.profileBackgroundTile
    val profileImageUrl: String? = builder.profileImageUrl
    val profileImageUrlHttps: String? = builder.profileImageUrlHttps
    val profileLinkColor: String? = builder.profileLinkColor
    val profileSidebarBorderColor: String? = builder.profileSidebarBorderColor
    val profileSidebarFillColor: String? = builder.profileSidebarFillColor
    val profileTextColor: String? = builder.profileTextColor
    val profileUseBackgroundImage: Boolean? = builder.profileUseBackgroundImage
    val protected: Boolean? = builder.protected
    val screenName: String? = builder.screenName
    val showAllInlineMedia: Boolean? = builder.showAllInlineMedia
    val statusesCount: Int? = builder.statusesCount
    val timeZone: String? = builder.timeZone
    val url: String? = builder.url
    val utcOffset: Int? = builder.utcOffset
    val verified: Boolean? = builder.verified

    companion object {
        @JvmStatic
        fun fluentBuilder(): FluentBuilder = BuilderImpl()

        fun builder(): DslBuilder = BuilderImpl()

        operator fun invoke(block: DslBuilder.() -> kotlin.Unit): User = BuilderImpl().apply(block).build()

    }

    override fun toString(): kotlin.String = buildString {
        append("User(")
        append("contributorsEnabled=$contributorsEnabled,")
        append("createdAt=$createdAt,")
        append("defaultProfile=$defaultProfile,")
        append("defaultProfileImage=$defaultProfileImage,")
        append("description=$description,")
        append("favouritesCount=$favouritesCount,")
        append("followRequestSent=$followRequestSent,")
        append("followersCount=$followersCount,")
        append("following=$following,")
        append("friendsCount=$friendsCount,")
        append("geoEnabled=$geoEnabled,")
        append("id=$id,")
        append("idStr=$idStr,")
        append("isTranslator=$isTranslator,")
        append("lang=$lang,")
        append("listedCount=$listedCount,")
        append("location=$location,")
        append("name=$name,")
        append("notifications=$notifications,")
        append("profileBackgroundColor=$profileBackgroundColor,")
        append("profileBackgroundImageUrl=$profileBackgroundImageUrl,")
        append("profileBackgroundImageUrlHttps=$profileBackgroundImageUrlHttps,")
        append("profileBackgroundTile=$profileBackgroundTile,")
        append("profileImageUrl=$profileImageUrl,")
        append("profileImageUrlHttps=$profileImageUrlHttps,")
        append("profileLinkColor=$profileLinkColor,")
        append("profileSidebarBorderColor=$profileSidebarBorderColor,")
        append("profileSidebarFillColor=$profileSidebarFillColor,")
        append("profileTextColor=$profileTextColor,")
        append("profileUseBackgroundImage=$profileUseBackgroundImage,")
        append("protected=$protected,")
        append("screenName=$screenName,")
        append("showAllInlineMedia=$showAllInlineMedia,")
        append("statusesCount=$statusesCount,")
        append("timeZone=$timeZone,")
        append("url=$url,")
        append("utcOffset=$utcOffset,")
        append("verified=$verified)")
    }

    override fun hashCode(): kotlin.Int {
        var result = contributorsEnabled?.hashCode() ?: 0
        result = 31 * result + (createdAt?.hashCode() ?: 0)
        result = 31 * result + (defaultProfile?.hashCode() ?: 0)
        result = 31 * result + (defaultProfileImage?.hashCode() ?: 0)
        result = 31 * result + (description?.hashCode() ?: 0)
        result = 31 * result + (favouritesCount ?: 0)
        result = 31 * result + (followRequestSent?.hashCode() ?: 0)
        result = 31 * result + (followersCount ?: 0)
        result = 31 * result + (following?.hashCode() ?: 0)
        result = 31 * result + (friendsCount ?: 0)
        result = 31 * result + (geoEnabled?.hashCode() ?: 0)
        result = 31 * result + (id ?: 0)
        result = 31 * result + (idStr?.hashCode() ?: 0)
        result = 31 * result + (isTranslator?.hashCode() ?: 0)
        result = 31 * result + (lang?.hashCode() ?: 0)
        result = 31 * result + (listedCount ?: 0)
        result = 31 * result + (location?.hashCode() ?: 0)
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (notifications?.hashCode() ?: 0)
        result = 31 * result + (profileBackgroundColor?.hashCode() ?: 0)
        result = 31 * result + (profileBackgroundImageUrl?.hashCode() ?: 0)
        result = 31 * result + (profileBackgroundImageUrlHttps?.hashCode() ?: 0)
        result = 31 * result + (profileBackgroundTile?.hashCode() ?: 0)
        result = 31 * result + (profileImageUrl?.hashCode() ?: 0)
        result = 31 * result + (profileImageUrlHttps?.hashCode() ?: 0)
        result = 31 * result + (profileLinkColor?.hashCode() ?: 0)
        result = 31 * result + (profileSidebarBorderColor?.hashCode() ?: 0)
        result = 31 * result + (profileSidebarFillColor?.hashCode() ?: 0)
        result = 31 * result + (profileTextColor?.hashCode() ?: 0)
        result = 31 * result + (profileUseBackgroundImage?.hashCode() ?: 0)
        result = 31 * result + (protected?.hashCode() ?: 0)
        result = 31 * result + (screenName?.hashCode() ?: 0)
        result = 31 * result + (showAllInlineMedia?.hashCode() ?: 0)
        result = 31 * result + (statusesCount ?: 0)
        result = 31 * result + (timeZone?.hashCode() ?: 0)
        result = 31 * result + (url?.hashCode() ?: 0)
        result = 31 * result + (utcOffset ?: 0)
        result = 31 * result + (verified?.hashCode() ?: 0)
        return result
    }

    override fun equals(other: kotlin.Any?): kotlin.Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as User

        if (contributorsEnabled != other.contributorsEnabled) return false
        if (createdAt != other.createdAt) return false
        if (defaultProfile != other.defaultProfile) return false
        if (defaultProfileImage != other.defaultProfileImage) return false
        if (description != other.description) return false
        if (favouritesCount != other.favouritesCount) return false
        if (followRequestSent != other.followRequestSent) return false
        if (followersCount != other.followersCount) return false
        if (following != other.following) return false
        if (friendsCount != other.friendsCount) return false
        if (geoEnabled != other.geoEnabled) return false
        if (id != other.id) return false
        if (idStr != other.idStr) return false
        if (isTranslator != other.isTranslator) return false
        if (lang != other.lang) return false
        if (listedCount != other.listedCount) return false
        if (location != other.location) return false
        if (name != other.name) return false
        if (notifications != other.notifications) return false
        if (profileBackgroundColor != other.profileBackgroundColor) return false
        if (profileBackgroundImageUrl != other.profileBackgroundImageUrl) return false
        if (profileBackgroundImageUrlHttps != other.profileBackgroundImageUrlHttps) return false
        if (profileBackgroundTile != other.profileBackgroundTile) return false
        if (profileImageUrl != other.profileImageUrl) return false
        if (profileImageUrlHttps != other.profileImageUrlHttps) return false
        if (profileLinkColor != other.profileLinkColor) return false
        if (profileSidebarBorderColor != other.profileSidebarBorderColor) return false
        if (profileSidebarFillColor != other.profileSidebarFillColor) return false
        if (profileTextColor != other.profileTextColor) return false
        if (profileUseBackgroundImage != other.profileUseBackgroundImage) return false
        if (protected != other.protected) return false
        if (screenName != other.screenName) return false
        if (showAllInlineMedia != other.showAllInlineMedia) return false
        if (statusesCount != other.statusesCount) return false
        if (timeZone != other.timeZone) return false
        if (url != other.url) return false
        if (utcOffset != other.utcOffset) return false
        if (verified != other.verified) return false

        return true
    }

    fun copy(block: DslBuilder.() -> kotlin.Unit = {}): User = BuilderImpl(this).apply(block).build()

    interface FluentBuilder {
        fun build(): User
        fun contributorsEnabled(contributorsEnabled: Boolean): FluentBuilder
        fun createdAt(createdAt: String): FluentBuilder
        fun defaultProfile(defaultProfile: Boolean): FluentBuilder
        fun defaultProfileImage(defaultProfileImage: Boolean): FluentBuilder
        fun description(description: String): FluentBuilder
        fun favouritesCount(favouritesCount: Int): FluentBuilder
        fun followRequestSent(followRequestSent: Boolean): FluentBuilder
        fun followersCount(followersCount: Int): FluentBuilder
        fun following(following: Boolean): FluentBuilder
        fun friendsCount(friendsCount: Int): FluentBuilder
        fun geoEnabled(geoEnabled: Boolean): FluentBuilder
        fun id(id: Int): FluentBuilder
        fun idStr(idStr: String): FluentBuilder
        fun isTranslator(isTranslator: Boolean): FluentBuilder
        fun lang(lang: String): FluentBuilder
        fun listedCount(listedCount: Int): FluentBuilder
        fun location(location: String): FluentBuilder
        fun name(name: String): FluentBuilder
        fun notifications(notifications: Boolean): FluentBuilder
        fun profileBackgroundColor(profileBackgroundColor: String): FluentBuilder
        fun profileBackgroundImageUrl(profileBackgroundImageUrl: String): FluentBuilder
        fun profileBackgroundImageUrlHttps(profileBackgroundImageUrlHttps: String): FluentBuilder
        fun profileBackgroundTile(profileBackgroundTile: Boolean): FluentBuilder
        fun profileImageUrl(profileImageUrl: String): FluentBuilder
        fun profileImageUrlHttps(profileImageUrlHttps: String): FluentBuilder
        fun profileLinkColor(profileLinkColor: String): FluentBuilder
        fun profileSidebarBorderColor(profileSidebarBorderColor: String): FluentBuilder
        fun profileSidebarFillColor(profileSidebarFillColor: String): FluentBuilder
        fun profileTextColor(profileTextColor: String): FluentBuilder
        fun profileUseBackgroundImage(profileUseBackgroundImage: Boolean): FluentBuilder
        fun protected(protected: Boolean): FluentBuilder
        fun screenName(screenName: String): FluentBuilder
        fun showAllInlineMedia(showAllInlineMedia: Boolean): FluentBuilder
        fun statusesCount(statusesCount: Int): FluentBuilder
        fun timeZone(timeZone: String): FluentBuilder
        fun url(url: String): FluentBuilder
        fun utcOffset(utcOffset: Int): FluentBuilder
        fun verified(verified: Boolean): FluentBuilder
    }

    interface DslBuilder {
        var contributorsEnabled: Boolean?
        var createdAt: String?
        var defaultProfile: Boolean?
        var defaultProfileImage: Boolean?
        var description: String?
        var favouritesCount: Int?
        var followRequestSent: Boolean?
        var followersCount: Int?
        var following: Boolean?
        var friendsCount: Int?
        var geoEnabled: Boolean?
        var id: Int?
        var idStr: String?
        var isTranslator: Boolean?
        var lang: String?
        var listedCount: Int?
        var location: String?
        var name: String?
        var notifications: Boolean?
        var profileBackgroundColor: String?
        var profileBackgroundImageUrl: String?
        var profileBackgroundImageUrlHttps: String?
        var profileBackgroundTile: Boolean?
        var profileImageUrl: String?
        var profileImageUrlHttps: String?
        var profileLinkColor: String?
        var profileSidebarBorderColor: String?
        var profileSidebarFillColor: String?
        var profileTextColor: String?
        var profileUseBackgroundImage: Boolean?
        var protected: Boolean?
        var screenName: String?
        var showAllInlineMedia: Boolean?
        var statusesCount: Int?
        var timeZone: String?
        var url: String?
        var utcOffset: Int?
        var verified: Boolean?

        fun build(): User
    }

    private class BuilderImpl() : FluentBuilder, DslBuilder {
        override var contributorsEnabled: Boolean? = null
        override var createdAt: String? = null
        override var defaultProfile: Boolean? = null
        override var defaultProfileImage: Boolean? = null
        override var description: String? = null
        override var favouritesCount: Int? = null
        override var followRequestSent: Boolean? = null
        override var followersCount: Int? = null
        override var following: Boolean? = null
        override var friendsCount: Int? = null
        override var geoEnabled: Boolean? = null
        override var id: Int? = null
        override var idStr: String? = null
        override var isTranslator: Boolean? = null
        override var lang: String? = null
        override var listedCount: Int? = null
        override var location: String? = null
        override var name: String? = null
        override var notifications: Boolean? = null
        override var profileBackgroundColor: String? = null
        override var profileBackgroundImageUrl: String? = null
        override var profileBackgroundImageUrlHttps: String? = null
        override var profileBackgroundTile: Boolean? = null
        override var profileImageUrl: String? = null
        override var profileImageUrlHttps: String? = null
        override var profileLinkColor: String? = null
        override var profileSidebarBorderColor: String? = null
        override var profileSidebarFillColor: String? = null
        override var profileTextColor: String? = null
        override var profileUseBackgroundImage: Boolean? = null
        override var protected: Boolean? = null
        override var screenName: String? = null
        override var showAllInlineMedia: Boolean? = null
        override var statusesCount: Int? = null
        override var timeZone: String? = null
        override var url: String? = null
        override var utcOffset: Int? = null
        override var verified: Boolean? = null

        constructor(x: User) : this() {
            this.contributorsEnabled = x.contributorsEnabled
            this.createdAt = x.createdAt
            this.defaultProfile = x.defaultProfile
            this.defaultProfileImage = x.defaultProfileImage
            this.description = x.description
            this.favouritesCount = x.favouritesCount
            this.followRequestSent = x.followRequestSent
            this.followersCount = x.followersCount
            this.following = x.following
            this.friendsCount = x.friendsCount
            this.geoEnabled = x.geoEnabled
            this.id = x.id
            this.idStr = x.idStr
            this.isTranslator = x.isTranslator
            this.lang = x.lang
            this.listedCount = x.listedCount
            this.location = x.location
            this.name = x.name
            this.notifications = x.notifications
            this.profileBackgroundColor = x.profileBackgroundColor
            this.profileBackgroundImageUrl = x.profileBackgroundImageUrl
            this.profileBackgroundImageUrlHttps = x.profileBackgroundImageUrlHttps
            this.profileBackgroundTile = x.profileBackgroundTile
            this.profileImageUrl = x.profileImageUrl
            this.profileImageUrlHttps = x.profileImageUrlHttps
            this.profileLinkColor = x.profileLinkColor
            this.profileSidebarBorderColor = x.profileSidebarBorderColor
            this.profileSidebarFillColor = x.profileSidebarFillColor
            this.profileTextColor = x.profileTextColor
            this.profileUseBackgroundImage = x.profileUseBackgroundImage
            this.protected = x.protected
            this.screenName = x.screenName
            this.showAllInlineMedia = x.showAllInlineMedia
            this.statusesCount = x.statusesCount
            this.timeZone = x.timeZone
            this.url = x.url
            this.utcOffset = x.utcOffset
            this.verified = x.verified
        }

        override fun build(): User = User(this)
        override fun contributorsEnabled(contributorsEnabled: Boolean): FluentBuilder = apply { this.contributorsEnabled = contributorsEnabled }
        override fun createdAt(createdAt: String): FluentBuilder = apply { this.createdAt = createdAt }
        override fun defaultProfile(defaultProfile: Boolean): FluentBuilder = apply { this.defaultProfile = defaultProfile }
        override fun defaultProfileImage(defaultProfileImage: Boolean): FluentBuilder = apply { this.defaultProfileImage = defaultProfileImage }
        override fun description(description: String): FluentBuilder = apply { this.description = description }
        override fun favouritesCount(favouritesCount: Int): FluentBuilder = apply { this.favouritesCount = favouritesCount }
        override fun followRequestSent(followRequestSent: Boolean): FluentBuilder = apply { this.followRequestSent = followRequestSent }
        override fun followersCount(followersCount: Int): FluentBuilder = apply { this.followersCount = followersCount }
        override fun following(following: Boolean): FluentBuilder = apply { this.following = following }
        override fun friendsCount(friendsCount: Int): FluentBuilder = apply { this.friendsCount = friendsCount }
        override fun geoEnabled(geoEnabled: Boolean): FluentBuilder = apply { this.geoEnabled = geoEnabled }
        override fun id(id: Int): FluentBuilder = apply { this.id = id }
        override fun idStr(idStr: String): FluentBuilder = apply { this.idStr = idStr }
        override fun isTranslator(isTranslator: Boolean): FluentBuilder = apply { this.isTranslator = isTranslator }
        override fun lang(lang: String): FluentBuilder = apply { this.lang = lang }
        override fun listedCount(listedCount: Int): FluentBuilder = apply { this.listedCount = listedCount }
        override fun location(location: String): FluentBuilder = apply { this.location = location }
        override fun name(name: String): FluentBuilder = apply { this.name = name }
        override fun notifications(notifications: Boolean): FluentBuilder = apply { this.notifications = notifications }
        override fun profileBackgroundColor(profileBackgroundColor: String): FluentBuilder = apply { this.profileBackgroundColor = profileBackgroundColor }
        override fun profileBackgroundImageUrl(profileBackgroundImageUrl: String): FluentBuilder = apply { this.profileBackgroundImageUrl = profileBackgroundImageUrl }
        override fun profileBackgroundImageUrlHttps(profileBackgroundImageUrlHttps: String): FluentBuilder = apply { this.profileBackgroundImageUrlHttps = profileBackgroundImageUrlHttps }
        override fun profileBackgroundTile(profileBackgroundTile: Boolean): FluentBuilder = apply { this.profileBackgroundTile = profileBackgroundTile }
        override fun profileImageUrl(profileImageUrl: String): FluentBuilder = apply { this.profileImageUrl = profileImageUrl }
        override fun profileImageUrlHttps(profileImageUrlHttps: String): FluentBuilder = apply { this.profileImageUrlHttps = profileImageUrlHttps }
        override fun profileLinkColor(profileLinkColor: String): FluentBuilder = apply { this.profileLinkColor = profileLinkColor }
        override fun profileSidebarBorderColor(profileSidebarBorderColor: String): FluentBuilder = apply { this.profileSidebarBorderColor = profileSidebarBorderColor }
        override fun profileSidebarFillColor(profileSidebarFillColor: String): FluentBuilder = apply { this.profileSidebarFillColor = profileSidebarFillColor }
        override fun profileTextColor(profileTextColor: String): FluentBuilder = apply { this.profileTextColor = profileTextColor }
        override fun profileUseBackgroundImage(profileUseBackgroundImage: Boolean): FluentBuilder = apply { this.profileUseBackgroundImage = profileUseBackgroundImage }
        override fun protected(protected: Boolean): FluentBuilder = apply { this.protected = protected }
        override fun screenName(screenName: String): FluentBuilder = apply { this.screenName = screenName }
        override fun showAllInlineMedia(showAllInlineMedia: Boolean): FluentBuilder = apply { this.showAllInlineMedia = showAllInlineMedia }
        override fun statusesCount(statusesCount: Int): FluentBuilder = apply { this.statusesCount = statusesCount }
        override fun timeZone(timeZone: String): FluentBuilder = apply { this.timeZone = timeZone }
        override fun url(url: String): FluentBuilder = apply { this.url = url }
        override fun utcOffset(utcOffset: Int): FluentBuilder = apply { this.utcOffset = utcOffset }
        override fun verified(verified: Boolean): FluentBuilder = apply { this.verified = verified }
    }
}
