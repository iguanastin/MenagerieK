package com.github.iguanastin.view.dialog

import com.github.iguanastin.app.menagerie.import.ImportJob
import javafx.beans.property.*

class ImportNotification(val job: ImportJob) {

    val statusProperty: StringProperty = SimpleStringProperty("Waiting")
    var status: String
        get() = statusProperty.get()
        set(value) = statusProperty.set(value)

    val progressProperty: DoubleProperty = SimpleDoubleProperty(-1.0)
    var progress: Double
        get() = progressProperty.get()
        set(value) = progressProperty.set(value)

    val finishedProperty: BooleanProperty = SimpleBooleanProperty(false)
    var isFinished: Boolean
        get() = finishedProperty.get()
        set(value) = finishedProperty.set(value)

    val errorProperty: BooleanProperty = SimpleBooleanProperty(false)
    var isError: Boolean
        get() = errorProperty.get()
        set(value) = errorProperty.set(value)

    init {
        job.onStart.add {
            status = "Started"
            progress = 0.0
        }
        job.onProgress.add { status, progress ->
            this.status = status
            this.progress = progress
        }
        job.onFinish.add {
            status = "Finished"
            progress = 1.0
            isFinished = true
        }
        job.onError.add {
            status = "Exception occurred"
            progress = 0.0
            isFinished = true
            isError = true
        }
    }

}