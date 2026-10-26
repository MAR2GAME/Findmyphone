package com.findmyphone.gpstracker.bean

data class PlayIntegrityBean(
    val accountDetails: AccountDetails,
    val appIntegrity: AppIntegrity,
    val deviceIntegrity: DeviceIntegrity,
    val requestDetails: RequestDetails
)

data class AccountDetails(
    val appLicensingVerdict: String
)

data class AppIntegrity(
    val appRecognitionVerdict: String
)

class DeviceIntegrity

data class RequestDetails(
    val requestHash: String,
    val requestPackageName: String,
    val timestampMillis: String
)