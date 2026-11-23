package io.github.kurtking514_byte.capricorn.ui

/**
 * The grid of points (a mesh) a flat Capri sprite is drawn on. Plain Kotlin, shared by the
 * home screen and the overlay.
 *
 * No body motion (2026-11-23, "copy the show", see CAPRI_REFERENCE.md): in the show she is a
 * held drawing with no sway, no breathing, rigid hair, no lean and no springy squash, so the
 * mesh is now a flat, still grid. The mesh is kept so motion can be added back later if needed.
 */
object CapriMesh {
    const val COLS = 8
    const val ROWS = 12

    fun newVerts() = FloatArray((COLS + 1) * (ROWS + 1) * 2)

    /**
     * Fills [verts] with a flat grid covering left..left+w, top..top+h.
     * The other parameters are unused since motion was removed; kept so callers don't change.
     */
    @Suppress("UNUSED_PARAMETER")
    fun fill(
        verts: FloatArray,
        left: Float, top: Float, w: Float, h: Float,
        t: Float, tiltX: Float, tiltY: Float, bounce: Float, life: Float = 1f,
    ) {
        var i = 0
        for (r in 0..ROWS) {
            val v = r / ROWS.toFloat()
            for (c in 0..COLS) {
                val u = c / COLS.toFloat()
                verts[i++] = left + u * w
                verts[i++] = top + v * h
            }
        }
    }
}
