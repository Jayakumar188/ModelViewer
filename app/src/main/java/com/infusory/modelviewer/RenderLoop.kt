package com.infusory.modelviewer

import android.view.Choreographer
import android.view.SurfaceView
import android.widget.FrameLayout
import com.google.android.filament.EntityManager
import com.google.android.filament.Skybox
import com.google.android.filament.Viewport
import com.infusory.modelviewer.filament.FilamentEngineHolder
import com.infusory.modelviewer.ui.ModelContainerView

class RenderLoop(
    private val engineHolder: FilamentEngineHolder,
    private val surfaceView: SurfaceView,
    private val containerHost: FrameLayout
) : Choreographer.FrameCallback {

    private var running = false
    private var lastFrameNanos = 0L
    private val minFrameIntervalNanos = 1_000_000_000L / TARGET_FPS

    private val backgroundSkybox = Skybox.Builder()
        .color(0.949f, 0.949f, 0.949f, 1f)
        .build(engineHolder.engine)
    private val backgroundScene = engineHolder.engine.createScene().apply {
        setSkybox(backgroundSkybox)
    }
    private val backgroundCamera =
        engineHolder.engine.createCamera(EntityManager.get().create())
    private val backgroundView = engineHolder.engine.createView().apply {
        setScene(backgroundScene)
        setCamera(backgroundCamera)
        setPostProcessingEnabled(false)
        setShadowingEnabled(false)
    }

    fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        Choreographer.getInstance().postFrameCallback(this)

        if (lastFrameNanos != 0L && frameTimeNanos - lastFrameNanos < minFrameIntervalNanos) {
            return
        }
        lastFrameNanos = frameTimeNanos

        val swapChain = engineHolder.swapChain ?: return
        val surfaceW = surfaceView.width
        val surfaceH = surfaceView.height
        if (surfaceW <= 0 || surfaceH <= 0) return

        if (!engineHolder.renderer.beginFrame(swapChain, frameTimeNanos)) {
            return
        }

        backgroundView.setViewport(Viewport(0, 0, surfaceW, surfaceH))
        engineHolder.renderer.render(backgroundView)

        var anyLabelsVisible = false
        for (i in 0 until containerHost.childCount) {
            val child = containerHost.getChildAt(i) as? ModelContainerView ?: continue
            if (child.width <= 0 || child.height <= 0) continue

            val ready = child.modelInstance.pollLoading()

            val left = child.x.toInt().coerceIn(0, surfaceW)
            val topFromTop = child.y.toInt()
            val vpWidth = child.width.coerceAtMost(surfaceW)
            val vpHeight = child.height.coerceAtMost(surfaceH)
            // Filament's Viewport origin is bottom-left; Android's is top-left.
            val bottom = (surfaceH - (topFromTop + vpHeight)).coerceIn(-vpHeight, surfaceH)
            val viewport = Viewport(left, bottom, vpWidth, vpHeight)

            if (ready) {
                child.modelInstance.updateViewportAndCamera(viewport)
                engineHolder.renderer.render(child.modelInstance.filamentView)
            }
            if (child.modelInstance.labelsVisible) anyLabelsVisible = true
            child.invalidate()
        }

        engineHolder.renderer.endFrame()
    }

    fun destroy() {
        stop()
        engineHolder.engine.destroyCameraComponent(backgroundCamera.entity)
        engineHolder.engine.destroyEntity(backgroundCamera.entity)
        engineHolder.engine.destroyView(backgroundView)
        backgroundScene.skybox?.let { engineHolder.engine.destroySkybox(it) }
        engineHolder.engine.destroyScene(backgroundScene)
    }

    companion object {
        private const val TARGET_FPS = 30
    }
}
