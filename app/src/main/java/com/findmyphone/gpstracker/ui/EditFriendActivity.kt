package com.findmyphone.gpstracker.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseMvvmActivity
import com.findmyphone.gpstracker.databinding.ActivityEditFriendBinding
import com.findmyphone.gpstracker.utils.ActivityManagerUtils
import com.findmyphone.gpstracker.utils.LogUtil
import com.findmyphone.gpstracker.viewmodel.EditFriendViewModel

class EditFriendActivity : BaseMvvmActivity<ActivityEditFriendBinding, EditFriendViewModel>() {

    companion object {
        // 将性别提取为枚举更安全，这里简化为常量
        private const val GENDER_FEMALE = "female"
        private const val GENDER_MALE = "male"
        private const val GENDER_OTHER = "other"
    }

    private var currentGender: String = GENDER_FEMALE
    private var friendCode: String? = null

    override fun init() {
        parseIntentExtras()
        setupTextWatcher()
        setupClickListeners()
        observeViewModel()
        setupNativeAd()
        LogUtil.log("enter_edit_display_name_page", emptyMap())
    }

    private fun setupNativeAd() {
        binding.nativeAdWidget.apply {
            setLayoutResId(R.layout.view_native_ad_two,2)
            setAdUnitId(AreaKey.displayNameSaveBtmNativeAdv)
            bindToLifecycle(this@EditFriendActivity)
            // 启动自动刷新（开始加载并每隔30秒刷新一次）
            startAutoRefresh()
        }
    }

    private fun parseIntentExtras() {
        friendCode = intent.getStringExtra("code")
        val intentGender = intent.getStringExtra("gender")
        if (!intentGender.isNullOrEmpty()) {
            currentGender = intentGender
        }
        updateGenderUI(currentGender)

        // 预填名称
        val preName = intent.getStringExtra("name")
        if (!preName.isNullOrEmpty()) {
            binding.etName.setText(preName)
            binding.ivClose.visibility = View.VISIBLE
        }
    }

    private fun updateGenderUI(gender: String) {
        // 重置所有样式
        binding.ivFemale.setImageResource(R.mipmap.ic_female)
        binding.ivMale.setImageResource(R.mipmap.ic_male)
        binding.rlFemale.background = null
        binding.rlMale.background = null
        binding.tvOther.setBackgroundResource(R.drawable.other_shape)

        when (gender) {
            GENDER_FEMALE -> {
                binding.ivFemale.setImageResource(R.mipmap.ic_female_check)
                binding.rlFemale.setBackgroundResource(R.drawable.sex_shape)
            }
            GENDER_MALE -> {
                binding.ivMale.setImageResource(R.mipmap.ic_male_check)
                binding.rlMale.setBackgroundResource(R.drawable.sex_shape)
            }
            GENDER_OTHER -> {
                binding.tvOther.setBackgroundResource(R.drawable.btn_nor)
            }
        }
    }

    private fun setupTextWatcher() {
        binding.etName.addTextChangedListener(afterTextChanged = { text ->
            binding.ivClose.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
        })
    }

    private fun setupClickListeners() {
        binding.ivClose.setOnClickListener { binding.etName.text.clear() }
        binding.ivBack.setOnClickListener {
            finish()
        }
        binding.ivFemale.setOnClickListener { selectGender(GENDER_FEMALE) }
        binding.ivMale.setOnClickListener { selectGender(GENDER_MALE) }
        binding.tvOther.setOnClickListener { selectGender(GENDER_OTHER) }
        binding.btSave.setOnClickListener { saveFriend() }
    }

    private fun selectGender(gender: String) {
        if (currentGender != gender) {
            currentGender = gender
            updateGenderUI(gender)
        }
    }

    private fun saveFriend() {
        val name = binding.etName.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.enter_display_name, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.addFriend(friendCode!!, name, currentGender)
    }

    private fun observeViewModel() {
        viewModel.addFriendResult.observe(this) { event ->
            val result = event.getContentIfNotHandled() ?: return@observe
            when (result) {
                is EditFriendViewModel.AddFriendResult.Loading -> {
                    handleLoadingState(true)
                }
                is EditFriendViewModel.AddFriendResult.Success -> {
                    ActivityManagerUtils.finishTopActivity(FriendLocationActivity::class.java)
                    showInterstitialAdWithStats(AreaKey.displayNameSaveClickAdv){
                        Toast.makeText(this, R.string.added_successfully, Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
                is EditFriendViewModel.AddFriendResult.Error -> {
                    handleLoadingState(false)
                    Toast.makeText(this, result.errorMsgResId, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}