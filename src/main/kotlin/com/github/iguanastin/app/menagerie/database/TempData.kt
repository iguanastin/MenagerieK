package com.github.iguanastin.app.menagerie.database

import com.github.iguanastin.app.menagerie.model.Histogram
import java.io.File


data class TempImageV9(val noSimilar: Boolean, val histogram: Histogram?)

data class TempFileV9(val md5: String, val file: File)

data class TempItemV9(val id: Int, val added: Long)

data class TempGroupV9(val title: String, val items: List<Int>)