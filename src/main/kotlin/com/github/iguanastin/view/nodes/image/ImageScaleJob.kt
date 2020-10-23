package com.github.iguanastin.view.nodes.image

import javafx.scene.image.Image

class ImageScaleJob(val source: Image, val targetScale: Double, var onSuccess: (Image) -> Unit, var onError: (Throwable) -> Unit = {})