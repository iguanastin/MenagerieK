package com.github.iguanastin.app.menagerie.duplicates

import com.github.iguanastin.app.menagerie.Histogram
import com.github.iguanastin.app.menagerie.ImageItem
import com.github.iguanastin.app.menagerie.Item
import jcuda.Pointer
import jcuda.Sizeof
import jcuda.driver.*
import java.util.*

object CUDADuplicateFinder {

    /**
     * Path to kernel .ptx file
     */
    private const val PTX = "./histdupe.ptx"
    private const val KERNEL_FUNCTION_NAME = "_Z14histDupeKernelPKfS0_S0_S0_PiS1_S1_S1_PfS1_iii"

    /**
     * Uses a CUDA device to find similar pairs of items based on their histograms.
     *
     * @param smallSet   Set of items to compare
     * @param largeSet   Set of items to compare against
     * @param confidence Confidence for similarity, value between 0 and 1
     * @param maxResults Maximum number of results to return
     * @return Set of similar pairs found with the given confidence
     */
    fun findDuplicates(smallSet: List<Item>, largeSet: List<Item>, confidence: Float, maxResults: Int): List<SimilarPair<ImageItem>> {

        // TODO find exact hash duplicates (CUDA only does histogram similarities)

        // Construct a clean dataset
        val trueSet1: List<ImageItem> = getCleanedSet(smallSet)
        val trueSet2: List<ImageItem> = getCleanedSet(largeSet)

        // Get adjusted dataset size. Padded to avoid memory access errors with 64 thread blocks.
        val N1 = Math.ceil(trueSet1.size / 64.0).toInt() * 64
        val N2 = trueSet2.size

        // Initialize the device and kernel
        val function = initCUFunction()

        // Init data array
        val data1 = initDataArray(trueSet1, N1)
        val data2 = initDataArray(trueSet2, N2)
        // Init confidence array
        val confs1 = initConfsArray(confidence, trueSet1, N1)
        val confs2 = initConfsArray(confidence, trueSet2, N2)
        //Init ids arrays
        val ids1 = initIdsArray(trueSet1, N1)
        val ids2 = initIdsArray(trueSet2, N2)

        // Allocate and copy data to device
        val d_data1 = CUdeviceptr()
        val d_data2 = CUdeviceptr()
        val d_confs1 = CUdeviceptr()
        val d_confs2 = CUdeviceptr()
        val d_ids1 = CUdeviceptr()
        val d_ids2 = CUdeviceptr()
        val d_resultsID1 = CUdeviceptr()
        val d_resultsID2 = CUdeviceptr()
        val d_resultsSimilarity = CUdeviceptr()
        val d_resultCount = CUdeviceptr()
        allocateAndCopyToDevice(maxResults, N1, N2, data1, data2, confs1, confs2, ids1, ids2, d_data1, d_data2, d_confs1, d_confs2, d_ids1, d_ids2, d_resultsID1, d_resultsID2, d_resultsSimilarity, d_resultCount)

        // Launch kernel
        launchKernel(maxResults, function, N1, N2, d_data1, d_data2, d_confs1, d_confs2, d_ids1, d_ids2, d_resultsID1, d_resultsID2, d_resultsSimilarity, d_resultCount)

        // Get results from device
        val results: List<SimilarPair<ImageItem>> = getResultsFromDevice(trueSet1, trueSet2, d_resultsID1, d_resultsID2, d_resultsSimilarity, d_resultCount)

        // Free device memory
        freeDeviceMemory(d_data1, d_data2, d_confs1, d_confs2, d_ids1, d_ids2, d_resultsID1, d_resultsID2, d_resultsSimilarity, d_resultCount)
        return results
    }

    private fun initIdsArray(set: List<ImageItem>, n: Int): IntArray {
        val ids1 = IntArray(n)
        for (i in 0 until n) {
            if (i < set.size) ids1[i] = set[i].id
        }
        return ids1
    }

    private fun freeDeviceMemory(vararg pointers: CUdeviceptr) {
        for (pointer in pointers) {
            JCudaDriver.cuMemFree(pointer)
        }
    }

    private fun getCleanedSet(set: List<Item>): List<ImageItem> {
        // Remove all items without histograms
        val trueSet: MutableList<ImageItem> = mutableListOf()
        set.forEach { item ->
            if (item is ImageItem && item.histogram != null) trueSet.add(item)
        }
        return trueSet
    }

    private fun initCUFunction(): CUfunction {
        // Init exceptions
        JCudaDriver.setExceptionsEnabled(true)

        // Init device and context
        JCudaDriver.cuInit(0)
        val device = CUdevice()
        JCudaDriver.cuDeviceGet(device, 0)
        val context = CUcontext()
        JCudaDriver.cuCtxCreate(context, 0, device)

        // Load CUDA module
        val module = CUmodule()
        JCudaDriver.cuModuleLoad(module, PTX)

        // Get function reference
        val function = CUfunction()
        JCudaDriver.cuModuleGetFunction(function, module, KERNEL_FUNCTION_NAME)
        return function
    }

    private fun getResultsFromDevice(set1: List<ImageItem>, set2: List<ImageItem>, d_resultsID1: CUdeviceptr, d_resultsID2: CUdeviceptr, d_resultsSimilarity: CUdeviceptr, d_resultCount: CUdeviceptr): List<SimilarPair<ImageItem>> {
        // Get result count from device
        val resultCountArr = IntArray(1)
        JCudaDriver.cuMemcpyDtoH(Pointer.to(resultCountArr), d_resultCount, Sizeof.INT.toLong())
        val resultCount = resultCountArr[0]
        // Get results from device
        val resultsID1 = IntArray(resultCount)
        val resultsID2 = IntArray(resultCount)
        val resultsSimilarity = FloatArray(resultCount)
        JCudaDriver.cuMemcpyDtoH(Pointer.to(resultsID1), d_resultsID1, resultCount * Sizeof.INT.toLong())
        JCudaDriver.cuMemcpyDtoH(Pointer.to(resultsID2), d_resultsID2, resultCount * Sizeof.INT.toLong())
        JCudaDriver.cuMemcpyDtoH(Pointer.to(resultsSimilarity), d_resultsSimilarity, resultCount * Sizeof.FLOAT.toLong())
        val results: MutableList<SimilarPair<ImageItem>> = ArrayList<SimilarPair<ImageItem>>()
        for (i in 0 until resultCount) {
            val pair: SimilarPair<ImageItem> = SimilarPair(getItemByID(resultsID1[i], set1, set2), getItemByID(resultsID2[i], set1, set2), resultsSimilarity[i].toDouble())
            if (!results.contains(pair)) results.add(pair)
        }
        return results
    }

    private fun getItemByID(id: Int, set1: List<ImageItem>, set2: List<ImageItem>): ImageItem {
        for (item in set1) {
            if (item.id == id) return item
        }
        for (item in set2) {
            if (item.id == id) return item
        }
        return null!!
    }

    private fun launchKernel(maxResults: Int, function: CUfunction, N1: Int, N2: Int, d_data1: CUdeviceptr, d_data2: CUdeviceptr, d_confs1: CUdeviceptr, d_confs2: CUdeviceptr, d_ids1: CUdeviceptr, d_ids2: CUdeviceptr, d_resultsID1: CUdeviceptr, d_resultsID2: CUdeviceptr, d_resultsSimilarity: CUdeviceptr, d_resultCount: CUdeviceptr) {
        // Set up kernel parameters
        val kernelParameters = Pointer.to(
                Pointer.to(d_data1),
                Pointer.to(d_data2),
                Pointer.to(d_confs1),
                Pointer.to(d_confs2),
                Pointer.to(d_ids1),
                Pointer.to(d_ids2),
                Pointer.to(d_resultsID1),
                Pointer.to(d_resultsID2),
                Pointer.to(d_resultsSimilarity),
                Pointer.to(d_resultCount),
                Pointer.to(intArrayOf(N1)),
                Pointer.to(intArrayOf(N2)),
                Pointer.to(intArrayOf(maxResults))
        )

        // Launch kernel
        JCudaDriver.cuLaunchKernel(function, Math.ceil(N1 / 64.0).toInt(), 1, 1, 64, 1, 1, 0, null, kernelParameters, null)
        JCudaDriver.cuCtxSynchronize()
    }

    private fun allocateAndCopyToDevice(maxResults: Int, N1: Int, N2: Int, data1: FloatArray, data2: FloatArray, confs1: FloatArray, confs2: FloatArray, ids1: IntArray, ids2: IntArray, d_data1: CUdeviceptr, d_data2: CUdeviceptr, d_confs1: CUdeviceptr, d_confs2: CUdeviceptr, d_ids1: CUdeviceptr, d_ids2: CUdeviceptr, d_resultsID1: CUdeviceptr, d_resultsID2: CUdeviceptr, d_resultsSimilarity: CUdeviceptr, d_resultCount: CUdeviceptr) {
        var bytes: Long = N1.toLong() * Histogram.BIN_SIZE * Histogram.NUM_CHANNELS * Sizeof.FLOAT
        JCudaDriver.cuMemAlloc(d_data1, bytes)
        JCudaDriver.cuMemcpyHtoD(d_data1, Pointer.to(data1), bytes)
        bytes = N2.toLong() * Histogram.BIN_SIZE * Histogram.NUM_CHANNELS * Sizeof.FLOAT
        JCudaDriver.cuMemAlloc(d_data2, bytes)
        JCudaDriver.cuMemcpyHtoD(d_data2, Pointer.to(data2), bytes)

        // Allocate and copy confs to device
        bytes = N1 * Sizeof.FLOAT.toLong()
        JCudaDriver.cuMemAlloc(d_confs1, bytes)
        JCudaDriver.cuMemcpyHtoD(d_confs1, Pointer.to(confs1), bytes)
        bytes = N2 * Sizeof.FLOAT.toLong()
        JCudaDriver.cuMemAlloc(d_confs2, bytes)
        JCudaDriver.cuMemcpyHtoD(d_confs2, Pointer.to(confs2), bytes)

        // Allocate and copy ids to device
        bytes = N1 * Sizeof.INT.toLong()
        JCudaDriver.cuMemAlloc(d_ids1, bytes)
        JCudaDriver.cuMemcpyHtoD(d_ids1, Pointer.to(ids1), bytes)
        bytes = N2 * Sizeof.INT.toLong()
        JCudaDriver.cuMemAlloc(d_ids2, bytes)
        JCudaDriver.cuMemcpyHtoD(d_ids2, Pointer.to(ids2), bytes)

        // Allocate results arrays on device
        bytes = maxResults * Sizeof.INT.toLong()
        JCudaDriver.cuMemAlloc(d_resultsID1, bytes)
        JCudaDriver.cuMemAlloc(d_resultsID2, bytes)
        bytes = maxResults * Sizeof.FLOAT.toLong()
        JCudaDriver.cuMemAlloc(d_resultsSimilarity, bytes)

        // Allocate result count on device
        JCudaDriver.cuMemAlloc(d_resultCount, Sizeof.INT.toLong())
    }

    private fun initConfsArray(confidence: Float, trueSet: List<ImageItem>, N: Int): FloatArray {
        val confs = FloatArray(N)
        val confidenceSquare = 1 - (1 - confidence) * (1 - confidence)
        for (i in 0 until N) {
            if (i < trueSet.size) {
                confs[i] = if (trueSet[i].histogram!!.isColorful!!) confidence else confidenceSquare
            } else {
                confs[i] = 2f // Impossible confidence
            }
        }
        return confs
    }

    private fun initDataArray(trueSet: List<ImageItem>, N: Int): FloatArray {
        val size: Int = Histogram.BIN_SIZE * Histogram.NUM_CHANNELS
        val data = FloatArray(N * size)
        for (i in trueSet.indices) {
            val hist: Histogram = trueSet[i].histogram!!
            for (j in 0 until Histogram.BIN_SIZE) {
                // Convert to float because GPUs work best with single precision
                data[i * size + j] = hist.alpha[j].toFloat()
                data[i * size + j + Histogram.BIN_SIZE] = hist.red[j].toFloat()
                data[i * size + j + Histogram.BIN_SIZE * 2] = hist.green[j].toFloat()
                data[i * size + j + Histogram.BIN_SIZE * 3] = hist.blue[j].toFloat()
            }
        }
        return data
    }
}