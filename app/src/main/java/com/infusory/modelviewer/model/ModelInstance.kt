package com.infusory.modelviewer.model

import com.google.android.filament.Camera
import com.google.android.filament.EntityManager
import com.google.android.filament.Scene
import com.google.android.filament.Viewport
import com.google.android.filament.View as FilamentView
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.utils.Manipulator
import com.infusory.modelviewer.filament.FilamentEngineHolder
import com.infusory.modelviewer.filament.GlbLabelParser
import com.infusory.modelviewer.filament.LabelInfo
import com.infusory.modelviewer.filament.MathUtils
import java.nio.ByteBuffer

enum class InteractionMode { NORMAL, INTERACTION }

data class ResolvedLabel(val entity: Int, val text: String)

class ModelInstance(
    val engineHolder: FilamentEngineHolder,
    val assetPath: String
) {
    val engine = engineHolder.engine

    val scene: Scene = engine.createScene()
    val camera: Camera = engine.createCamera(EntityManager.get().create())
    val filamentView: FilamentView = engine.createView().apply {
        setScene(this@ModelInstance.scene)
        setCamera(this@ModelInstance.camera)
        setShadowingEnabled(false)
        setAntiAliasing(FilamentView.AntiAliasing.NONE)
        setPostProcessingEnabled(false)
        renderQuality = renderQuality.apply {
            hdrColorBuffer = FilamentView.QualityLevel.LOW
        }
        dynamicResolutionOptions = dynamicResolutionOptions.apply {
            enabled = true
            quality = FilamentView.QualityLevel.LOW
        }
    }

    var asset: FilamentAsset? = null
        private set
    private var glbBuffer: ByteBuffer? = null

    var mode: InteractionMode = InteractionMode.NORMAL
    var labelsVisible: Boolean = false

    private var rawLabels: List<LabelInfo> = emptyList()
    var resolvedLabels: List<ResolvedLabel> = emptyList()
        private set

    var manipulator: Manipulator? = null
        private set

    private var loading = false
    var isReady = false
        private set

    init {
        scene.indirectLight = engineHolder.sharedIndirectLight
        scene.addEntity(engineHolder.sharedSunEntity)
    }

    fun beginLoad(rawGlb: ByteBuffer) {
        glbBuffer = rawGlb
        rawLabels = GlbLabelParser.parseLabels(rawGlb)

        val newAsset = engineHolder.assetLoader.createAsset(rawGlb) ?: return
        asset = newAsset
        scene.addEntities(newAsset.entities)
        engineHolder.resourceLoader.asyncBeginLoad(newAsset)
        loading = true
    }

    fun pollLoading(): Boolean {
        if (isReady) return true
        if (!loading) return false
        engineHolder.resourceLoader.asyncUpdateLoad()
        val progress = engineHolder.resourceLoader.asyncGetLoadProgress()
        if (progress >= 1f) {
            loading = false
            isReady = true
            onFinishedLoading()
        }
        return isReady
    }

    private fun onFinishedLoading() {
        val a = asset ?: return
        a.releaseSourceData()

        resolvedLabels = rawLabels.mapNotNull { info ->
            val entity = a.getFirstEntityByName(info.nodeName)
            if (entity != 0) ResolvedLabel(entity, info.text) else null
        }

        frameCameraOnAsset()
    }

    private fun frameCameraOnAsset() {
        val a = asset ?: return
        val box = a.boundingBox
        val center = box.center
        val halfExtent = box.halfExtent
        val radius = maxOf(halfExtent[0], halfExtent[1], halfExtent[2]).coerceAtLeast(0.01f)

        manipulator = Manipulator.Builder()
            .targetPosition(center[0], center[1], center[2])
            .orbitHomePosition(center[0], center[1], center[2] + radius * 3.2f)
            .upVector(0f, 1f, 0f)
            .zoomSpeed(0.02f)
            .build(Manipulator.Mode.ORBIT)

        camera.setExposure(16f, 1f / 125f, 100f)
        val near = (radius * 0.05f).coerceAtLeast(0.01f)
        val far = radius * 20f
        camera.setProjection(45.0, 1.0, near.toDouble(), far.toDouble(), Camera.Fov.VERTICAL)
    }

    private val lastEye = FloatArray(3)
    private val lastTarget = FloatArray(3)
    private val lastUp = FloatArray(3)
    private var lastFovY = 45f
    private var lastAspect = 1f
    private var lastNear = 0.05f
    private var lastFar = 1000f

    fun updateViewportAndCamera(viewport: Viewport) {
        filamentView.setViewport(viewport)
        val m = manipulator ?: return
        if (viewport.width <= 0 || viewport.height <= 0) return
        m.setViewport(viewport.width, viewport.height)

        lastAspect = viewport.width.toFloat() / viewport.height.toFloat()
        lastFovY = 45f
        lastNear = 0.05f
        lastFar = 1000f
        camera.setProjection(
            lastFovY.toDouble(),
            lastAspect.toDouble(),
            lastNear.toDouble(),
            lastFar.toDouble(),
            Camera.Fov.VERTICAL
        )

        m.getLookAt(lastEye, lastTarget, lastUp)
        camera.lookAt(
            lastEye[0].toDouble(), lastEye[1].toDouble(), lastEye[2].toDouble(),
            lastTarget[0].toDouble(), lastTarget[1].toDouble(), lastTarget[2].toDouble(),
            lastUp[0].toDouble(), lastUp[1].toDouble(), lastUp[2].toDouble()
        )
    }

    fun projectLabels(viewportW: Int, viewportH: Int): List<Triple<Float, Float, String>> {
        if (!labelsVisible || resolvedLabels.isEmpty()) return emptyList()
        val tcm = engine.transformManager
        val viewMatrix = MathUtils.lookAt(lastEye, lastTarget, lastUp)
        val projMatrix = MathUtils.perspective(lastFovY, lastAspect, lastNear, lastFar)
        val viewProj = MathUtils.multiply(projMatrix, viewMatrix)

        val out = mutableListOf<Triple<Float, Float, String>>()
        for (label in resolvedLabels) {
            val instance = tcm.getInstance(label.entity)
            if (instance == 0) continue
            val world = FloatArray(16)
            tcm.getWorldTransform(instance, world)
            val worldPos = MathUtils.translationOf(world)
            val screen = MathUtils.worldToScreen(worldPos, viewProj, viewportW.toFloat(), viewportH.toFloat())
                ?: continue
            out.add(Triple(screen[0], screen[1], label.text))
        }
        return out
    }

    fun dispose() {
        asset?.let {
            scene.removeEntities(it.entities)
            engineHolder.assetLoader.destroyAsset(it)
        }
        scene.removeEntity(engineHolder.sharedSunEntity)
        engine.destroyCameraComponent(camera.entity)
        engine.destroyEntity(camera.entity)
        engine.destroyView(filamentView)
        engine.destroyScene(scene)
        glbBuffer = null
    }
}
