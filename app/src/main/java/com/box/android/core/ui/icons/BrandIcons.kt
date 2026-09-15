package com.box.android.core.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object BrandIcons {
    val Google: ImageVector
        get() {
            if (_google != null) return _google!!
            _google = ImageVector.Builder(
                name = "Google",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    fill = SolidColor(Color(0xFFEA4335)),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(12f, 5f)
                    curveTo(13.68f, 5f, 15.2f, 5.58f, 16.39f, 6.72f)
                    lineTo(19.95f, 3.16f)
                    curveTo(17.8f, 1.15f, 15.06f, 0f, 12f, 0f)
                    curveTo(7.39f, 0f, 3.42f, 2.64f, 1.44f, 6.49f)
                    lineTo(5.58f, 9.7f)
                    curveTo(6.57f, 6.98f, 9.06f, 5f, 12f, 5f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF4285F4)),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(23.49f, 12.27f)
                    curveTo(23.49f, 11.48f, 23.41f, 10.73f, 23.3f, 10f)
                    horizontalLineTo(12f)
                    verticalLineToRelative(4.51f)
                    horizontalLineToRelative(6.47f)
                    curveTo(16.94f, 16.4f, 14.8f, 18.06f, 12f, 18.06f)
                    curveTo(9.06f, 18.06f, 6.57f, 16.08f, 5.58f, 13.36f)
                    lineTo(1.44f, 16.57f)
                    curveTo(3.42f, 20.42f, 7.39f, 23.06f, 12f, 23.06f)
                    curveTo(18.23f, 23.06f, 23.49f, 17.92f, 23.49f, 12.27f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFFFBBC05)),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(5.58f, 13.36f)
                    curveTo(5.33f, 12.56f, 5.19f, 11.71f, 5.19f, 10.83f)
                    curveTo(5.19f, 9.95f, 5.33f, 9.1f, 5.58f, 8.3f)
                    lineTo(1.44f, 5.09f)
                    curveTo(0.52f, 6.92f, 0f, 8.81f, 0f, 10.83f)
                    curveTo(0f, 12.85f, 0.52f, 14.74f, 1.44f, 16.57f)
                    lineTo(5.58f, 13.36f)
                    close()
                }
                path(
                    fill = SolidColor(Color(0xFF34A853)),
                    pathFillType = PathFillType.NonZero
                ) {
                    moveTo(12f, 23.06f)
                    curveTo(15.06f, 23.06f, 17.8f, 21.91f, 19.95f, 19.9f)
                    lineTo(15.93f, 16.78f)
                    curveTo(14.85f, 17.5f, 13.51f, 18.06f, 12f, 18.06f)
                    curveTo(9.06f, 18.06f, 6.57f, 16.08f, 5.58f, 13.36f)
                    lineTo(1.44f, 16.57f)
                    curveTo(3.42f, 20.42f, 7.39f, 23.06f, 12f, 23.06f)
                    close()
                }
            }.build()
            return _google!!
        }
    private var _google: ImageVector? = null

    val GitHub: ImageVector
        get() {
            if (_gitHub != null) return _gitHub!!
            _gitHub = ImageVector.Builder(
                name = "GitHub",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f
            ).apply {
                path(
                    fill = SolidColor(Color.White),
                    pathFillType = PathFillType.EvenOdd
                ) {
                    moveTo(12f, 2f)
                    curveTo(6.477f, 2f, 2f, 6.484f, 2f, 12.017f)
                    curveTo(2f, 16.446f, 4.843f, 20.198f, 8.805f, 21.523f)
                    curveTo(9.305f, 21.616f, 9.487f, 21.304f, 9.487f, 21.037f)
                    curveTo(9.487f, 20.799f, 9.479f, 19.992f, 9.475f, 19.141f)
                    curveTo(6.693f, 19.754f, 6.107f, 17.781f, 6.107f, 17.781f)
                    curveTo(5.652f, 16.604f, 4.996f, 16.291f, 4.996f, 16.291f)
                    curveTo(4.089f, 15.658f, 5.065f, 15.671f, 5.065f, 15.671f)
                    curveTo(6.068f, 15.743f, 6.596f, 16.721f, 6.596f, 16.721f)
                    curveTo(7.488f, 18.273f, 8.937f, 17.824f, 9.508f, 17.564f)
                    curveTo(9.599f, 16.906f, 9.858f, 16.458f, 10.144f, 16.204f)
                    curveTo(7.923f, 15.948f, 5.589f, 15.074f, 5.589f, 11.182f)
                    curveTo(5.589f, 10.074f, 5.978f, 9.167f, 6.619f, 8.455f)
                    curveTo(6.517f, 8.197f, 6.174f, 7.164f, 6.717f, 5.767f)
                    curveTo(6.717f, 5.767f, 7.556f, 5.495f, 9.467f, 6.811f)
                    curveTo(10.264f, 6.586f, 11.109f, 6.474f, 11.951f, 6.47f)
                    curveTo(12.793f, 6.474f, 13.638f, 6.586f, 14.436f, 6.811f)
                    curveTo(16.345f, 5.495f, 17.182f, 5.767f, 17.182f, 5.767f)
                    curveTo(17.727f, 7.164f, 17.384f, 8.197f, 17.283f, 8.455f)
                    curveTo(17.926f, 9.167f, 18.312f, 10.074f, 18.312f, 11.182f)
                    curveTo(18.312f, 15.086f, 15.972f, 15.944f, 13.743f, 16.195f)
                    curveTo(14.1f, 16.507f, 14.425f, 17.129f, 14.425f, 18.083f)
                    curveTo(14.425f, 19.453f, 14.412f, 20.557f, 14.412f, 20.892f)
                    curveTo(14.412f, 21.163f, 14.59f, 21.48f, 15.099f, 21.381f)
                    curveTo(19.057f, 20.052f, 21.897f, 16.304f, 21.897f, 11.874f)
                    curveTo(21.897f, 6.484f, 17.422f, 2f, 12f, 2f)
                    close()
                }
            }.build()
            return _gitHub!!
        }
    private var _gitHub: ImageVector? = null
}
