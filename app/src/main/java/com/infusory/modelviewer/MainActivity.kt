package com.infusory.modelviewer

import android.os.Bundle
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.infusory.modelviewer.filament.FilamentEngineHolder
import com.infusory.modelviewer.model.ModelInstance
import com.infusory.modelviewer.ui.ContainerCallbacks
import com.infusory.modelviewer.ui.ModelContainerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), ContainerCallbacks {

    private val availableModels = listOf(
        "Bulb.glb" to "Light Bulb",
        "Lungs.glb" to "Lungs",
        "Microscope.glb" to "Microscope",
        "Fiagena.glb" to "Fiagena",
        "solarsystem.glb" to "Solar System"
    )

    private lateinit var engineHolder: FilamentEngineHolder
    private lateinit var renderLoop: RenderLoop
    private lateinit var containerHost: FrameLayout
    private lateinit var emptyStateText: TextView

    private var cascadeStep = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val surfaceView = findViewById<SurfaceView>(R.id.surfaceView)
        containerHost = findViewById(R.id.containerHost)
        emptyStateText = findViewById(R.id.emptyStateText)
        val fab = findViewById<FloatingActionButton>(R.id.addModelFab)

        engineHolder = FilamentEngineHolder.get()
        engineHolder.init(this, surfaceView)
        renderLoop = RenderLoop(engineHolder, surfaceView, containerHost)

        fab.setOnClickListener { showModelPicker() }
    }

    override fun onResume() {
        super.onResume()
        renderLoop.start()
    }

    override fun onPause() {
        renderLoop.stop()
        super.onPause()
    }

    override fun onDestroy() {
        for (i in 0 until containerHost.childCount) {
            (containerHost.getChildAt(i) as? ModelContainerView)?.modelInstance?.dispose()
        }
        renderLoop.destroy()
        engineHolder.destroy()
        super.onDestroy()
    }

    private fun showModelPicker() {
        val names = availableModels.map { it.second }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.add_model)
            .setItems(names) { _, index -> addModel(availableModels[index].first) }
            .show()
    }

    private fun addModel(assetFileName: String) {
        val modelInstance = ModelInstance(engineHolder, "models/$assetFileName")
        val container = ModelContainerView(this, modelInstance, assetFileName)
        container.callbacks = this

        val hostW = containerHost.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val hostH = containerHost.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val size = (minOf(hostW, hostH) * 0.55f).toInt()
        container.setMaxSize(minOf(hostW, hostH))

        val lp = FrameLayout.LayoutParams(size, size)
        containerHost.addView(container, lp)

        val offset = (cascadeStep * 32 * resources.displayMetrics.density).toInt()
        cascadeStep = (cascadeStep + 1) % 8
        container.x = ((hostW - size) / 2f + offset).coerceIn(0f, (hostW - size).coerceAtLeast(0).toFloat())
        container.y = ((hostH - size) / 2f + offset).coerceIn(0f, (hostH - size).coerceAtLeast(0).toFloat())

        emptyStateText.visibility = TextView.GONE

        lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                engineHolder.loadModelBytes(this@MainActivity, "models/$assetFileName")
            }
            modelInstance.beginLoad(bytes)
        }
    }

    override fun onClose(view: ModelContainerView) {
        containerHost.removeView(view)
        view.modelInstance.dispose()
        if (containerHost.childCount == 0) {
            emptyStateText.visibility = TextView.VISIBLE
        }
    }

    override fun onBringToFront(view: ModelContainerView) {
        if (containerHost.getChildAt(containerHost.childCount - 1) === view) return
        containerHost.removeView(view)
        containerHost.addView(view)
    }
}
