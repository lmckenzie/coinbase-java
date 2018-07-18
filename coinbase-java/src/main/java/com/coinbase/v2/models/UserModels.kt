package com.coinbase.v2.models

import com.google.gson.annotations.Expose
import com.google.gson.annotations.SerializedName
import java.util.*
import javax.annotation.Generated

@Generated("org.jsonschema2pojo")
data class User @JvmOverloads constructor(@SerializedName("data") @Expose var data: UserData? = null)

@Generated("org.jsonschema2pojo")
data class Address @JvmOverloads constructor(
        @SerializedName("line1") @Expose var line1: String? = null,
        @SerializedName("line2") @Expose var line2: String? = null,
        @SerializedName("line3") @Expose var line3: Any? = null,
        @SerializedName("city") @Expose var city: String? = null,
        @SerializedName("state") @Expose var state: String? = null,
        @SerializedName("postal_code") @Expose var postalCode: String? = null,
        @SerializedName("country") @Expose var country: Country? = null
)

@Generated("org.jsonschema2pojo")
data class Country @JvmOverloads constructor(
        @SerializedName("code") @Expose var code: String? = null,
        @SerializedName("name") @Expose var name: String? = null
)

@Generated("org.jsonschema2pojo")
data class UserData @JvmOverloads constructor(
        @SerializedName("id") @Expose var id: String? = null,
        @SerializedName("name") @Expose var name: String? = null,
        @SerializedName("username") @Expose var username: String? = null,
        @SerializedName("profile_location") @Expose var profileLocation: String? = null,
        @SerializedName("profile_bio") @Expose var profileBio: String? = null,
        @SerializedName("profile_url") @Expose var profileUrl: String? = null,
        @SerializedName("avatar_url") @Expose var avatarUrl: String? = null,
        @SerializedName("resource") @Expose var resource: String? = null,
        @SerializedName("resource_path") @Expose var resourcePath: String? = null,
        @SerializedName("email") @Expose var email: String? = null,
        @SerializedName("time_zone") @Expose var timeZone: String? = null,
        @SerializedName("native_currency") @Expose var nativeCurrency: String? = null,
        @SerializedName("bitcoin_unit") @Expose var bitcoinUnit: String? = null,
        @SerializedName("country") @Expose var country: Country? = null,
        @SerializedName("created_at") @Expose var createdAt: String? = null,
        @SerializedName("restrictions") @Expose var restrictions: List<String> = ArrayList(),
        @SerializedName("feature_flags") @Expose var featureFlags: List<String> = ArrayList(),
        @SerializedName("split_test_groups") @Expose var splitTestGroups: List<SplitTest> = ArrayList(),
        @SerializedName("admin_flags") @Expose var adminFlags: List<String> = ArrayList(),
        @SerializedName("personal_details") @Expose var personalDetails: PersonalDetails? = null,
        @SerializedName("tiers") @Expose var tiers: Tiers? = null,
        @SerializedName("merchant") @Expose var merchant: Any? = null,
        @SerializedName("oauth") @Expose var oauth: Oauth? = null,
        @SerializedName("referral_id") @Expose var referralId: Any? = null
)

@Generated("org.jsonschema2pojo")
data class DateOfBirth @JvmOverloads constructor(
        @SerializedName("year") @Expose var year: Int? = null,
        @SerializedName("month") @Expose var month: Int? = null,
        @SerializedName("day") @Expose var day: Int? = null
)

@Generated("org.jsonschema2pojo")
data class LegalName @JvmOverloads constructor(
        @SerializedName("first_name") @Expose var firstName: String? = null,
        @SerializedName("last_name") @Expose var lastName: String? = null
)

@Generated("org.jsonschema2pojo")
data class Oauth @JvmOverloads constructor(
        @SerializedName("access_token") @Expose var accessToken: String? = null,
        @SerializedName("token_type") @Expose var tokenType: String? = null,
        @SerializedName("expires_in") @Expose var expiresIn: Int? = null,
        @SerializedName("refresh_token") @Expose var refreshToken: String? = null,
        @SerializedName("scope") @Expose var scope: String? = null
)

@Generated("org.jsonschema2pojo")
data class PersonalDetails @JvmOverloads constructor(
        @SerializedName("date_of_birth") @Expose var dateOfBirth: DateOfBirth? = null,
        @SerializedName("address") @Expose var address: Address? = null,
        @SerializedName("legal_name") @Expose var legalName: LegalName? = null
)

@Generated("org.jsonschema2pojo")
data class SplitTest @JvmOverloads constructor(
        @SerializedName("test") @Expose var test: String? = null,
        @SerializedName("group") @Expose var group: String? = null,
        @SerializedName("is_tracked") @Expose var isTracked: Boolean? = null
)

@Generated("org.jsonschema2pojo")
data class Tiers @JvmOverloads constructor(
        @SerializedName("upgrade_button_text") @Expose var upgradeButtonText: String? = null,
        @SerializedName("header") @Expose var header: String? = null,
        @SerializedName("body") @Expose var body: String? = null,
        @SerializedName("completed_description") @Expose var completedDescription: String? = null
)

