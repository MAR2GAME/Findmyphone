package com.findmyphone.gpstracker.utils

import kotlin.collections.isNotEmpty
import kotlin.collections.toList
import kotlin.ranges.until
import kotlin.to

/**
 * 循环迭代器 - 用于按照顺序循环反复地从集合中取值
 * @param list 要循环的集合，不能为空
 * @throws IllegalArgumentException 如果传入空集合
 */
class CircularIterator<T> private constructor(private val list: List<T>) {

    companion object {
        /**
         * 创建循环迭代器
         * @param list 要循环的集合
         * @return CircularIterator 实例
         * @throws IllegalArgumentException 如果传入空集合
         */
        fun <T> of(list: List<T>): CircularIterator<T> {
            require(list.isNotEmpty()) { "集合不能为空" }
            return CircularIterator(list)
        }

        /**
         * 安全创建循环迭代器，如果集合为空则返回null
         */
        fun <T> ofOrNull(list: List<T>): CircularIterator<T>? {
            return if (list.isNotEmpty()) CircularIterator(list) else null
        }
    }

    private val size: Int = list.size
    private var currentIndex: Int = 0

    /**
     * 获取下一个元素
     * @return 下一个元素
     */
    fun next(): T {
        val element = list[currentIndex]
        currentIndex = (currentIndex + 1) % size
        return element
    }
    /**
     * 获取指定步长后的元素
     * @param steps 步长（默认为1）
     * @return 步长后的元素
     */
    fun next(steps: Int = 1): T {
        require(steps > 0) { "步长必须大于0" }
        currentIndex = (currentIndex + steps) % size
        return list[currentIndex]
    }
    /**
     * 获取原始集合的大小
     */
    fun size(): Int = size
    /**
     * 跳过指定数量的元素
     * @param count 要跳过的元素数量
     */
    fun skip(count: Int) {
        require(count >= 0) { "跳过的数量不能为负数" }
        currentIndex = (currentIndex + count) % size
    }

}
