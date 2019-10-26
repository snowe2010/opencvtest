package com.tylerthrailkill.testing

import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.opencv.core.Core
import org.opencv.core.CvType.CV_32F
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import tornadofx.App
import tornadofx.View
import tornadofx.attachTo
import tornadofx.button
import tornadofx.flowpane
import tornadofx.label
import tornadofx.launch
import tornadofx.useMaxWidth
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import tornadofx.*
import kotlin.math.abs

fun main(args: Array<String>) {
    nu.pattern.OpenCV.loadLocally()
//    val mat = Mat.eye(5, 5, CvType.CV_8UC1)
//    println("mat = ${mat.dump()}")
    launch<MyApp>(args)
}

val firstHeartCoords = Rectangle(530, 20, 70, 60)
val lastHeartCoords = Rectangle(835, 150, 75, 60)

class MyApp : App(MyView::class)

class MyView : View() {

    val robot = Robot()

    private val firstHeartProperty = SimpleStringProperty("")
    var firstHeartString by firstHeartProperty
    var firstHeartLabel = label(firstHeartProperty) { }

    private val lastHeartProperty = SimpleStringProperty("")
    var lastHeartString by lastHeartProperty
    var lastHeartLabel = label(lastHeartProperty) { }

    val base = bufferedImage2Mat(ImageIO.read(File("baseheart.jpg")))
    var baseCompare: Double? = null


    init {
        base.convertTo(base, CV_32F)
        Core.normalize(base, base, 0.0, 1.0, Core.NORM_MINMAX)
        baseCompare = Imgproc.compareHist(base, base, 2)
        
        subscribe<RunStepRequest> { event ->
            runBlocking {
                launch {
                    while (shouldRun) {
                        runGame()
                        delay(200)
                    }
                }
            }
        }
    }

    var shouldRun = true

    override val root = flowpane {


        firstHeartLabel.attachTo(this)
        lastHeartLabel.attachTo(this)

        firstHeartString = "starting"
        lastHeartString = "starting"
        var i = 0
        subscribe<PrintStepEvent> { event ->
            firstHeartString = if (event.firstHeartPresent) "present$i" else "not present$i"
            lastHeartString = if (event.lastHeartPresent) "present$i" else "not present$i"
            i++
        }

        button("Start") {
            useMaxWidth = true
            setOnAction {
                shouldRun = true
                fire(RunStepRequest())
            }
        }

        button("Stop")
        {
            setOnAction {
                shouldRun = false
            }
        }
    }


    private fun runGame() {
        val heart = robot.createScreenCapture(firstHeartCoords)
        val frame = bufferedImage2Mat(heart)
        frame.convertTo(frame, CV_32F)
        Core.normalize(frame, frame, 0.0, 1.0, Core.NORM_MINMAX)

        val frameCompare = Imgproc.compareHist(base, frame, 2)

        val b = abs(baseCompare!! - frameCompare) < 100
        val present = if (b) "present" else "not present"

        fire(PrintStepEvent(b, b))
        val h1 = checkForHeart(firstHeartCoords, "second")
        val h2 = checkForHeart(lastHeartCoords, "last")
        fire(PrintStepEvent(h1, h2))
    }

    private fun checkForHeart(heartCoords: Rectangle, heartName: String): Boolean {
        val heart = robot.createScreenCapture(heartCoords)
        val frame = bufferedImage2Mat(heart)
        frame.convertTo(frame, CV_32F)
        Core.normalize(frame, frame, 0.0, 1.0, Core.NORM_MINMAX)

        val frameCompare = Imgproc.compareHist(base, frame, 2)

        val b = abs(baseCompare!! - frameCompare) < 100
        val present = if (b) "present" else "not present"
        println("$heartName heart is $present")
        return b
    }
}


fun mat2Image(frame: Mat): WritableImage? {
    try {
        return SwingFXUtils.toFXImage(matToBufferedImage(frame), null)
    } catch (e: Exception) {
        System.err.println("Cannot convert the Mat object: $e")
        return null
    }

}

fun matToBufferedImage(original: Mat): BufferedImage {
    // init
    var image: BufferedImage? = null
    val width = original.width()
    val height = original.height()
    val channels = original.channels()
    val sourcePixels = ByteArray(width * height * channels)
    original.get(0, 0, sourcePixels)

    if (original.channels() > 1) {
        image = BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR)
    } else {
        image = BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY)
    }
    val targetPixels = (image.raster.dataBuffer as DataBufferByte).data
    System.arraycopy(sourcePixels, 0, targetPixels, 0, sourcePixels.size)

    return image
}

fun BufferedImage.toImage() = SwingFXUtils.toFXImage(this, null)


private fun updateImageView(view: ImageView, image: WritableImage?) {
    Platform.runLater { view.imageProperty().set(image) }
}


fun bufferedImage2Mat(image: BufferedImage): Mat {

    val byteArrayOutputStream = ByteArrayOutputStream()
    ImageIO.write(image, "jpg", byteArrayOutputStream)
    byteArrayOutputStream.flush()
    return Imgcodecs.imdecode(MatOfByte(*byteArrayOutputStream.toByteArray()), Imgcodecs.CV_LOAD_IMAGE_UNCHANGED)

}

class RunStepRequest : FXEvent(EventBus.RunOn.BackgroundThread)
class PrintStepEvent(val firstHeartPresent: Boolean, val lastHeartPresent: Boolean) : FXEvent()
