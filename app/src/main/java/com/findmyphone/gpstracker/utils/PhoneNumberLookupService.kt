package com.findmyphone.gpstracker.utils

import com.findmyphone.gpstracker.bean.PhoneNumberInfo
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberToCarrierMapper
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber
import com.google.i18n.phonenumbers.PhoneNumberToTimeZonesMapper
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.collections.isNotEmpty
import kotlin.collections.joinToString
import kotlin.jvm.java
import kotlin.takeIf
import kotlin.text.isNotEmpty
import kotlin.text.isNullOrBlank
import kotlin.text.isNullOrEmpty
import kotlin.text.startsWith
import kotlin.text.take
import kotlin.text.takeLast

class PhoneNumberLookupService {

    private val phoneUtil = PhoneNumberUtil.getInstance()
    private val geocoder = PhoneNumberOfflineGeocoder.getInstance()
    private val carrierMapper = PhoneNumberToCarrierMapper.getInstance()
    private val timezoneMapper = PhoneNumberToTimeZonesMapper.getInstance()

    private val logger = Logger.getLogger(PhoneNumberLookupService::class.java.name)

    /**
     * 查询手机号信息（挂起函数，适用于协程）
     * @param phoneNumberWithPlus 带区号的完整号码，如 "+8613812345678"
     * @param defaultRegion 可选：默认国家代码（当号码不含区号时使用），如 "CN"。若号码不含 '+' 且此参数为 null，则返回 null。
     * @param locale 期望的语言区域，默认为系统默认语言。所有地理、运营商、国家名称将优先使用该语言，若数据缺失则回退到英文。
     */
    suspend fun lookupPhoneNumber(
        phoneNumberWithPlus: String,
        defaultRegion: String? = null,
        locale: Locale = Locale.getDefault()
    ): PhoneNumberInfo? = withContext(Dispatchers.Default) {
        try {
            // 1. 处理无区号且无默认国家的情况
            if (!phoneNumberWithPlus.startsWith("+") && defaultRegion == null) {
                logger.warning(
                    "Phone number does not start with '+' and no defaultRegion provided: ${
                        maskPhoneNumber(
                            phoneNumberWithPlus
                        )
                    }"
                )
                return@withContext null
            }

            // 2. 校验 defaultRegion 有效性（可选）
            if (defaultRegion != null && !phoneUtil.getSupportedRegions().contains(defaultRegion)) {
                logger.warning("Invalid defaultRegion: $defaultRegion, not in supported regions")
                return@withContext null
            }

            // 3. 解析号码
            val parsedNumber = if (phoneNumberWithPlus.startsWith("+")) {
                phoneUtil.parse(phoneNumberWithPlus, "")
            } else {
                phoneUtil.parse(phoneNumberWithPlus, defaultRegion!!)
            }

            // 4. 验证有效性
            if (!phoneUtil.isValidNumber(parsedNumber)) {
                logger.info("Invalid phone number: ${maskPhoneNumber(phoneNumberWithPlus)}")
                return@withContext null
            }

            // 5. 获取国家信息（ISO 代码可能为 null）
            val countryCode = parsedNumber.countryCode
            val isoCode = getCountryIsoCode(parsedNumber, defaultRegion)
            val countryName = getCountryName(isoCode, locale)

            // 6. 获取地理描述（使用指定语言，若为空则回退英文）
            val geographicDescription = geocoder.getDescriptionForNumber(parsedNumber, locale)
                ?.takeIf { it.isNotEmpty() }
                ?: geocoder.getDescriptionForNumber(parsedNumber, Locale.ENGLISH) ?: ""

            // 7. 获取时区
            val timezones = timezoneMapper.getTimeZonesForNumber(parsedNumber)
            val timezone = if (timezones.isNotEmpty()) timezones.joinToString(" & ") else "Unknown"

            // 8. 获取运营商（使用指定语言，若为空则回退英文）
            val carrier = carrierMapper.getNameForNumber(parsedNumber, locale)
                ?.takeIf { it.isNotEmpty() }
                ?: carrierMapper.getNameForNumber(parsedNumber, Locale.ENGLISH) ?: ""

            val international =
                phoneUtil.format(parsedNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)

            PhoneNumberInfo(
                originalNumber = phoneNumberWithPlus,
                internationalFormat = international,
                countryCode = countryCode,
                countryIsoCode = isoCode ?: "",
                countryName = countryName,
                city = geographicDescription,  // 注意：实际为地理区域描述，可能为省份/城市级别
                timezone = timezone,
                carrier = carrier,
                isValid = true
            )
        } catch (e: NumberParseException) {
            logger.log(
                Level.WARNING,
                "Failed to parse phone number: ${maskPhoneNumber(phoneNumberWithPlus)}",
                e
            )
            null
        } catch (e: Exception) {
            logger.log(
                Level.SEVERE,
                "Unexpected error during phone number lookup for ${
                    maskPhoneNumber(phoneNumberWithPlus)
                }",
                e
            )
            null
        }
    }

    /**
     * 获取号码所属的国家/地区 ISO 代码（如 "CN"）
     * 优先使用 PhoneNumberUtil.getRegionCodeForNumber 处理共享区号场景
     * 若无法获取且提供了 defaultRegion，则回退至 defaultRegion；否则返回 null
     */
    private fun getCountryIsoCode(
        number: Phonenumber.PhoneNumber,
        defaultRegion: String?
    ): String? {
        val region = phoneUtil.getRegionCodeForNumber(number)
        if (!region.isNullOrEmpty()) return region

        if (!defaultRegion.isNullOrEmpty()) {
            logger.warning("Could not determine region for number, falling back to defaultRegion: $defaultRegion")
            return defaultRegion
        }

        logger.warning("Could not determine region for number and no defaultRegion provided, returning null")
        return null
    }

    /**
     * 根据 ISO 代码获取国家名称（优先使用指定 locale，若结果为空则使用英文，最后返回 "未知" 或 ISO 代码）
     */
    private fun getCountryName(isoCode: String?, locale: Locale): String {
        if (isoCode.isNullOrEmpty()) return "Unknown"

        return try {
            val countryLocale = Locale("", isoCode)
            // 尝试使用调用方指定的语言
            val nameInTargetLang = countryLocale.getDisplayCountry(locale)
            if (!nameInTargetLang.isNullOrBlank()) return nameInTargetLang

            // 回退到英文
            val nameInEnglish = countryLocale.getDisplayCountry(Locale.ENGLISH)
            if (!nameInEnglish.isNullOrBlank()) return nameInEnglish

            // 最后返回 ISO 代码
            isoCode
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to get country name for ISO code: $isoCode", e)
            isoCode
        }
    }

    /**
     * 手机号脱敏：保留前3位和后4位，中间用 **** 代替
     */
    private fun maskPhoneNumber(phoneNumber: String): String {
        if (phoneNumber.length <= 7) return "***"
        val visiblePrefix = phoneNumber.take(3)
        val visibleSuffix = phoneNumber.takeLast(4)
        return "$visiblePrefix****$visibleSuffix"
    }
}