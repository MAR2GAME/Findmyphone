package com.findmyphone.gpstracker.ui

import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.findmyphone.gpstracker.R
import com.findmyphone.gpstracker.ad.AreaKey
import com.findmyphone.gpstracker.base.activity.BaseMvvmActivity
import com.findmyphone.gpstracker.base.command.readyGo
import com.findmyphone.gpstracker.base.command.readyGoThenKill
import com.findmyphone.gpstracker.bean.FriendLocationBeansItem
import com.findmyphone.gpstracker.bean.Position
import com.findmyphone.gpstracker.databinding.ActivityLocationsListBinding
import com.findmyphone.gpstracker.viewmodel.LocationsListViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.jvm.java

class LocationsListActivity : BaseMvvmActivity<ActivityLocationsListBinding, LocationsListViewModel>() {

    private lateinit var friendAdapter: BaseQuickAdapter<FriendLocationBeansItem, BaseViewHolder>
    private var currentPage = 1

    private var  appOpenFrom:String?=null

    override fun init() {
        appOpenFrom = intent.getStringExtra("appOpenFrom")
        initAdapter()
        initViewModel()
        setupListeners()
        setupBackPressHandler()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBack()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        currentPage = 1
        viewModel.getLocationsData(currentPage)
    }
    fun onBack(){
        showInterstitialAdWithStats(AreaKey.friendListBackHomeAdv){
            if(TextUtils.isEmpty(appOpenFrom)){
                finish()
            }else{
                readyGoThenKill(MainActivity::class.java)
            }
        }
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener { onBack() }
        binding.btAdd.setOnClickListener { readyGo(AddFriendActivity::class.java) }
    }
    private fun initAdapter() {
        friendAdapter = object : BaseQuickAdapter<FriendLocationBeansItem, BaseViewHolder>(R.layout.item_friend_loccation) {
            override fun convert(holder: BaseViewHolder, item: FriendLocationBeansItem) {
                bindName(holder, item)
                bindLocationStatus(holder, item)
                bindClickListener(holder, item)
            }

            private fun bindName(holder: BaseViewHolder, item: FriendLocationBeansItem) {
                val displayName = item.displayName.takeIf { it.isNotBlank() } ?: ""
                holder.setText(R.id.tv_name, displayName)
            }

            private fun bindLocationStatus(holder: BaseViewHolder, item: FriendLocationBeansItem) {
                val position = item.position
                if (hasValidLocation(position)) {
                    holder.setImageResource(R.id.iv_map, R.mipmap.ic_friend_location)
                    val timeText = position?.lastUpdatedAt?.formatToDateTimeString()
                        ?: getString(R.string.tracking_disabled)
                    holder.setText(R.id.tv_time, timeText)
                    holder.setImageResource(R.id.iv_hint,R.mipmap.ic_item_hint)
                    holder.setTextColor(R.id.tv_time,getColor(R.color.hint_color))
                } else {
                    holder.setImageResource(R.id.iv_map, R.mipmap.ic_location_erro)
                    holder.setText(R.id.tv_time, getString(R.string.tracking_disabled))
                    holder.setImageResource(R.id.iv_hint,R.mipmap.ic_disable)
                    holder.setTextColor(R.id.tv_time,getColor(R.color.disable_color))
                }
            }

            private fun bindClickListener(holder: BaseViewHolder, item: FriendLocationBeansItem) {
                holder.getView<View>(R.id.cl_content).setOnClickListener {
                    if (hasValidLocation(item.position)) {
                        showInterstitialAdWithStats(AreaKey.enterFriendMapAdv){
                            val bundle = Bundle().apply {
                                putParcelable("extra_friend_location", item)
                            }
                            readyGo(FriendLocationActivity::class.java, bundle)
                        }
                    } else {
                        Toast.makeText(
                            this@LocationsListActivity,
                            getString(R.string.request_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            private fun hasValidLocation(position: Position?): Boolean {
                return position != null && position.longitude != null && position.latitude != null
            }
        }
        binding.rcMap.apply {
            adapter = friendAdapter
            layoutManager = LinearLayoutManager(this@LocationsListActivity, RecyclerView.VERTICAL, false)
        }
        binding.sm.setOnLoadMoreListener { refreshlayout ->
            currentPage++
            viewModel.getLocationsData(currentPage)
        }
    }
    private val DEFAULT_DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm:ss")

    fun Long.formatToDateTimeString(zoneId: ZoneId = ZoneId.systemDefault()): String {
        val instant = Instant.ofEpochMilli(this)
        val dateTime = instant.atZone(zoneId)
        return dateTime.format(DEFAULT_DATE_FORMATTER)
    }

    private fun initViewModel() {
        viewModel.isLoading.observe(this) {
            handleLoadingState(it)
        }
        viewModel.locationsData.observe(this) { response ->
            binding.sm.finishLoadMore()
            if (currentPage == 1) {
                if(response!=null){
                    response!!.items?.let { friendAdapter.setList(it) }
                }else{
                    friendAdapter.setList(mutableListOf())
                }
            } else {
                response?.items?.let { friendAdapter.addData(it) }
            }
            val totalItems = response?.total ?: 0
            if (friendAdapter.data.isEmpty() || friendAdapter.data.size == totalItems) {
                binding.sm.finishLoadMoreWithNoMoreData()
            } else {
                binding.sm.resetNoMoreData()
            }
            binding.ivNoData.visibility = if (friendAdapter.data.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}