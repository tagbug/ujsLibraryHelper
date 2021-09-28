package com.tagbug.ujslibraryhelper

import android.annotation.SuppressLint
import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.text.method.ScrollingMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.widget.addTextChangedListener
import com.tagbug.ujslibraryhelper.MainActivity.Config.configFileName
import com.tagbug.ujslibraryhelper.util.*
import com.tagbug.ujslibraryhelper.util.OrderHelper.OrderFailException
import com.tagbug.ujslibraryhelper.util.OrderHelper.OrderTimeType
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import kotlin.reflect.jvm.javaMethod

class MainActivity : AppCompatActivity() {
    @SuppressLint("HandlerLeak")
    private val handler = object : Handler() {
        override fun handleMessage(msg: Message) {
            if (msg.obj is Runnable) {
                (msg.obj as Runnable).run()
            }
        }
    }

    /**
     * 配置项
     */
    object Config {
        const val configFileName = "MainActivity.Config"
    }

    private var defaultProgressDialog: ProgressDialog? = null
    private val infoToShowWithStart =
        listOf("为了保证定时任务效果，建议给予自启动权限，点击右上角有三个点导航到设置页", "通知推送效果可以手动调节，可以手动测试", "建议给予APP自启动权限，否则定时运行功能在部分系统上可能会失效")
    private val orderTimeTypeTip = "预约时间类型：\n" +
            "DEFAULT 默认，按照用户写定的时间&座位等信息预约，不会检查时间&座位可用性\n" +
            "CHECKED 同默认，但会检查先时间&座位是否可用再预约（下面的都会做检查）\n" +
            "TODAY 选择运行时当天\n" +
            "TOMORROW 选择运行时的后一天\n" +
            "FUTURE_TIME 会查询可用时间，并选择最靠后的那一个\n" +
            "FUTURE_TIME_NOT_TODAY 同上，但永远不会选择今天\n\n" +
            "显示规则：\n" +
            "DEFAULT 显示具体的orderDay\n" +
            "CHECKED 显示具体的orderDay并在前面加上*号\n" +
            "其他的 显示具体Type"
    private val orderSeatTip = "请注意：\n当前选择的预约模式不支持查看座位的可用性，具体以预约结果为准"
    private var hasOrderTimeTypeTip: Boolean = true
    private var orderTimeType: OrderTimeType? = null
    private var timingTime: Calendar? = null
    private var hasAutoChooseSeat: Boolean = false
    private var logHistory: Deque<String> = ArrayDeque(100)
    private var logHistoryIterator: Iterator<String>? = null

    private fun loadConfig() {
        getSharedPreferences(configFileName, MODE_PRIVATE).apply {
            hasOrderTimeTypeTip = getBoolean("hasOrderTimeTypeTip", true)
            getString("orderTimeType", null)?.apply { orderTimeType = OrderTimeType.valueOf(this) }
            getString("timingTime", null)?.apply {
                ObjectToBase64.decode(this)?.apply { timingTime = this as Calendar }
            }
            hasAutoChooseSeat = getBoolean("hasAutoChooseSeat", false)
            getString("logHistory", null)?.apply {
                ObjectToBase64.decode(this)?.apply { logHistory = this as Deque<String> }
            }
        }
    }

    private fun saveConfig() {
        getSharedPreferences(configFileName, MODE_PRIVATE).edit {
            putBoolean("hasOrderTimeTypeTip", hasOrderTimeTypeTip)
            putString("orderTimeType", orderTimeType?.name)
            putString("timingTime", ObjectToBase64.encode(timingTime))
            putBoolean("hasAutoChooseSeat", hasAutoChooseSeat)
            putString("logHistory", ObjectToBase64.encode(logHistory))
        }
    }


    private fun updateConfigToUI() {
        input_userName.setText(OrderHelper.userId)
        input_areaId.setText(OrderHelper.areaId)
        /**
         * 显示规则：
         * DEFAULT 显示具体的orderDay
         * CHECKED 显示具体的orderDay并在前面加上*号
         * 其他的 显示具体Type
         */
        orderTimeType?.let {
            val info: String? = when (it) {
                OrderTimeType.DEFAULT -> OrderHelper.orderDay
                OrderTimeType.CHECKED -> "*${OrderHelper.orderDay}"
                else -> it.name
            }
            input_orderTime.setText(info)
        }
        input_seatId.setText(OrderHelper.seatId)
        button_chooseTimingTime.text =
            timingTime?.let { String.format("%tT", timingTime) } ?: getText(R.string.timeChoiceButtonHint)
        switch_autoChooseSeat.isChecked = hasAutoChooseSeat
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        logTextView.movementMethod = ScrollingMovementMethod.getInstance()
        defaultProgressDialog = ProgressDialog(this)
        // 加载配置项
        loadConfig()
        // 初始化OrderHelper
        OrderHelper.init(this)
        // 更新UI
        updateConfigToUI()
        if (timingTime != null) {
            button_cancelTiming.visibility = View.VISIBLE
        }
        // 显示默认提示信息
        infoToShowWithStart.forEach { log("->提示：$it") }
        log("")
        // 创建通知渠道
        TimerNotification.addNotificationChannel(this)
        // 设置用户名输入监听器
        input_userName.addTextChangedListener {
            OrderHelper.userId = input_userName.text.toString()
        }
        // 设置选择地区按钮的回调
        button_chooseArea.setOnClickListener {
            showProgressing()
            OrderHelper.getAreaTree().thenAccept {
                if (it.status != 1) {
                    throw OrderFailException("获取可用空间失败！", it.toString())
                }
                val mainList = it.data.list.map { item ->
                    if (item.isValid == 1) item.name else "${item.name}（当前不可用）"
                }
                val msg = Message()
                msg.obj = Runnable {
                    hideProgressing()
                    // 注：对UI的更新需要在主线程中完成，此处用Handle机制
                    AlertDialog.Builder(this).setTitle("选择大类").setItems(mainList.toTypedArray()) { _, mainIndex ->
                        val firstList = it.data.list[mainIndex]._child.map { item ->
                            if (item.isValid == 1) item.name else "${item.name}（当前不可用）"
                        }
                        AlertDialog.Builder(this).setTitle("选择大类")
                            .setItems(firstList.toTypedArray()) { _, firstIndex ->
                                val secondList = it.data.list[mainIndex]._child[firstIndex]._child.map { item ->
                                    if (item.isValid == 1) item.name else "${item.name}（当前不可用）"
                                }
                                AlertDialog.Builder(this).setTitle("选择小类")
                                    .setItems(secondList.toTypedArray()) { _, secondIndex ->
                                        OrderHelper.areaId =
                                            it.data.list[mainIndex]._child[firstIndex]._child[secondIndex].id.toString()
                                        log("你选择的是 ${secondList[secondIndex]}")
                                        if (it.data.list[mainIndex]._child[firstIndex]._child[secondIndex].isValid != 1)
                                            log("Warn: 请注意，选择项目前不可用")
                                        updateConfigToUI()
                                    }.show()
                            }.show()
                    }.show()
                }
                handler.sendMessage(msg)
            }.exceptionally {
                hideProgressing()
                // 如果发生异常导致执行失败，则给出适当提示
                OrderHelper.dealWithException(log)(it)
                return@exceptionally null
            }
        }
        // 设置选取预约时间按钮的回调
        button_chooseOrderTime.setOnClickListener {
            val choiceValues = OrderTimeType.values()
            val choiceList = choiceValues.map { it.name }
            val ca = Calendar.getInstance()
            val chooseTypeDialog =
                AlertDialog.Builder(this).setTitle("选择预约时间类型").setItems(choiceList.toTypedArray()) { _, index ->
                    if (choiceValues[index] == OrderTimeType.DEFAULT || choiceValues[index] == OrderTimeType.CHECKED) {
                        showProgressing()
                        OrderHelper.getSpaceDay().thenAccept {
                            hideProgressing()
                            if (it.status != 1) {
                                throw OrderFailException("获取可预约日期失败", it.toString())
                            }
                            val availableDays = it.data.list.map { item -> item.day }
                            // 在主线程中更新UI
                            val msg = Message()
                            msg.obj = Runnable {
                                AlertDialog.Builder(this).setTitle("提示").setMessage("注意，当前可预约日期为：\n$availableDays")
                                    .setCancelable(false)
                                    .setPositiveButton("我知道了") { _, _ ->
                                        DatePickerDialog(this).apply {
                                            setOnDateSetListener { _, year, month, dayOfMonth ->
                                                ca.set(Calendar.YEAR, year)
                                                ca.set(Calendar.MONTH, month)
                                                ca.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                                val chosenDate = String.format("%tF", ca)
                                                OrderHelper.orderDay = chosenDate
                                                orderTimeType = choiceValues[index]
                                                log("你选择的预约时间类型是：${orderTimeType?.name}，具体预约的时间为：$chosenDate")
                                                updateConfigToUI()
                                            }
                                            updateDate(
                                                ca.get(Calendar.YEAR),
                                                ca.get(Calendar.MONTH),
                                                ca.get(Calendar.DAY_OF_MONTH)
                                            )
                                        }.show()
                                    }.show()
                            }
                            handler.sendMessage(msg)
                        }.exceptionally {
                            hideProgressing()
                            // 如果发生异常导致执行失败，则给出适当提示
                            OrderHelper.dealWithException(log)(it)
                            return@exceptionally null
                        }
                    } else {
                        orderTimeType = choiceValues[index]
                        log("你选择的预约时间类型是：${orderTimeType?.name}")
                        updateConfigToUI()
                    }
                }
            if (hasOrderTimeTypeTip) {
                AlertDialog.Builder(this).setTitle("提示").setMessage(orderTimeTypeTip)
                    .setPositiveButton("我知道了") { _, which ->
                        if (which == AlertDialog.BUTTON_POSITIVE) {
                            chooseTypeDialog.show()
                        }
                    }
                    .setNegativeButton("不再提示") { _, which ->
                        if (which == AlertDialog.BUTTON_NEGATIVE) {
                            hasOrderTimeTypeTip = false
                            chooseTypeDialog.show()
                        }
                    }.show()
            } else {
                chooseTypeDialog.show()
            }
        }
        // 设置选取座位按钮的回调
        val seatPickerDialog: (it: SpaceOldJSON) -> Unit = {
            val availableSeat = it.data.list.map { item -> item.name + if (item.status != 1) "（不可用）" else "" }
            val msg = Message()
            msg.obj = Runnable {
                hideProgressing()
                AlertDialog.Builder(this).setTitle("选择座位").setItems(availableSeat.toTypedArray()) { _, index ->
                    OrderHelper.seatId = it.data.list[index].id.toString()
                    log("你选择的座位名是 \"${availableSeat[index]}\"")
                    if (it.data.list[index].status != 1) {
                        log("Warn: 请注意，选择项目前不可用")
                    }
                    updateConfigToUI()
                }.show()
            }
            handler.sendMessage(msg)
        }
        val seatPicker = {
            showProgressing()
            OrderHelper.orderDay = String.format("%tF", Calendar.getInstance())
            OrderHelper.timeSegment = "0"
            OrderHelper.getSpaceOld("8:00", "23:50").thenAccept(seatPickerDialog).exceptionally {
                hideProgressing()
                // 如果发生异常导致执行失败，则给出适当提示
                OrderHelper.dealWithException(log)(it)
                return@exceptionally null
            }
        }
        button_chooseSeat.setOnClickListener {
            if (orderTimeType == OrderTimeType.DEFAULT || orderTimeType == OrderTimeType.CHECKED) {
                showProgressing()
                OrderHelper.getSpaceTime().thenApply {
                    if (it.status != 1) {
                        throw OrderFailException("获取目标日期可预约时间段失败！", it.toString())
                    }
                    OrderHelper.timeSegment = it.data.list[0].id.toString()
                    OrderHelper.getSpaceOld(it.data.list[0].startTime, it.data.list[0].endTime).get()
                }.thenAccept(seatPickerDialog).exceptionally {
                    hideProgressing()
                    // 如果发生异常导致执行失败，则给出适当提示
                    OrderHelper.dealWithException(log)(it)
                    return@exceptionally null
                }
            } else {
                AlertDialog.Builder(this).setTitle("提示").setMessage(orderSeatTip).setCancelable(false)
                    .setPositiveButton("我知道了") { _, _ ->
                        seatPicker()
                    }.show()
            }
        }
        // 设置Switch回调
        switch_autoChooseSeat.setOnCheckedChangeListener { _, isChecked -> hasAutoChooseSeat = isChecked }
        // 设置选取定时运行时间按钮的回调
        button_chooseTimingTime.setOnClickListener {
            timingTime = timingTime ?: Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hourOfDay, minute ->
                    timingTime!!.set(Calendar.HOUR_OF_DAY, hourOfDay)
                    timingTime!!.set(Calendar.MINUTE, minute)
                    timingTime!!.set(Calendar.SECOND, 1)
                    log(String.format("你选择的定时运行时间为：%tT", timingTime))
                    updateConfigToUI()
                },
                timingTime!!.get(Calendar.HOUR_OF_DAY),
                timingTime!!.get(Calendar.MINUTE),
                true
            ).show()
        }
        // 设置预约测试按钮的回调
        testButton.setOnClickListener {
            if (orderTimeType == null) {
                log("Error: 请先选择预约时间")
                return@setOnClickListener
            }
            OrderHelper.runWithTimer(orderTimeType!!, hasAutoChooseSeat, log)
                .exceptionally {
                    // 如果发生异常导致执行失败，则给出适当提示
                    OrderHelper.dealWithException(log)(it)
                    return@exceptionally null
                }
        }
        // 设置保存&定时运行按钮的回调
        saveButton.setOnClickListener {
            OrderHelper.saveConfig(this)
            saveConfig()
            log("i: 配置已保存！")
            if (timingTime == null) {
                log("Error: 请先设置定时运行时间！")
                return@setOnClickListener
            }
            // 设置定时任务
            val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, AutoTimer::class.java)
            intent.setPackage(packageName)
            val alarmIntent = PendingIntent.getBroadcast(this, 0, intent, 0)
            alarmMgr.cancel(alarmIntent)
            val ca = Calendar.getInstance()
            timingTime?.apply {
                set(ca.get(Calendar.YEAR), ca.get(Calendar.MONTH), ca.get(Calendar.DAY_OF_MONTH))
                if (get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE) <= ca.get(Calendar.HOUR_OF_DAY) * 60 + ca.get(
                        Calendar.MINUTE
                    )
                ) {
                    set(Calendar.DAY_OF_MONTH, get(Calendar.DAY_OF_MONTH) + 1)
                }
                alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, alarmIntent)
            }
            button_cancelTiming.visibility = View.VISIBLE
            // 设置重启恢复
            val receiver = ComponentName(this, BootReceiver::class.java)
            packageManager.setComponentEnabledSetting(
                receiver,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            log("i: 定时任务已设置！")
            log("i: 重启监听已开启，将自动恢复定时任务")
            log(String.format("下次运行时间：%tF %tT", timingTime, timingTime))
        }
        // 设置取消定时任务按钮的回调
        button_cancelTiming.setOnClickListener {
            // 取消定时任务
            val alarmMgr = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(this, AutoTimer::class.java)
            val alarmIntent = PendingIntent.getBroadcast(this, 0, intent, 0)
            alarmMgr.cancel(alarmIntent)
            button_chooseTimingTime.setText(R.string.timeChoiceButtonHint)
            button_cancelTiming.visibility = View.INVISIBLE
            log("i: 定时任务已取消")
            // 取消重启监听
            val receiver = ComponentName(this, BootReceiver::class.java)
            packageManager.setComponentEnabledSetting(
                receiver,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            timingTime = null
            saveConfig()
            log("i: 重启监听已关闭")
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val miu = MobileInfoUtils()
        when (item.itemId) {
            R.id.menu_DetailInterface -> miu.jumpDetailInterface(this)
            R.id.menu_StartInterface -> miu.jumpStartInterface(this)
            R.id.menu_ShowRecentDialog -> showRecentLog()
            R.id.menu_CancelBook -> {
                showProgressing()
                OrderHelper.loginNow().thenApply {
                    OrderHelper.getBookHistory().get()
                }.thenAccept {
                    hideProgressing()
                    if (it.status != 1) {
                        throw OrderFailException("获取预约历史失败！", it.toString())
                    }
                    val bookList = it.data.list!!.map { item ->
                        val timeSplit = item.bookTimeSegment.split("~", " ")
                        val timeInfo =
                            timeSplit[0] + ' ' + timeSplit[1].split(".")[0] + '~' + timeSplit[3].split(".")[0]
                        "${item.spaceInfo}\n[${item.statusName}]\n${timeInfo}\n"
                    }
                    val msg = Message()
                    msg.obj = Runnable {
                        val itemListener: (Int) -> Unit = { index ->
                            AlertDialog.Builder(this).setTitle("提示").setMessage("你确定要取消吗？（一天内可能有取消次数上限）")
                                .setPositiveButton("确定") { _, which ->
                                    if (which == AlertDialog.BUTTON_POSITIVE) {
                                        showProgressing()
                                        OrderHelper.cancelBookNow(it.data.list!![index].id.toString())
                                            .thenAccept { rs ->
                                                val msg1 = Message()
                                                msg1.obj = Runnable {
                                                    hideProgressing()
                                                    Toast.makeText(this, rs.msg, Toast.LENGTH_LONG).show()
                                                }
                                                handler.sendMessage(msg1)
                                            }.exceptionally { e ->
                                                hideProgressing()
                                                // 如果发生异常导致执行失败，则给出适当提示
                                                OrderHelper.dealWithException(log)(e)
                                                return@exceptionally null
                                            }
                                    }
                                }.setNegativeButton("再想想") { _, _ -> }.show()
                        }
                        AlertDialog.Builder(this).setTitle("查看&取消预约")
                            .setItems(bookList.toTypedArray()) { _, index ->
                                itemListener(index)
                            }.show()
                    }
                    handler.sendMessage(msg)
                }.exceptionally {
                    hideProgressing()
                    // 如果发生异常导致执行失败，则给出适当提示
                    OrderHelper.dealWithException(log)(it)
                    if(it.cause is OrderFailException){
                        log("提示：可能是你还没有预约记录")
                    }
                    return@exceptionally null
                }
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        OrderHelper.saveConfig(this)
        saveConfig()
    }

    private fun showRecentLog() {
        if (logHistory.isEmpty()) {
            log("--暂无记录--")
        } else {
            logHistoryIterator = logHistoryIterator ?: logHistory.iterator()
            logHistoryIterator?.apply {
                var i = 0
                while (i < 5 && this.hasNext()) {
                    log(this.next())
                    i++
                }
                if (this.hasNext()) {
                    log("重复操作再看5个...")
                } else {
                    log("到底了...")
                }
            }
        }
    }

    private fun showProgressing() {
        defaultProgressDialog?.show()
    }

    private fun hideProgressing() {
        defaultProgressDialog?.dismiss()
    }

    /**
     * 打印即时日志并在必要时滚动
     */
    private val log: ((message: String) -> Unit) =
        { message: String ->
            logTextView.append("$message\n")
            logTextView.layout?.let {
                val scrollAmount: Int =
                    it.getLineTop(logTextView.lineCount) - logTextView.height
                logTextView.scrollTo(0, scrollAmount.coerceAtLeast(0))
            }
        }
}