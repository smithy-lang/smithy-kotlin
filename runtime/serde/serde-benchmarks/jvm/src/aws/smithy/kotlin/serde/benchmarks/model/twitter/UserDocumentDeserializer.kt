/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

// Code generated by smithy-kotlin-codegen. DO NOT EDIT!

package aws.smithy.kotlin.serde.benchmarks.model.twitter

import aws.smithy.kotlin.runtime.serde.Deserializer
import aws.smithy.kotlin.runtime.serde.SdkFieldDescriptor
import aws.smithy.kotlin.runtime.serde.SdkObjectDescriptor
import aws.smithy.kotlin.runtime.serde.SerialKind
import aws.smithy.kotlin.runtime.serde.deserializeStruct
import aws.smithy.kotlin.runtime.serde.json.JsonSerialName


internal suspend fun deserializeUserDocument(deserializer: Deserializer): User {
    val builder = User.builder()
    val CONTRIBUTORSENABLED_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Boolean, JsonSerialName("contributors_enabled"))
    val CREATEDAT_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("created_at"))
    val DEFAULTPROFILE_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Boolean, JsonSerialName("default_profile"))
    val DEFAULTPROFILEIMAGE_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Boolean, JsonSerialName("default_profile_image"))
    val DESCRIPTION_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("description"))
    val FAVOURITESCOUNT_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("favourites_count"))
    val FOLLOWREQUESTSENT_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Boolean, JsonSerialName("follow_request_sent"))
    val FOLLOWERSCOUNT_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("followers_count"))
    val FOLLOWING_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Boolean, JsonSerialName("following"))
    val FRIENDSCOUNT_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("friends_count"))
    val GEOENABLED_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Boolean, JsonSerialName("geo_enabled"))
    val ID_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("id"))
    val IDSTR_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("id_str"))
    val ISTRANSLATOR_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Boolean, JsonSerialName("is_translator"))
    val LANG_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("lang"))
    val LISTEDCOUNT_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("listed_count"))
    val LOCATION_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("location"))
    val NAME_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("name"))
    val NOTIFICATIONS_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Boolean, JsonSerialName("notifications"))
    val PROFILEBACKGROUNDCOLOR_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("profile_background_color"))
    val PROFILEBACKGROUNDIMAGEURL_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("profile_background_image_url"))
    val PROFILEBACKGROUNDIMAGEURLHTTPS_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("profile_background_image_url_https"))
    val PROFILEBACKGROUNDTILE_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Boolean, JsonSerialName("profile_background_tile"))
    val PROFILEIMAGEURL_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("profile_image_url"))
    val PROFILEIMAGEURLHTTPS_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("profile_image_url_https"))
    val PROFILELINKCOLOR_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("profile_link_color"))
    val PROFILESIDEBARBORDERCOLOR_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("profile_sidebar_border_color"))
    val PROFILESIDEBARFILLCOLOR_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("profile_sidebar_fill_color"))
    val PROFILETEXTCOLOR_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("profile_text_color"))
    val PROFILEUSEBACKGROUNDIMAGE_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Boolean, JsonSerialName("profile_use_background_image"))
    val PROTECTED_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Boolean, JsonSerialName("protected"))
    val SCREENNAME_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("screen_name"))
    val SHOWALLINLINEMEDIA_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Boolean, JsonSerialName("show_all_inline_media"))
    val STATUSESCOUNT_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("statuses_count"))
    val TIMEZONE_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("time_zone"))
    val URL_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, JsonSerialName("url"))
    val UTCOFFSET_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, JsonSerialName("utc_offset"))
    val VERIFIED_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Boolean, JsonSerialName("verified"))
    val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
        field(CONTRIBUTORSENABLED_DESCRIPTOR)
        field(CREATEDAT_DESCRIPTOR)
        field(DEFAULTPROFILE_DESCRIPTOR)
        field(DEFAULTPROFILEIMAGE_DESCRIPTOR)
        field(DESCRIPTION_DESCRIPTOR)
        field(FAVOURITESCOUNT_DESCRIPTOR)
        field(FOLLOWREQUESTSENT_DESCRIPTOR)
        field(FOLLOWERSCOUNT_DESCRIPTOR)
        field(FOLLOWING_DESCRIPTOR)
        field(FRIENDSCOUNT_DESCRIPTOR)
        field(GEOENABLED_DESCRIPTOR)
        field(ID_DESCRIPTOR)
        field(IDSTR_DESCRIPTOR)
        field(ISTRANSLATOR_DESCRIPTOR)
        field(LANG_DESCRIPTOR)
        field(LISTEDCOUNT_DESCRIPTOR)
        field(LOCATION_DESCRIPTOR)
        field(NAME_DESCRIPTOR)
        field(NOTIFICATIONS_DESCRIPTOR)
        field(PROFILEBACKGROUNDCOLOR_DESCRIPTOR)
        field(PROFILEBACKGROUNDIMAGEURL_DESCRIPTOR)
        field(PROFILEBACKGROUNDIMAGEURLHTTPS_DESCRIPTOR)
        field(PROFILEBACKGROUNDTILE_DESCRIPTOR)
        field(PROFILEIMAGEURL_DESCRIPTOR)
        field(PROFILEIMAGEURLHTTPS_DESCRIPTOR)
        field(PROFILELINKCOLOR_DESCRIPTOR)
        field(PROFILESIDEBARBORDERCOLOR_DESCRIPTOR)
        field(PROFILESIDEBARFILLCOLOR_DESCRIPTOR)
        field(PROFILETEXTCOLOR_DESCRIPTOR)
        field(PROFILEUSEBACKGROUNDIMAGE_DESCRIPTOR)
        field(PROTECTED_DESCRIPTOR)
        field(SCREENNAME_DESCRIPTOR)
        field(SHOWALLINLINEMEDIA_DESCRIPTOR)
        field(STATUSESCOUNT_DESCRIPTOR)
        field(TIMEZONE_DESCRIPTOR)
        field(URL_DESCRIPTOR)
        field(UTCOFFSET_DESCRIPTOR)
        field(VERIFIED_DESCRIPTOR)
    }

    deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
        loop@while (true) {
            when (findNextFieldIndex()) {
                CONTRIBUTORSENABLED_DESCRIPTOR.index -> builder.contributorsEnabled = deserializeBoolean()
                CREATEDAT_DESCRIPTOR.index -> builder.createdAt = deserializeString()
                DEFAULTPROFILE_DESCRIPTOR.index -> builder.defaultProfile = deserializeBoolean()
                DEFAULTPROFILEIMAGE_DESCRIPTOR.index -> builder.defaultProfileImage = deserializeBoolean()
                DESCRIPTION_DESCRIPTOR.index -> builder.description = deserializeString()
                FAVOURITESCOUNT_DESCRIPTOR.index -> builder.favouritesCount = deserializeInt()
                FOLLOWREQUESTSENT_DESCRIPTOR.index -> builder.followRequestSent = deserializeBoolean()
                FOLLOWERSCOUNT_DESCRIPTOR.index -> builder.followersCount = deserializeInt()
                FOLLOWING_DESCRIPTOR.index -> builder.following = deserializeBoolean()
                FRIENDSCOUNT_DESCRIPTOR.index -> builder.friendsCount = deserializeInt()
                GEOENABLED_DESCRIPTOR.index -> builder.geoEnabled = deserializeBoolean()
                ID_DESCRIPTOR.index -> builder.id = deserializeInt()
                IDSTR_DESCRIPTOR.index -> builder.idStr = deserializeString()
                ISTRANSLATOR_DESCRIPTOR.index -> builder.isTranslator = deserializeBoolean()
                LANG_DESCRIPTOR.index -> builder.lang = deserializeString()
                LISTEDCOUNT_DESCRIPTOR.index -> builder.listedCount = deserializeInt()
                LOCATION_DESCRIPTOR.index -> builder.location = deserializeString()
                NAME_DESCRIPTOR.index -> builder.name = deserializeString()
                NOTIFICATIONS_DESCRIPTOR.index -> builder.notifications = deserializeBoolean()
                PROFILEBACKGROUNDCOLOR_DESCRIPTOR.index -> builder.profileBackgroundColor = deserializeString()
                PROFILEBACKGROUNDIMAGEURL_DESCRIPTOR.index -> builder.profileBackgroundImageUrl = deserializeString()
                PROFILEBACKGROUNDIMAGEURLHTTPS_DESCRIPTOR.index -> builder.profileBackgroundImageUrlHttps = deserializeString()
                PROFILEBACKGROUNDTILE_DESCRIPTOR.index -> builder.profileBackgroundTile = deserializeBoolean()
                PROFILEIMAGEURL_DESCRIPTOR.index -> builder.profileImageUrl = deserializeString()
                PROFILEIMAGEURLHTTPS_DESCRIPTOR.index -> builder.profileImageUrlHttps = deserializeString()
                PROFILELINKCOLOR_DESCRIPTOR.index -> builder.profileLinkColor = deserializeString()
                PROFILESIDEBARBORDERCOLOR_DESCRIPTOR.index -> builder.profileSidebarBorderColor = deserializeString()
                PROFILESIDEBARFILLCOLOR_DESCRIPTOR.index -> builder.profileSidebarFillColor = deserializeString()
                PROFILETEXTCOLOR_DESCRIPTOR.index -> builder.profileTextColor = deserializeString()
                PROFILEUSEBACKGROUNDIMAGE_DESCRIPTOR.index -> builder.profileUseBackgroundImage = deserializeBoolean()
                PROTECTED_DESCRIPTOR.index -> builder.protected = deserializeBoolean()
                SCREENNAME_DESCRIPTOR.index -> builder.screenName = deserializeString()
                SHOWALLINLINEMEDIA_DESCRIPTOR.index -> builder.showAllInlineMedia = deserializeBoolean()
                STATUSESCOUNT_DESCRIPTOR.index -> builder.statusesCount = deserializeInt()
                TIMEZONE_DESCRIPTOR.index -> builder.timeZone = deserializeString()
                URL_DESCRIPTOR.index -> builder.url = deserializeString()
                UTCOFFSET_DESCRIPTOR.index -> builder.utcOffset = deserializeInt()
                VERIFIED_DESCRIPTOR.index -> builder.verified = deserializeBoolean()
                null -> break@loop
                else -> skipValue()
            }
        }
    }
    return builder.build()
}
