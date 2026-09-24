# 3D Model Viewer

## 3D Library

I used **Google Filament + gltfio** directly instead of SceneView.

The main reason is that I wanted to use **one Engine, Renderer and SwapChain** for all models. All models are rendered inside one shared `SurfaceView`, and each model container uses its own viewport.

This also avoids Z-order issues that can happen when multiple `SurfaceView`s overlap.

## Performance Optimizations

* One shared Filament `Engine`, `Renderer` and `SwapChain`.
* Shared `UbershaderProvider`, `AssetLoader` and `ResourceLoader`.
* Shared ambient light and directional light for all models.
* Shadows, anti-aliasing and post-processing are disabled.
* Dynamic resolution is set to `LOW`.
* Models are loaded asynchronously using gltfio.
* `.glb` file bytes are cached after the first load.
* Rendering is limited to around **30 FPS**.
* Filament resources are properly released when a model is closed.

## Tradeoffs

* Multiple copies of the same `.glb` currently load their own geometry and textures. GPU instancing is not implemented.
* Lighting is kept simple using ambient + directional light instead of an HDR environment map.
* Labels are supported for glTF nodes, but there is currently no automatic collision handling between labels.

## Future Improvements

* Add GPU instancing for repeated models.
* Improve label positioning and collision handling.
* Add LOD or texture optimization for larger models.
* Save model position, size and layout across configuration changes.

## Known Limitations

* Tested with **Filament 1.51.0**.
* Very small containers may cause the button bar and model area to overlap.
