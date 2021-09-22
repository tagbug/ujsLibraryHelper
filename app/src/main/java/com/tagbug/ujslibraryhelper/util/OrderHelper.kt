package com.tagbug.ujslibraryhelper.util

import android.content.Context
import androidx.core.content.edit
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.tagbug.ujslibraryhelper.OkHttp.CookieManager
import com.tagbug.ujslibraryhelper.util.OrderHelper.OrderTimeType.*
import okhttp3.*
import java.io.IOException
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

/**
 * 预约助手核心
 */
object OrderHelper {
    private val executor = Executors.newCachedThreadPool()
    private lateinit var client: OkHttpClient
    private val moshi = Moshi.Builder().build()

    /**
     * 配置项
     */
    private const val configFileName = "OrderHelper.Config"
    private const val defaultPassword = "c6f057b86584942e415435ffb1fa93d4" // 000的md5值
    var userId: String? = null
    private var accessToken: String? = null
    var areaId: String? = null
    var orderDay: String? = null
    var timeSegment: String? = null
    var seatId: String? = null
    private var spaceType: String? = null

    /**
     * 从sp获取配置
     */
    fun loadConfig(context: Context) {
        val sp = context.getSharedPreferences(configFileName, Context.MODE_PRIVATE)
        userId = sp.getString("userId", null)
        accessToken = sp.getString("accessToken", null)
        areaId = sp.getString("areaId", null)
        orderDay = sp.getString("orderDay", null)
        timeSegment = sp.getString("timeSegment", null)
        seatId = sp.getString("seatId", null)
        spaceType = sp.getString("spaceType", null)
    }

    /**
     * 将配置写入sp
     */
    fun saveConfig(context: Context) {
        val sp = context.getSharedPreferences(configFileName, Context.MODE_PRIVATE)
        sp.edit {
            putString("userId", userId)
            putString("accessToken", accessToken)
            putString("areaId", areaId)
            putString("orderDay", orderDay)
            putString("timeSegment", timeSegment)
            putString("seatId", seatId)
            putString("spaceType", spaceType)
        }
    }

    /**
     * 初始化方法，必须先运行一下
     */
    fun init(context: Context) {
        client = OkHttpClient.Builder()
            .cookieJar(CookieManager(context))
            .build()
        loadConfig(context)
    }

    /**
     * 异步发送请求
     *
     * @param request 请求体
     * @param callback 成功后的回调
     * @throws IOException 当请求失败或响应体为空时抛出
     */
    private fun <T> sendRequest(
        request: Request,
        callback: (responseBody: ResponseBody) -> T
    ): CompletableFuture<T> {
        return CompletableFuture.supplyAsync({
            try {
                return@supplyAsync client.newCall(request).execute().use { response -> callback(response.body!!) }
            } catch (e: java.lang.Exception) {
                throw IOException("请求时网络异常：${request.method} ${request.url}", e)
            }
        }, executor)
    }

    /**
     * 异步发送请求并处理返回的JSON数据
     *
     * @param request 请求体
     * @param type 用于Moshi对JSON对象解析的类
     */
    private fun <T> resolveJSONRequest(request: Request, type: Class<T>): CompletableFuture<T> {
        return sendRequest(request) {
            val reader = JsonReader.of(it.source())
            reader.isLenient = true
            return@sendRequest moshi.adapter(type).fromJson(reader) ?: throw Exception("JSON解析错误：$type")
        }
    }

    /**
     * 生成POST请求体
     *
     * @param url URL字符串
     * @param map 请求体键值对
     */
    private fun generatePostRequest(url: String, map: Map<String, String?>): Request {
        val formBodyBuilder = FormBody.Builder()
        for ((key, value) in map) {
            value?.let { formBodyBuilder.add(key, it) }
        }
        val formBody = formBodyBuilder.build()
        return Request.Builder().url(url).post(formBody).build()
    }

    /**
     * 生成GET请求体
     *
     * @param url URL字符串
     * @param map 请求体键值对
     */
    private fun generateGetRequest(url: String, map: Map<String, String?>?): Request {
        val sb = StringBuilder()
        sb.append(url)
        map?.let {
            sb.append('?')
            for ((key, value) in it) {
                sb.append("$key=$value&")
            }
            sb.removeSuffix("&")
        }
        return Request.Builder().url(sb.toString()).get().build()
    }

    /**
     * 检查map中的value是否为空
     *
     * @param args 要检查的键值对
     * @throws IllegalStateException 当任意value为null时抛出
     */
    private fun checkArgs(args: Map<String, String?>) {
        args.forEach { (key, value) ->
            if (value == null) {
                throw IllegalStateException("参数不能为空：$key 对应value为null！")
            }
        }
    }

    /**
     * 异步处理登录流程
     */
    fun loginNow(): CompletableFuture<LoginJSON> {
        return CompletableFuture.supplyAsync({
            val args = mapOf("username" to userId, "password" to defaultPassword, "from" to "mobile")
            checkArgs(args)
            val request = generatePostRequest("http://libspace.ujs.edu.cn/api.php/login", args)
            return@supplyAsync resolveJSONRequest(request, LoginJSON::class.java).get()
        }, executor)
    }

    /**
     * 异步处理获取可用空间
     */
    fun getAreaTree(): CompletableFuture<AreaTreeJSON> {
        val request = generateGetRequest("http://libspace.ujs.edu.cn/api.php/areas?tree=1", null)
        return resolveJSONRequest(request, AreaTreeJSON::class.java)
    }

    /**
     * 异步处理获取目前可预约日期
     */
    fun getSpaceDay(): CompletableFuture<SpaceDayJSON> {
        return CompletableFuture.supplyAsync({
            if (areaId == null) {
                throw IllegalStateException("参数不能为空：areaId 对应value为null！")
            }
            val request = generateGetRequest("http://libspace.ujs.edu.cn/api.php/space_days/$areaId", null)
            return@supplyAsync resolveJSONRequest(request, SpaceDayJSON::class.java).get()
        }, executor)
    }

    /**
     * 异步处理获取目标日期可预约时间断
     */
    fun getSpaceTime(): CompletableFuture<SpaceTimeJSON> {
        return CompletableFuture.supplyAsync({
            val args = mapOf("area" to areaId, "day" to orderDay)
            checkArgs(args)
            val request = generateGetRequest("http://libspace.ujs.edu.cn/api.php/space_time_buckets", args)
            return@supplyAsync resolveJSONRequest(request, SpaceTimeJSON::class.java).get()
        }, executor)
    }

    /**
     * 异步处理获取当前可用位置信息
     */
    fun getSpaceOld(startTime: String, endTime: String): CompletableFuture<SpaceOldJSON> {
        return CompletableFuture.supplyAsync({
            val args = mapOf(
                "area" to areaId,
                "day" to orderDay,
                "endTime" to endTime,
                "segment" to timeSegment,
                "startTime" to startTime
            )
            checkArgs(args)
            val request = generateGetRequest("http://libspace.ujs.edu.cn/api.php/spaces_old", args)
            return@supplyAsync resolveJSONRequest(request, SpaceOldJSON::class.java).get()
        }, executor)
    }

    /**
     * 异步处理获取所选位置详情信息
     */
    fun getSpaceDetail(): CompletableFuture<SpaceJSON> {
        return CompletableFuture.supplyAsync({
            if (seatId == null) {
                throw IllegalStateException("参数不能为空：seatId 对应value为null！")
            }
            val request = generateGetRequest("http://libspace.ujs.edu.cn/api.php/spaces/$seatId", null)
            return@supplyAsync resolveJSONRequest(request, SpaceJSON::class.java).get()
        }, executor)
    }

    /**
     * 异步处理预约位置流程
     */
    fun bookNow(): CompletableFuture<BookJSON> {
        return CompletableFuture.supplyAsync({
            val args = mapOf(
                "access_token" to accessToken,
                "userid" to userId,
                "type" to spaceType,
                "id" to seatId,
                "segment" to timeSegment
            )
            checkArgs(args)
            val request = generatePostRequest("http://libspace.ujs.edu.cn/api.php/spaces/$seatId/book", args)
            return@supplyAsync resolveJSONRequest(request, BookJSON::class.java).get()
        }, executor)
    }

    /**
     * 自动预约流程
     *
     * @param orderTimeType 定时运行的类型
     * @param autoSelectSeat 是否自动选取相邻座位（如果目标不可用）
     * @param logger 日志记录器（可以省略）
     */
    fun runWithTimer(
        orderTimeType: OrderTimeType,
        autoSelectSeat: Boolean,
        logger: ((message: String) -> Unit)?
    ): CompletableFuture<String> {
        return CompletableFuture.supplyAsync({
            // 先登录
            val loginFinished = loginNow().thenAccept {
                if (it.status != 1) {
                    throw OrderFailException("登录失败！", it.toString())
                }
                accessToken = it.data._hash_.access_token
                logger?.invoke("${it.msg}: 用户信息: ${it.data.list.name} ${it.data.list.deptName}")
            }
            // 根据不同的运行选项执行不同逻辑
            return@supplyAsync when (orderTimeType) {
                // 直接预约
                DEFAULT -> loginFinished.thenApply {
                    // 获取spaceType
                    val seatResult = getSpaceDetail().get()
                    if (seatResult.status != 1) {
                        throw OrderFailException("获取座位详情失败！", seatResult.toString())
                    }
                    spaceType = seatResult.data.list.areaInfo.type.toString()
                    logger?.invoke("${seatResult.msg}: $spaceType")
                    // 预约
                    val bookResult = bookNow().get()
                    if (bookResult.status != 1) {
                        throw OrderFailException("预约失败！", bookResult.toString())
                    }
                    logger?.invoke("${bookResult.msg}: 预约的ID为 ${bookResult.data.list.id}")
                    return@thenApply "${bookResult.msg}: 预约的ID为 ${bookResult.data.list.id}"
                }.get()
                // 先检查时间&座位可用性
                else -> loginFinished.thenApply {
                    // 设置预约日期
                    logger?.invoke("检查时间可用性...")
                    val dayResult = getSpaceDay().get()
                    val availableDays = dayResult.data.list
                    when (orderTimeType) {
                        CHECKED -> if (availableDays.all { it.day != orderDay }) throw OrderFailException(
                            "当前选取时间不可用！",
                            availableDays.toString()
                        )
                        TODAY -> {
                            orderDay = String.format("%tF", Calendar.getInstance()) // 获取当前日期
                            if (availableDays.all { it.day != orderDay }) throw OrderFailException(
                                "当前选取时间不可用！",
                                availableDays.toString()
                            )
                        }
                        TOMORROW -> {
                            val calendar = Calendar.getInstance()
                            calendar.add(Calendar.DAY_OF_MONTH, 1)
                            orderDay = String.format("%tF", calendar) // 获取当前日期
                            if (availableDays.all { it.day != orderDay }) throw OrderFailException(
                                "当前选取时间不可用！",
                                availableDays.toString()
                            )
                        }
                        FUTURE_TIME -> orderDay = availableDays.last().day
                        else -> throw Exception("不支持的预约时间类型！")
                    }
                    // 设置segment
                    val timeResult = getSpaceTime().get()
                    if (timeResult.status != 1) {
                        throw OrderFailException("获取segment失败！", timeResult.toString())
                    }
                    timeSegment = timeResult.data.list.first().id.toString()
                    logger?.invoke("检查位置可用性...")
                    // 检查座位可用性
                    var seatResult = getSpaceDetail().get()
                    if (seatResult.status != 1) {
                        throw OrderFailException("获取座位详情失败！", seatResult.toString())
                    }
                    // 如果当前座位不可用
                    if (seatResult.data.list.status != 1) {
                        // 未开启自动选取
                        if (!autoSelectSeat) {
                            throw OrderFailException("当前选取座位不可用！", seatResult.toString())
                        }
                        logger?.invoke("当前选取位置不可用，正在选取邻近可用位置...")
                        // 否则找邻近座位
                        val seatInfoResult =
                            getSpaceOld(
                                timeResult.data.list.first().startTime,
                                timeResult.data.list.first().endTime
                            ).get()
                        if (seatInfoResult.status != 1) {
                            throw OrderFailException("获取可用座位列表失败！", seatInfoResult.toString())
                        }
                        class SortSeat(val sortNum: Int, val seatId: Int)

                        val availableSeats = mutableListOf<SortSeat>()
                        val seatIdNum = seatId!!.toInt()
                        seatInfoResult.data.list.forEach { item ->
                            if (item.status == 1) availableSeats.add(
                                SortSeat(
                                    kotlin.math.abs(item.id - seatIdNum),
                                    item.id
                                )
                            )
                        }
                        // 对列表排序以寻找最合适空位
                        availableSeats.sortWith { a, b -> a.sortNum - b.sortNum }
                        seatId = availableSeats.first().seatId.toString()
                        logger?.invoke("找到合适位置：$seatId")
                    }
                    // 获取spaceType
                    seatResult = getSpaceDetail().get()
                    if (seatResult.status != 1) {
                        throw OrderFailException("获取座位详情失败！", seatResult.toString())
                    }
                    spaceType = seatResult.data.list.areaInfo.type.toString()
                    logger?.invoke("${seatResult.msg}: $spaceType")
                    // 预约
                    val bookResult = bookNow().get()
                    if (bookResult.status != 1) {
                        throw OrderFailException("预约失败！", bookResult.toString())
                    }
                    logger?.invoke("${bookResult.msg}: 预约的ID为 ${bookResult.data.list.id}")
                    return@thenApply "${bookResult.msg}: 预约的ID为 ${bookResult.data.list.id}"
                }.get()
            }
        }, executor)
    }

    fun dealWithException(
        baseLogger: ((message: String) -> Unit),
        extraLogger: ((message: String) -> Unit)?
    ): (Throwable) -> Unit {
        return { e ->
            var exception = e
            var logger = baseLogger
            if (extraLogger != null) {
                logger("运行错误，长按显示详细信息：")
                logger = extraLogger
            }
            if (exception is CompletionException) {
                exception = exception.cause ?: exception
            }
            if (exception is ExecutionException) {
                exception = exception.cause ?: exception
            }
            when (exception) {
                is java.lang.IllegalStateException -> {
                    logger("$exception\n请检查前一项是否未填写/未选择，如果在定时运行时报错请检查配置是否保存")
                }
                is IOException -> {
                    logger("$exception\n请检查网络连接是否正常")
                }
                is OrderFailException -> {
                    logger("$exception\n服务器返回数据异常，提供异常JSON以供参考：\n${exception.json}")
                }
                is java.lang.Exception -> {
                    logger("$exception\n这是一个预期之外的异常，请联系作者处理")
                }
            }
        }
    }

    fun dealWithException(logger: ((message: String) -> Unit)): (Throwable) -> Unit {
        return dealWithException(logger, null)
    }

    /**
     * 预约时间类型：
     * DEFAULT 默认，按照用户写定的时间&座位等信息预约，不会检查时间&座位可用性
     * CHECKED 同默认，但会检查先时间&座位是否可用再预约（下面的都会做检查）
     * TODAY 选择运行时当天
     * TOMORROW 选择运行时的后一天
     * FUTURE_TIME 会查询可用时间，并选择最靠后的那一个
     * FUTURE_TIME_NOT_TODAY 同上，但永远不会选择今天
     */
    enum class OrderTimeType { DEFAULT, CHECKED, TODAY, TOMORROW, FUTURE_TIME }

    class OrderFailException(message: String, val json: String) : Exception(message)
}