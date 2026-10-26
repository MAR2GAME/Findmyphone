package com.findmyphone.gpstracker.push

import android.content.Intent
import android.os.Build
import android.os.Parcelable
import android.text.TextUtils
import android.util.Log
import com.findmyphone.gpstracker.receiver.BootCompletedReceiver
import com.findmyphone.gpstracker.services.PersistentNotificationService
import com.findmyphone.gpstracker.utils.AppPrefsUtils

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

import kotlinx.parcelize.Parcelize
import kotlin.jvm.java
import kotlin.text.orEmpty

class MyAppFirebaseMessagingService

	: FirebaseMessagingService() {
	override fun onNewToken(token: String) {
		super.onNewToken(token)
		AppPrefsUtils.commitString("FCM_TOKEN",token)
		Log.e("weiyu","FCM_TOKEN"+token)
	}
	override fun onMessageReceived(message: RemoteMessage) {
		super.onMessageReceived(message)

		if(PersistentNotificationService.isRunning){
			val messageType=message.data[KEY_MESSAGE_TYPE].orEmpty()
			val message=message.data[KEY_MESSAGE].orEmpty()
			if(!TextUtils.isEmpty(messageType)&&!TextUtils.isEmpty(message)){
				Log.e("weiyu","sos消息发送"+message)
				val intent = Intent(PersistentNotificationService.ACTION_START_MESSAGE).apply {
					putExtra("message", message)
					putExtra("messageType", messageType)
					setPackage(packageName)   // 限制为本应用接收
				}
				sendBroadcast(intent)
			}
		}else{
			startService()
		}
		// 解析消息数据
		//val messageData = parseMessageData(message.data)



	}

	/**
	 * 解析RemoteMessage数据
	 */
	private fun parseMessageData(data: Map<String, String>): FcmMessage {
		return FcmMessage(
//			fcmSendTime = data[KEY_FCM_SEND_TIME].orEmpty(),
//			id = data[KEY_ID].orEmpty(),
//			appPackage = data[KEY_APP_PACKAGE].orEmpty(),
//			fcmContent = data[KEY_FCM_CONTENT].orEmpty(),
//			fcmType = data[KEY_FCM_TYPE].orEmpty(),
//			fcmTitle = data[KEY_FCM_TITLE].orEmpty(),
			messageType=data[KEY_MESSAGE_TYPE].orEmpty(),
			message =data[KEY_MESSAGE].orEmpty()
		)
	}


//	/**
//	 * 通知其他组件消息已接收
//	 */
//private fun notifyMessageReceived(message: String,messageType: String) {
//	try {
//		Intent(this, PersistentNotificationService::class.java).apply {
//			action = PersistentNotificationService.ACTION_START_MESSAGE
//			putExtra("message",message)
//			putExtra("messageType",messageType)
//			startService(this)
//		}
//
//	} catch (e: Exception) {
//
//	}
//}


	private fun startService() {
		try {
			val intent = Intent(this, BootCompletedReceiver::class.java).apply {
				action = "FCM_NOTIFICATION"
			}
			// 根据Android版本选择合适的发送方式
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
				intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
			}
			sendBroadcast(intent)
			Log.d(TAG, "FCM notification broadcast sent")
		} catch (e: Exception) {
			Log.e(TAG, "Failed to send FCM notification broadcast", e)
		}
	}
	companion object {
		private const val TAG = "MyFirebaseMessagingService"
		private const val KEY_MESSAGE = "message"
		private const val KEY_MESSAGE_TYPE = "messageType"
	}

	/**
	 * 数据类封装FCM消息
	 */@Parcelize
	data class FcmMessage(
//		val fcmSendTime: String = "",
//		val id: String = "",
//		val appPackage: String = "",
//		val fcmContent: String = "",
//		val fcmType: String = "",
//		val fcmTitle: String = "",
		val messageType:String="",
		val message:String = "",
	) : Parcelable


}