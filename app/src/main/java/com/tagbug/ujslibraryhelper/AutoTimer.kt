package com.tagbug.ujslibraryhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import com.tagbug.ujslibraryhelper.util.ObjectToBase64
import com.tagbug.ujslibraryhelper.util.OrderHelper
import com.tagbug.ujslibraryhelper.util.TimerNotification
import java.util.*

/**
 * 定时运行广播接收器
 */
class AutoTimer : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context?.apply {
            OrderHelper.init(this)
            tryToRunWithTimer(this)
        }
    }

    /**
     * 尝试运行
     */
    private fun tryToRunWithTimer(context: Context) {
        var orderTimeType: OrderHelper.OrderTimeType? = null
        var hasAutoChooseSeat: Boolean
        var logHistory: Deque<String>? = null
        context.getSharedPreferences(MainActivity.Config.configFileName, Context.MODE_PRIVATE).apply {
            getString("orderTimeType", null)?.apply { orderTimeType = OrderHelper.OrderTimeType.valueOf(this) }
            hasAutoChooseSeat = getBoolean("hasAutoChooseSeat", false)
            getString("logHistory", null)?.apply {
                ObjectToBase64.decode(this)?.apply { logHistory = this as Deque<String> }
            }
        }
        if (orderTimeType == null) {
            TimerNotification.showSimpleNotification(context, "自动预约失败：预约时间未设置")
            return
        }
        logHistory = logHistory ?: ArrayDeque(100)
        OrderHelper.runWithTimer(orderTimeType!!, hasAutoChooseSeat, null).thenAccept {
            TimerNotification.showSimpleNotification(context, it)
            logHistory?.apply {
                if (!offerFirst(it)) {
                    removeLast()
                    addFirst(it)
                }
            }
        }.exceptionally { e ->
            OrderHelper.dealWithException {
                logHistory?.apply {
                    if (!offerFirst(it)) {
                        removeLast()
                        addFirst(it)
                    }
                }
            }(e)
            var messageTitle = ""
            var message = ""
            OrderHelper.dealWithException({ messageTitle = it }, { message = it })(e)
            TimerNotification.showLargeNotification(context, messageTitle, message)
            return@exceptionally null
        }.thenAccept {
            context.getSharedPreferences(MainActivity.Config.configFileName, Context.MODE_PRIVATE).edit {
                putString("logHistory", ObjectToBase64.encode(logHistory))
            }
        }
    }
}