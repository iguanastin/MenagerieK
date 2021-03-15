package com.github.iguanastin.app.menagerie.duplicates.local

import com.github.iguanastin.app.menagerie.model.Item
import com.github.iguanastin.app.menagerie.model.SimilarPair
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.ceil

object CPUDuplicateFinder {

    fun findDuplicates(set1: List<Item>, set2: List<Item>, confidence: Double = 0.95, threaded: Boolean = true): List<SimilarPair<Item>> {
        if (threaded) {
            val large = if (set1.size > set2.size) set1 else set2
            val small = if (large === set1) set2 else set1

            val threadCount = Runtime.getRuntime().availableProcessors().coerceAtMost(large.size)
            val blockSize = ceil(large.size.toDouble() / threadCount).toInt()

            val threads: MutableSet<Thread> = mutableSetOf()
            val resultSets = Collections.synchronizedSet(mutableSetOf<List<SimilarPair<Item>>>())

            for (i in 0 until threadCount) {
                threads.add(thread(start = true, isDaemon = true) {
                    resultSets.add(findDuplicatesUtility(large.subList(i * blockSize, ((i + 1) * blockSize).coerceAtMost(large.size)), small, confidence))
                })
            }

            // Wait for all threads to finish
            threads.forEach { it.join() }

            // Combine results
            val results = mutableListOf<SimilarPair<Item>>()
            resultSets.forEach { set ->
                set.forEach { pair ->
                    if (pair !in results) results.add(pair)
                }
            }
            return results
        } else {
            return findDuplicatesUtility(set1, set2, confidence)
        }
    }

    private fun findDuplicatesUtility(set1: List<Item>, set2: List<Item>, confidence: Double): List<SimilarPair<Item>> {
        val results = mutableListOf<SimilarPair<Item>>()

        for (i1 in set1) {
            for (i2 in set2) {
                if (i1 == i2) continue

                val similarity = i1.similarityTo(i2)
                if (similarity > confidence) {
                    results.add(SimilarPair(i1, i2, similarity))
                }
            }
        }

        return results
    }

}