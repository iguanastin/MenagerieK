package com.github.iguanastin.app.menagerie.duplicates.local

import com.github.iguanastin.app.menagerie.model.*
import jcuda.Pointer
import jcuda.Sizeof
import jcuda.driver.*
import java.io.File
import kotlin.math.ceil

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
    fun findDuplicates(
        smallSet: List<Item>,
        largeSet: List<Item>,
        confidence: Float,
        maxResults: Int
    ): List<SimilarPair<Item>> {

        // TODO find exact hash duplicates (CUDA only does histogram similarities)
        // Probably need another kernel for this

        // Construct a clean dataset
        val trueSet1: List<ImageItem> = getCleanedSet(smallSet)
        val trueSet2: List<ImageItem> = getCleanedSet(largeSet)

        if (trueSet1.isEmpty() || trueSet2.isEmpty()) return emptyList()

        // Get adjusted dataset size. Padded to avoid memory access errors with 64 thread blocks.
        val n1 = ceil(trueSet1.size / 64.0).toInt() * 64
        val n2 = trueSet2.size

        // Initialize the device and kernel
        val function = initCUFunction()

        // Init data array
        val data1 = initDataArray(trueSet1, n1)
        val data2 = initDataArray(trueSet2, n2)
        // Init confidence array
        val confs1 = initConfsArray(confidence, trueSet1, n1)
        val confs2 = initConfsArray(confidence, trueSet2, n2)
        //Init ids arrays
        val ids1 = initIdsArray(trueSet1, n1)
        val ids2 = initIdsArray(trueSet2, n2)

        // Allocate and copy data to device
        val dData1 = CUdeviceptr()
        val dData2 = CUdeviceptr()
        val dConfs1 = CUdeviceptr()
        val dConfs2 = CUdeviceptr()
        val dIds1 = CUdeviceptr()
        val dIds2 = CUdeviceptr()
        val dResultsid1 = CUdeviceptr()
        val dResultsid2 = CUdeviceptr()
        val dResultssimilarity = CUdeviceptr()
        val dResultcount = CUdeviceptr()
        allocateAndCopyToDevice(
            maxResults,
            n1,
            n2,
            data1,
            data2,
            confs1,
            confs2,
            ids1,
            ids2,
            dData1,
            dData2,
            dConfs1,
            dConfs2,
            dIds1,
            dIds2,
            dResultsid1,
            dResultsid2,
            dResultssimilarity,
            dResultcount
        )

        // Launch kernel
        launchKernel(
            maxResults,
            function,
            n1,
            n2,
            dData1,
            dData2,
            dConfs1,
            dConfs2,
            dIds1,
            dIds2,
            dResultsid1,
            dResultsid2,
            dResultssimilarity,
            dResultcount
        )

        // Get results from device
        val results: List<SimilarPair<Item>> =
            getResultsFromDevice(largeSet[0].menagerie, dResultsid1, dResultsid2, dResultssimilarity, dResultcount)

        // Free device memory
        freeDeviceMemory(
            dData1,
            dData2,
            dConfs1,
            dConfs2,
            dIds1,
            dIds2,
            dResultsid1,
            dResultsid2,
            dResultssimilarity,
            dResultcount
        )

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
        val ptx = if (File(PTX).exists()) PTX else "app/$PTX"
        val module = CUmodule()
        JCudaDriver.cuModuleLoad(module, ptx)

        // Get function reference
        val function = CUfunction()
        JCudaDriver.cuModuleGetFunction(function, module, KERNEL_FUNCTION_NAME)
        return function
    }

    private fun getResultsFromDevice(
        menagerie: Menagerie,
        d_resultsID1: CUdeviceptr,
        d_resultsID2: CUdeviceptr,
        d_resultsSimilarity: CUdeviceptr,
        d_resultCount: CUdeviceptr
    ): List<SimilarPair<Item>> {
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
        JCudaDriver.cuMemcpyDtoH(
            Pointer.to(resultsSimilarity),
            d_resultsSimilarity,
            resultCount * Sizeof.FLOAT.toLong()
        )
        val results: MutableList<SimilarPair<Item>> = mutableListOf()
        for (i in 0 until resultCount) {
            val i1 = menagerie.getItem(resultsID1[i])
            val i2 = menagerie.getItem(resultsID2[i])
            if (i1 != null && i2 != null) {
                val pair: SimilarPair<Item> = SimilarPair(i1, i2, resultsSimilarity[i].toDouble())
                if (!results.contains(pair)) results.add(pair)
            }
        }
        return results
    }

    private fun launchKernel(
        maxResults: Int,
        function: CUfunction,
        N1: Int,
        N2: Int,
        d_data1: CUdeviceptr,
        d_data2: CUdeviceptr,
        d_confs1: CUdeviceptr,
        d_confs2: CUdeviceptr,
        d_ids1: CUdeviceptr,
        d_ids2: CUdeviceptr,
        d_resultsID1: CUdeviceptr,
        d_resultsID2: CUdeviceptr,
        d_resultsSimilarity: CUdeviceptr,
        d_resultCount: CUdeviceptr
    ) {
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
        JCudaDriver.cuLaunchKernel(function, ceil(N1 / 64.0).toInt(), 1, 1, 64, 1, 1, 0, null, kernelParameters, null)
        JCudaDriver.cuCtxSynchronize()
    }

    private fun allocateAndCopyToDevice(
        maxResults: Int,
        N1: Int,
        N2: Int,
        data1: FloatArray,
        data2: FloatArray,
        confs1: FloatArray,
        confs2: FloatArray,
        ids1: IntArray,
        ids2: IntArray,
        d_data1: CUdeviceptr,
        d_data2: CUdeviceptr,
        d_confs1: CUdeviceptr,
        d_confs2: CUdeviceptr,
        d_ids1: CUdeviceptr,
        d_ids2: CUdeviceptr,
        d_resultsID1: CUdeviceptr,
        d_resultsID2: CUdeviceptr,
        d_resultsSimilarity: CUdeviceptr,
        d_resultCount: CUdeviceptr
    ) {
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
                confs[i] = if (trueSet[i].histogram!!.isColorful) confidence else confidenceSquare
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
