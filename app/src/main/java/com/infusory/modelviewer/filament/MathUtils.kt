package com.infusory.modelviewer.filament

object MathUtils {

    fun multiply(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(16)
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += a[k * 4 + row] * b[col * 4 + k]
                }
                r[col * 4 + row] = sum
            }
        }
        return r
    }

    fun lookAt(eye: FloatArray, target: FloatArray, up: FloatArray): FloatArray {
        val fx = target[0] - eye[0]; val fy = target[1] - eye[1]; val fz = target[2] - eye[2]
        val fLen = Math.sqrt((fx * fx + fy * fy + fz * fz).toDouble()).toFloat().coerceAtLeast(1e-6f)
        val zx = -fx / fLen; val zy = -fy / fLen; val zz = -fz / fLen

        val uxL = up[0]; val uyL = up[1]; val uzL = up[2]
        var xx = uyL * zz - uzL * zy
        var xy = uzL * zx - uxL * zz
        var xz = uxL * zy - uyL * zx
        val xLen = Math.sqrt((xx * xx + xy * xy + xz * xz).toDouble()).toFloat().coerceAtLeast(1e-6f)
        xx /= xLen; xy /= xLen; xz /= xLen

        val yx = zy * xz - zz * xy
        val yy = zz * xx - zx * xz
        val yz = zx * xy - zy * xx

        return floatArrayOf(
            xx, yx, zx, 0f,
            xy, yy, zy, 0f,
            xz, yz, zz, 0f,
            -(xx * eye[0] + xy * eye[1] + xz * eye[2]),
            -(yx * eye[0] + yy * eye[1] + yz * eye[2]),
            -(zx * eye[0] + zy * eye[1] + zz * eye[2]),
            1f
        )
    }

    fun perspective(fovYDegrees: Float, aspect: Float, near: Float, far: Float): FloatArray {
        val f = (1.0 / Math.tan(Math.toRadians(fovYDegrees / 2.0))).toFloat()
        val m = FloatArray(16)
        m[0] = f / aspect
        m[5] = f
        m[10] = (far + near) / (near - far)
        m[11] = -1f
        m[14] = (2 * far * near) / (near - far)
        return m
    }

    fun translationOf(m: FloatArray): FloatArray = floatArrayOf(m[12], m[13], m[14])

    fun worldToScreen(
        worldPos: FloatArray,
        viewProjection: FloatArray,
        viewportW: Float,
        viewportH: Float
    ): FloatArray? {
        val x = worldPos[0]; val y = worldPos[1]; val z = worldPos[2]
        val m = viewProjection
        val clipX = m[0] * x + m[4] * y + m[8] * z + m[12]
        val clipY = m[1] * x + m[5] * y + m[9] * z + m[13]
        val clipW = m[3] * x + m[7] * y + m[11] * z + m[15]
        if (clipW <= 0.0001f) return null
        val ndcX = clipX / clipW
        val ndcY = clipY / clipW
        val screenX = (ndcX * 0.5f + 0.5f) * viewportW
        val screenY = (1f - (ndcY * 0.5f + 0.5f)) * viewportH
        return floatArrayOf(screenX, screenY)
    }
}
