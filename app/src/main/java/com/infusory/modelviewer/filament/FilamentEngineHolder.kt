package com.infusory.modelviewer.filament

import android.content.Context
import android.view.SurfaceView
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.View as FilamentView
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import com.google.android.filament.Filament
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.utils.Utils

class FilamentEngineHolder private constructor() {

    lateinit var engine: Engine
        private set
    lateinit var renderer: Renderer
        private set
    lateinit var materialProvider: UbershaderProvider
        private set
    lateinit var assetLoader: AssetLoader
        private set
    lateinit var resourceLoader: ResourceLoader
        private set
    lateinit var uiHelper: UiHelper
        private set

    var swapChain: SwapChain? = null
        private set

    var sharedIndirectLight: IndirectLight? = null
        private set
    var sharedSunEntity: Int = 0
        private set

    private val modelBufferCache = HashMap<String, ByteBuffer>()

    fun init(context: Context, surfaceView: SurfaceView) {
        if (::engine.isInitialized) return // already initialized (e.g. rotation)

        Filament.init()
        Gltfio.init()
        Utils.init()

        engine = Engine.create()
        renderer = engine.createRenderer()
        materialProvider = UbershaderProvider(engine)
        assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
        resourceLoader = ResourceLoader(engine, /* normalizeSkinningWeights = */ true)

        setupSharedLighting()

        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: android.view.Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface)
            }

            override fun onDetachedFromSurface() {
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                }
                swapChain = null
            }

            override fun onResized(width: Int, height: Int) {
            }
        }
        uiHelper.attachTo(surfaceView)
    }

    private fun setupSharedLighting() {
        sharedIndirectLight = IndirectLight.Builder()
            .intensity(15_000f)
            .build(engine)

        sharedSunEntity = EntityManager.get().create()
        com.google.android.filament.LightManager.Builder(com.google.android.filament.LightManager.Type.DIRECTIONAL)
            .color(1.0f, 1.0f, 1.0f)
            .intensity(80_000f)
            .direction(-0.5f, -1f, -0.3f)
            .castShadows(false)
            .build(engine, sharedSunEntity)
    }

    fun loadModelBytes(context: Context, assetPath: String): ByteBuffer {
        modelBufferCache[assetPath]?.let { return it.duplicate() }
        val bytes = context.assets.open(assetPath).use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).apply {
            put(bytes)
            rewind()
        }
        modelBufferCache[assetPath] = buffer
        return buffer.duplicate()
    }

    fun destroy() {
        if (!::engine.isInitialized) return
        uiHelper.detach()
        sharedIndirectLight?.let { engine.destroyIndirectLight(it) }
        if (sharedSunEntity != 0) engine.destroyEntity(sharedSunEntity)
        resourceLoader.destroy()
        assetLoader.destroy()
        materialProvider.destroyMaterials()
        swapChain?.let { engine.destroySwapChain(it) }
        engine.destroyRenderer(renderer)
        engine.destroy()
        modelBufferCache.clear()
    }

    companion object {
        @Volatile private var instance: FilamentEngineHolder? = null
        fun get(): FilamentEngineHolder =
            instance ?: synchronized(this) {
                instance ?: FilamentEngineHolder().also { instance = it }
            }
    }
}
