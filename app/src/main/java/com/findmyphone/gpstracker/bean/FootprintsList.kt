package com.findmyphone.gpstracker.bean

class  FootprintsList : ArrayList<FootprintsListItem>()

data class FootprintsListItem(
    val historyId: Int,
    val locationDetail: String,
    val timestamp: Long
)