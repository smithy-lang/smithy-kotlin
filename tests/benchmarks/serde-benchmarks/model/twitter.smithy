$version: "1.0"

namespace aws.benchmarks.twitter

use aws.benchmarks.protocols#serdeBenchmarkJson

@serdeBenchmarkJson
service Twitter {
    version: "2019-12-16",
    operations: [GetFeed]
}

@http(uri: "/GetFeed", method: "POST")
operation GetFeed {
    input: GetFeedResponse,
    output: GetFeedResponse
}

structure GetFeedResponse {
    feed: TwitterFeed
}


structure TwitterFeed {
    search_metadata: SearchMetadata,
    statuses: StatusList
}

list StatusList {
    member: Status
}


structure SearchMetadata {
    completed_in: Double,
    count: Integer,
    max_id: Long,
    max_id_str: String,
    next_results: String,
    query: String,
    refresh_url: String,
    since_id: Integer,
    since_id_str: String
}


structure Status {
    created_at: String,
    entities: Entities,
    favorited: Boolean,
    id: Long,
    id_str: String,
    possibly_sensitive: Boolean,
    retweet_count: Integer,
    retweeted: Boolean,
    source: String,
    text: String,
    truncated: Boolean,
    user: User
}


structure Entities {
    hashtags: HashTagList,
    media: MediaList,
    urls: UrlList,
    user_mentions: UserMentionList
}

list HashTagList {
    member: Hashtag
}

list MediaList {
    member: Media
}

list UrlList {
    member: Url
}

list UserMentionList {
    member: UserMention
}


structure User {
    contributors_enabled: Boolean,
    created_at: String,
    default_profile: Boolean,
    default_profile_image: Boolean,
    description: String,
    favourites_count: Integer,
    follow_request_sent: Boolean,
    followers_count: Integer,
    following: Boolean,
    friends_count: Integer,
    geo_enabled: Boolean,
    id: Integer,
    id_str: String,
    is_translator: Boolean,
    lang: String,
    listed_count: Integer,
    location: String,
    name: String,
    notifications: Boolean,
    profile_background_color: String,
    profile_background_image_url: String,
    profile_background_image_url_https: String,
    profile_background_tile: Boolean,
    profile_image_url: String,
    profile_image_url_https: String,
    profile_link_color: String,
    profile_sidebar_border_color: String,
    profile_sidebar_fill_color: String,
    profile_text_color: String,
    profile_use_background_image: Boolean,
    url: String,
    protected: Boolean,
    screen_name: String,
    show_all_inline_media: Boolean,
    statuses_count: Integer,
    time_zone: String,
    utc_offset: Integer,
    verified: Boolean
}


structure Hashtag {
    indices: IntList,
    text: String
}

list IntList {
    member: Integer
}


structure Media {
    display_url: String,
    expanded_url: String,
    id: Long,
    id_str: String,
    indices: IntList,
    media_url: String,
    media_url_https: String,
    sizes: Sizes,
    type: String,
    url: String
}


structure Url {
    display_url: String,
    expanded_url: String,
    indices: IntList,
    url: String
}


structure UserMention {
    id: Integer,
    id_str: String,
    indices: IntList,
    name: String,
    screen_name: String
}


structure Sizes {
    large: Large,
    medium: Medium,
    small: Small,
    thumb: Thumb
}


structure Large {
    h: Integer,
    resize: String,
    w: Integer
}


structure Medium {
    h: Integer,
    resize: String,
    w: Integer
}


structure Small {
    h: Integer,
    resize: String,
    w: Integer
}


structure Thumb {
    h: Integer,
    resize: String,
    w: Integer
}
