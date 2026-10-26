package com.findmyphone.gpstracker.bean

data class AdMapping(
    val config: Config,
    val configs: List<ConfigX>,
    val nature_config: NatureConfig
)

data class Config(
    val ad: String,
    val ad_native: String,
    val fcm_topic: String,
    val notification: String,
    val preload: String
)

data class ConfigX(
    val brands: List<String>,
    val config: Config,
    val countrys: List<String>
)

data class NatureConfig(
    val ad: String,
    val ad_native: String,
    val fcm_topic: String,
    val notification: String,
    val preload: String
)