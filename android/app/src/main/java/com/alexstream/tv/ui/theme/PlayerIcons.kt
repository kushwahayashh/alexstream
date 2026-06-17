package com.alexstream.tv.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object PlayerIcons {
    val Play: ImageVector
        get() = ImageVector.Builder(
            name = "Play",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.White)) {
            moveTo(6f, 4f)
            verticalLineToRelative(16f)
            curveToRelative(0f, 0.552f, 0.448f, 1f, 1f, 1f)
            curveToRelative(0.184f, 0f, 0.364f, -0.051f, 0.524f, -0.148f)
            lineToRelative(13f, -8f)
            curveToRelative(0.468f, -0.288f, 0.614f, -0.902f, 0.326f, -1.37f)
            curveToRelative(-0.082f, -0.133f, -0.193f, -0.244f, -0.326f, -0.326f)
            lineToRelative(-13f, -8f)
            curveToRelative(-0.468f, -0.288f, -1.082f, -0.142f, -1.37f, 0.326f)
            curveToRelative(-0.097f, 0.16f, -0.148f, 0.34f, -0.148f, 0.524f)
            close()
        }.build()

    val Pause: ImageVector
        get() = ImageVector.Builder(
            name = "Pause",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.White)) {
            moveTo(9f, 4f)
            horizontalLineToRelative(-2f)
            curveToRelative(-1.105f, 0f, -2f, 0.895f, -2f, 2f)
            verticalLineToRelative(12f)
            curveToRelative(0f, 1.105f, 0.895f, 2f, 2f, 2f)
            horizontalLineToRelative(2f)
            curveToRelative(1.105f, 0f, 2f, -0.895f, 2f, -2f)
            verticalLineToRelative(-12f)
            curveToRelative(0f, -1.105f, -0.895f, -2f, -2f, -2f)
            close()
            moveTo(17f, 4f)
            horizontalLineToRelative(-2f)
            curveToRelative(-1.105f, 0f, -2f, 0.895f, -2f, 2f)
            verticalLineToRelative(12f)
            curveToRelative(0f, 1.105f, 0.895f, 2f, 2f, 2f)
            horizontalLineToRelative(2f)
            curveToRelative(1.105f, 0f, 2f, -0.895f, 2f, -2f)
            verticalLineToRelative(-12f)
            curveToRelative(0f, -1.105f, -0.895f, -2f, -2f, -2f)
            close()
        }.build()

    val Rewind: ImageVector
        get() = ImageVector.Builder(
            name = "Rewind",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.White)) {
            moveTo(20.341f, 4.247f)
            lineToRelative(-8f, 7f)
            curveToRelative(-0.457f, 0.4f, -0.457f, 1.106f, 0f, 1.506f)
            lineToRelative(8f, 7f)
            curveToRelative(0.647f, 0.565f, 1.659f, 0.106f, 1.659f, -0.753f)
            verticalLineToRelative(-14f)
            curveToRelative(0f, -0.86f, -1.012f, -1.318f, -1.659f, -0.753f)
            close()
            moveTo(9.341f, 4.247f)
            lineToRelative(-8f, 7f)
            curveToRelative(-0.457f, 0.4f, -0.457f, 1.106f, 0f, 1.506f)
            lineToRelative(8f, 7f)
            curveToRelative(0.647f, 0.565f, 1.659f, 0.106f, 1.659f, -0.753f)
            verticalLineToRelative(-14f)
            curveToRelative(0f, -0.86f, -1.012f, -1.318f, -1.659f, -0.753f)
            close()
        }.build()

    val Forward: ImageVector
        get() = ImageVector.Builder(
            name = "Forward",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.White)) {
            moveTo(2f, 5f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 0.86f, 1.012f, 1.318f, 1.659f, 0.753f)
            lineToRelative(8f, -7f)
            curveToRelative(0.457f, -0.4f, 0.457f, -1.106f, 0f, -1.506f)
            lineToRelative(-8f, -7f)
            curveToRelative(-0.647f, -0.565f, -1.659f, -0.106f, -1.659f, 0.753f)
            close()
            moveTo(13f, 5f)
            verticalLineToRelative(14f)
            curveToRelative(0f, 0.86f, 1.012f, 1.318f, 1.659f, 0.753f)
            lineToRelative(8f, -7f)
            curveToRelative(0.457f, -0.4f, 0.457f, -1.106f, 0f, -1.506f)
            lineToRelative(-8f, -7f)
            curveToRelative(-0.647f, -0.565f, -1.659f, -0.106f, -1.659f, 0.753f)
            close()
        }.build()

    val Caption: ImageVector
        get() = ImageVector.Builder(
            name = "Caption",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.White)) {
            moveTo(19f, 4f)
            arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 3f)
            verticalLineToRelative(10f)
            arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, -3f, 3f)
            horizontalLineToRelative(-14f)
            arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, -3f, -3f)
            verticalLineToRelative(-10f)
            arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, -3f)
            close()
            moveToRelative(-10.5f, 4f)
            arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2.5f, 2.5f)
            verticalLineToRelative(3f)
            arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 5f, 0f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2f, 0f)
            arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -1f, 0f)
            verticalLineToRelative(-3f)
            arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 1f, 0f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 0f)
            arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2.5f, -2.5f)
            moveToRelative(7f, 0f)
            arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2.5f, 2.5f)
            verticalLineToRelative(3f)
            arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, 5f, 0f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2f, 0f)
            arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -1f, 0f)
            verticalLineToRelative(-3f)
            arcToRelative(0.5f, 0.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 1f, 0f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 0f)
            arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, -2.5f, -2.5f)
        }.build()

    val Audio: ImageVector
        get() = ImageVector.Builder(
            name = "Audio",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(18.364f, 19.364f)
            arcToRelative(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, -12.728f, 0f)
        }.path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(15.536f, 16.536f)
            arcToRelative(5f, 5f, 0f, isMoreThanHalf = true, isPositiveArc = false, -7.072f, 0f)
        }.path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(11f, 13f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = false, 2f, 0f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = true, isPositiveArc = false, -2f, 0f)
        }.build()

    val CircleCheck: ImageVector
        get() = ImageVector.Builder(
            name = "CircleCheck",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(fill = SolidColor(Color.White)) {
            moveTo(17f, 3.34f)
            arcToRelative(10f, 10f, 0f, isMoreThanHalf = true, isPositiveArc = true, -14.995f, 8.984f)
            lineToRelative(-0.005f, -0.324f)
            lineToRelative(0.005f, -0.324f)
            arcToRelative(10f, 10f, 0f, isMoreThanHalf = false, isPositiveArc = true, 14.995f, -8.336f)
            close()
            moveToRelative(-1.293f, 5.953f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.32f, -0.083f)
            lineToRelative(-0.094f, 0.083f)
            lineToRelative(-3.293f, 3.292f)
            lineToRelative(-1.293f, -1.292f)
            lineToRelative(-0.094f, -0.083f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.403f, 1.403f)
            lineToRelative(0.083f, 0.094f)
            lineToRelative(2f, 2f)
            lineToRelative(0.094f, 0.083f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.226f, 0f)
            lineToRelative(0.094f, -0.083f)
            lineToRelative(4f, -4f)
            lineToRelative(0.083f, -0.094f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.083f, -1.32f)
            close()
        }.build()
}
