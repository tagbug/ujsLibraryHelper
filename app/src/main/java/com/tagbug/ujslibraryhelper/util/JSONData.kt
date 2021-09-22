package com.tagbug.ujslibraryhelper.util

import com.squareup.moshi.JsonClass

/**
 * JSON from http://libspace.ujs.edu.cn/api.php/login
 */
@JsonClass(generateAdapter = true)
data class LoginJSON(var status: Int, var msg: String, var data: LoginData)

@JsonClass(generateAdapter = true)
data class LoginData(var list: LoginList, var _hash_: LoginHash)

@JsonClass(generateAdapter = true)
data class LoginList(var id: String, var name: String, var deptName: String)

@JsonClass(generateAdapter = true)
data class LoginHash(var access_token: String, var expire: String)

/**
 * JSON from http://libspace.ujs.edu.cn/api.php/areas?tree=1
 */
@JsonClass(generateAdapter = true)
data class AreaTreeJSON(var status: Int, var msg: String, var data: AreaTreeData)

@JsonClass(generateAdapter = true)
data class AreaTreeData(var list: List<AreaTreeListItem>)

@JsonClass(generateAdapter = true)
data class AreaTreeListItem(var name: String, var isValid: Int, var _child: List<AreaTreeListFirstChild>)

@JsonClass(generateAdapter = true)
data class AreaTreeListFirstChild(var id: Int, var isValid: Int, var name: String, var _child: List<AreaTreeListSecondChild>)

@JsonClass(generateAdapter = true)
data class AreaTreeListSecondChild(var id: Int, var isValid: Int, var name: String)

/**
 * JSON from http://libspace.ujs.edu.cn/api.php/space_days/(12就是上面的areaId)
 */
@JsonClass(generateAdapter = true)
data class SpaceDayJSON(var status: Int, var msg: String, var data: SpaceDayData)

@JsonClass(generateAdapter = true)
data class SpaceDayData(var list: List<SpaceDayListItem>)

@JsonClass(generateAdapter = true)
data class SpaceDayListItem(var day: String)

/**
 * JSON from http://libspace.ujs.edu.cn/api.php/space_time_buckets?area=(12就是上面的areaId)&day=(2021-09-19类似格式的时间)
 */
@JsonClass(generateAdapter = true)
data class SpaceTimeJSON(var status: Int, var msg: String, var data: SpaceTimeData)

@JsonClass(generateAdapter = true)
data class SpaceTimeData(var list: List<SpaceTimeListItem>)

@JsonClass(generateAdapter = true)
data class SpaceTimeListItem(var id: Int, var startTime: String, var endTime: String)

/**
 * JSON from http://libspace.ujs.edu.cn/api.php/spaces_old?area=(12)&day=(2021-09-19)&endTime=(23:50)&segment=(35407上一部的id)&startTime=(18:47)
 */
@JsonClass(generateAdapter = true)
data class SpaceOldJSON(var status: Int, var msg: String, var data: SpaceOldData)

@JsonClass(generateAdapter = true)
data class SpaceOldData(var list: List<SpaceOldListItem>)

@JsonClass(generateAdapter = true)
data class SpaceOldListItem(var id: Int, var name: String, var status: Int, var status_name: String)

/**
 * JSON from http://libspace.ujs.edu.cn/api.php/spaces/(664对应上一步具体座位id)
 */
@JsonClass(generateAdapter = true)
data class SpaceJSON(var status: Int, var msg: String, var data: SpaceData)

@JsonClass(generateAdapter = true)
data class SpaceData(var list: SpaceList)

@JsonClass(generateAdapter = true)
data class SpaceList(var name: String, var status: Int, var areaInfo: SpaceAreaInfo)

@JsonClass(generateAdapter = true)
data class SpaceAreaInfo(var type: Int)

/**
 * JSON from http://libspace.ujs.edu.cn/api.php/spaces/(664)/book
 */
@JsonClass(generateAdapter = true)
data class BookJSON(var status: Int, var msg: String, var data: BookData)

@JsonClass(generateAdapter = true)
data class BookData(var list: BookList)

@JsonClass(generateAdapter = true)
data class BookList(var id: Int)