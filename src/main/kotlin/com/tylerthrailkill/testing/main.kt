package com.tylerthrailkill.testing

import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.embed.swing.SwingFXUtils
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.CvType.CV_32F
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfInt
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import tornadofx.App
import tornadofx.View
import tornadofx.action
import tornadofx.attachTo
import tornadofx.button
import tornadofx.flowpane
import tornadofx.imageview
import tornadofx.label
import tornadofx.launch
import tornadofx.useMaxWidth
import java.awt.Rectangle
import java.awt.Robot
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import javax.imageio.ImageIO
import kotlin.math.abs
import tornadofx.*

fun main(args: Array<String>) {
    nu.pattern.OpenCV.loadLocally()
//    val mat = Mat.eye(5, 5, CvType.CV_8UC1)
//    println("mat = ${mat.dump()}")
    launch<MyApp>(args)
}

val secondHeartCoords = Rectangle(530, 20, 70, 60)
val testCoords = Rectangle(1670, 460, 70, 60)
val lastHeartCoords = Rectangle(835, 150, 75, 60)

class MyApp : App(MyView::class)
class MyView : View() {
    val histogram1 = imageview {
        id = "histogram1"
    }
    val histogram2 = imageview {
        id = "histogram2"
    }
    private val labelProperty = SimpleStringProperty("")
    var label by labelProperty
    
    override val root = flowpane {
        val testHeart = Robot().createScreenCapture(testCoords)
        val ivHeart1 = ImageView(SwingFXUtils.toFXImage(testHeart, null))
        val ivHeart2 = ImageView(SwingFXUtils.toFXImage(testHeart, null))
        val histogramView = ImageView(SwingFXUtils.toFXImage(testHeart, null))
        histogram1.attachTo(this)
        ivHeart1.attachTo(this)
        ivHeart2.attachTo(this)
        imageview {
            id = "heart1"
        }
        imageview {
            id = "heart2"
        }
        
        label = "hi"
        Thread.sleep(3000)
        label = "updated"


        button("Start") {
            useMaxWidth = true
            action {
                val base = bufferedImage2Mat(ImageIO.read(File("baseheart.jpg")))
                base.convertTo(base, CV_32F)
                Core.normalize(base, base, 0.0, 1.0, Core.NORM_MINMAX)
                
                repeat(100) {
                    GlobalScope.launch {
                        val secondHeart = Robot().createScreenCapture(secondHeartCoords)
                        println("running second heart")
                        Platform.runLater { ivHeart1.imageProperty().set(secondHeart.toImage()) }
                        val frame = bufferedImage2Mat(secondHeart)

                        frame.convertTo(frame, CV_32F);
                        Core.normalize(frame, frame, 0.0, 1.0, Core.NORM_MINMAX)
                        val baseCompare = Imgproc.compareHist(base, base, 2)
                        val frameCompare = Imgproc.compareHist(base, frame, 2)
//                        println("base: $baseCompare frame: $frameCompare diff ${abs(baseCompare - frameCompare)}")
                        println("heart is ${if (abs(baseCompare - frameCompare) < 100) "present" else "not present"}")
                        showHistogram(frame, false)
                        delay(330)
                    }
                    GlobalScope.launch {
                        val lastHeart = Robot().createScreenCapture(lastHeartCoords)

                        println("running last heart")
                        Platform.runLater { ivHeart2.imageProperty().set(lastHeart.toImage()) }
                        delay(330)
                    }
                }

            }
        }
    }

    private fun showHistogram(frame: Mat, gray: Boolean) {
        // split the frames in multiple images
        val images = ArrayList<Mat>()
        Core.split(frame, images)

        // set the number of bins at 256
        val histSize = MatOfInt(256)
        // only one channel
        val channels = MatOfInt(0)
        // set the ranges
        val histRange = MatOfFloat(0f, 256f)

        // compute the histograms for the B, G and R components
        val hist_b = Mat()
        val hist_g = Mat()
        val hist_r = Mat()

        // B component or gray image
        Imgproc.calcHist(images.subList(0, 1), channels, Mat(), hist_b, histSize, histRange, false)

        // G and R components (if the image is not in gray scale)
        if (!gray) {
            Imgproc.calcHist(images.subList(1, 2), channels, Mat(), hist_g, histSize, histRange, false)
            Imgproc.calcHist(images.subList(2, 3), channels, Mat(), hist_r, histSize, histRange, false)
        }

        // draw the histogram
        val hist_w = 150 // width of the histogram image
        val hist_h = 150 // height of the histogram image
        val bin_w = Math.round(hist_w / histSize.get(0, 0)[0]).toDouble()

        val histImage = Mat(hist_h, hist_w, CvType.CV_8UC3, Scalar(0.0, 0.0, 0.0))
        // normalize the result to [0, histImage.rows()]
        Core.normalize(hist_b, hist_b, 0.0, histImage.rows().toDouble(), Core.NORM_MINMAX, -1, Mat())

        // for G and R components
        if (!gray) {
            Core.normalize(hist_g, hist_g, 0.0, histImage.rows().toDouble(), Core.NORM_MINMAX, -1, Mat())
            Core.normalize(hist_r, hist_r, 0.0, histImage.rows().toDouble(), Core.NORM_MINMAX, -1, Mat())
        }

        // effectively draw the histogram(s)
        var i = 1
        while (i < histSize.get(0, 0)[0]) {
            // B component or gray image
            Imgproc.line(
                histImage, Point(bin_w * (i - 1), hist_h - Math.round(hist_b.get(i - 1, 0)[0]).toDouble()),
                Point(bin_w * i, hist_h - Math.round(hist_b.get(i, 0)[0]).toDouble()), Scalar(255.0, 0.0, 0.0), 2, 8, 0
            )
            // G and R components (if the image is not in gray scale)
            if (!gray) {
                Imgproc.line(
                    histImage, Point(bin_w * (i - 1), hist_h - Math.round(hist_g.get(i - 1, 0)[0]).toDouble()),
                    Point(bin_w * i, hist_h - Math.round(hist_g.get(i, 0)[0]).toDouble()), Scalar(0.0, 255.0, 0.0), 2, 8,
                    0
                )
                Imgproc.line(
                    histImage, Point(bin_w * (i - 1), hist_h - Math.round(hist_r.get(i - 1, 0)[0]).toDouble()),
                    Point(bin_w * i, hist_h - Math.round(hist_r.get(i, 0)[0]).toDouble()), Scalar(0.0, 0.0, 255.0), 2, 8,
                    0
                )
            }
            i++
        }

        // display the histogram...
        val histImg = mat2Image(histImage)
        updateImageView(histogram1, histImg)

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
