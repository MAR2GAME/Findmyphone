package com.findmyphone.gpstracker.utils

import android.app.Activity
import java.util.*
import kotlin.collections.any
import kotlin.collections.indexOfLast
import kotlin.collections.isNotEmpty
import kotlin.jvm.javaClass


object ActivityManagerUtils {

    /**
     * 用于存储和统一销毁一套操作的Activities
     */
    public var activities: Stack<Activity>  = Stack()

    /**
     * 将该activity入栈
     *
     * @param activity
     */
    public fun addActivity(activity: Activity){
        activities.add(activity)
    }

    /*
      获取当前栈顶
   */
    fun currentActivity(): Activity? {
        return if (activities.isNotEmpty()) {
            activities.lastElement()
        } else {
            null
        }
    }

    /**
     * 将该activity出栈
     *
     * @param activity
     */
    fun removeActivity(activity: Activity?) {
        if (activity != null) {
            activities.remove(activity)
            activity.finish()
        }
    }

    fun finishTopActivity(cls: Class<*>): Boolean {
        val index = activities.indexOfLast { it.javaClass == cls }
        return if (index >= 0) {
            val activity = activities[index]
            if (!activity.isFinishing) {
                activity.finish()
            }
            activities.removeAt(index)
            true
        } else {
            false
        }
    }

    fun  hasActivity(cls: Class<*>): Boolean{
        return  activities.any {it.javaClass == cls }
    }



    /**
     * 结束所有activity
     */
    fun finishAll() {
        for (activity in activities) {
            if (!activity.isFinishing) {
                activity.finish()
            }
        }
        activities.clear()
       // Process.killProcess(Process.myPid())
    }






}