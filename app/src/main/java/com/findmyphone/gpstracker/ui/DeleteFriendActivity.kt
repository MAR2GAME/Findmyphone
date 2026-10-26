package com.findmyphone.gpstracker.ui

import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseMvvmActivity
import com.findmyphone.gpstracker.base.command.readyGoThenKill
import com.findmyphone.gpstracker.bean.FriendLocationBeansItem
import com.findmyphone.gpstracker.databinding.ActivityDeleteFriendBinding
import com.findmyphone.gpstracker.utils.ActivityManagerUtils
import com.findmyphone.gpstracker.view.DeleteDialog
import com.findmyphone.gpstracker.viewmodel.DeleteFriendViewModel

class DeleteFriendActivity : BaseMvvmActivity<ActivityDeleteFriendBinding, DeleteFriendViewModel>() {

    // 提取非空数据，避免频繁使用 !!
    private lateinit var friendData: FriendLocationBeansItem
    private var deleteDialog: DeleteDialog? = null
    private var isCheck = true   // 根据需求决定是否使用，此处保留

    override fun init() {
        initViewModel()
        // 取出数据并提前校验
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("extra_friend_location", FriendLocationBeansItem::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("extra_friend_location") as? FriendLocationBeansItem
        }
        if (data == null) {
            Toast.makeText(this, getString(R.string.loading_failed), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        friendData = data
        // 显示名称
        if (!TextUtils.isEmpty(friendData.displayName)) {
            binding.tvName.text = friendData.displayName
        }
        // 性别图标
        when (friendData.gender) {
            "male"   -> binding.ivGender.setImageResource(R.mipmap.ic_male_check)
            "female" -> binding.ivGender.setImageResource(R.mipmap.ic_female_check)
            else     -> binding.ivGender.setImageResource(R.mipmap.ic_other_check)
        }
        // 返回
        binding.ivBack.setOnClickListener { finish() }
        // 删除按钮
        binding.btDelete.setOnClickListener {
            if (deleteDialog == null) {
                deleteDialog = DeleteDialog(this)
                deleteDialog?.setmOnClickListener(object : DeleteDialog.onClickListener {
                    override fun onNext() {
                        viewModel.deleteFriend(friendData)
                    }
                })
            }
            deleteDialog?.show()
        }

        // 复选框（如果后续需要关联删除参数，在此处使用 isCheck）
        binding.ivCheck.setOnClickListener {
            isCheck = !isCheck
            if (isCheck) {
                binding.ivCheck.setImageResource(R.mipmap.iv_check)
            } else {
                Toast.makeText(this, getString(R.string.not_sharing_location), Toast.LENGTH_SHORT).show()
                binding.ivCheck.setImageResource(R.mipmap.iv_check_nor)
            }
        }

        // 编辑
        binding.llEdit.setOnClickListener {
            if (TextUtils.isEmpty(friendData.friendSecurityCode)) {
                Toast.makeText(this, getString(R.string.request_error), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showInterstitialAdWithStats(AreaKey.enterDisplayNameAdv){
                val bundle = Bundle().apply {
                    putString("code", friendData.friendSecurityCode)
                    putString("name", friendData.displayName)
                    putString("gender", friendData.gender)
                }
                readyGoThenKill(EditFriendActivity::class.java, bundle)
            }
        }
    }

    private fun initViewModel() {
        viewModel.isLoading.observe(this) { handleLoadingState(it) }

        viewModel.isDeleteState.observe(this) { success ->
            if (success) {
                Toast.makeText(this, getString(R.string.deleted_successfully), Toast.LENGTH_SHORT).show()
                ActivityManagerUtils.finishTopActivity(FriendLocationActivity::class.java)
                finish()
            }
        }

        // 观察错误事件，由 Activity 显示 Toast
        viewModel.errorEvent.observe(this) { event ->
            event.getContentIfNotHandled()?.let { errorMsg ->
                Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        deleteDialog?.release()  // 确保内部已 dismiss
        deleteDialog = null
        super.onDestroy()
    }
}