# 3D Model Viewer — README

## 3D library: Filament + gltfio (used directly, not through a wrapper)

[Google Filament](https://github.com/google/filament) was chosen over the higher-level
`SceneView` wrapper for one reason: this task needs **one Engine/Renderer/SwapChain
shared across every on-screen model**, not five independent 3D views. Talking to
Filament and its `gltfio` loader directly gives full control over:

- a single `UbershaderProvider` (all models share pre-compiled material shaders —
  no per-model shader-compile hitch the first time a new model appears),
- a single `AssetLoader` / `ResourceLoader`,
- and — the biggest one — a **single shared `SurfaceView`/`SwapChain`**, with each
  draggable container rendering into its own `Viewport` rectangle inside that one
  surface, instead of one `SurfaceView` per container.

That last point matters because containers can be dragged on top of each other, and
Android does not guarantee correct Z-order compositing between multiple independently
overlapping `SurfaceView`s. Rendering everything into one shared surface and letting
draw order (2D child order) decide what's on top sidesteps that class of bug entirely.
Full reasoning is in the doc comments on `FilamentEngineHolder` and `RenderLoop`.

## Performance optimisations applied

- **One Engine, one Renderer, one SwapChain, one asset/material pipeline** for the
  whole app (see above) — avoids 4x duplicated GPU setup with 5 models loaded.
- **Shared lighting.** One ambient `IndirectLight` + one directional "sun" `Light`,
  reused (added to) every container's `Scene`, instead of rebuilt per container.
  No HDR/IBL cubemap is loaded — a real GPU + APK-size cost this task doesn't need.
- **Shadows, AA, and post-processing are off** on every container's Filament `View`
  (`isShadowingEnabled = false`, `AntiAliasing.NONE`, `isPostProcessingEnabled = false`,
  dynamic resolution at `QualityLevel.LOW`). Five small on-screen viewports don't need
  FXAA/bloom/DoF/vignette, and skipping them is a direct GPU-time saving.
- **Async, incremental model loading** via gltfio's `asyncBeginLoad` /
  `asyncUpdateLoad`, polled once per frame, instead of the blocking
  `loadResources()` call. Adding a 2–6 MB `.glb` mid-session decodes its textures
  over several frames instead of stalling one frame for the whole thing.
- **Raw `.glb` bytes are cached per asset path** after first read, so re-adding the
  same model doesn't re-touch `assets/` each time.
- **Render loop throttled to ~30 fps** via a timestamp gate in the `Choreographer`
  callback, matching the task's own target rather than burning GPU time at a
  120 Hz display refresh rate for no visible benefit.
- **Clean resource teardown on Close** (spec 1.6): the `FilamentAsset`, its entities,
  the container's `Scene`/`Camera`/`View` are all explicitly destroyed, so closing
  and re-adding models repeatedly doesn't leak GPU memory over a long session.

## Tradeoffs made

- **Same-model instancing is not implemented.** If the user adds the *same* `.glb`
  several times, each instance currently decodes and uploads its own full copy of
  the geometry/textures rather than sharing GPU buffers via gltfio's asset-instancing
  API (`AssetLoader.createInstancedAsset`). That API expects the instance count
  up front, which doesn't fit "the user adds copies one at a time, unpredictably" —
  implementing it properly needs a small pooling layer. Noted below.
- **Lighting is deliberately flat** (ambient + one directional light, no IBL) rather
  than a realistic environment map, trading a bit of visual polish for meaningfully
  less GPU work × 5 concurrent scenes.
- **One label per glTF node, connector line only** — no collision/decluttering
  between nearby labels. On a dense model this can produce overlapping label chips.

## What I'd improve with more time

- Implement real GPU-buffer instancing for repeated copies of the same model.
- Label layout that nudges overlapping chips apart instead of letting them collide.
- A lightweight LOD swap (or texture downscale) for `Microscope.glb`, which has by
  far the most primitives (123) of the five sample models and is the most likely of
  the five to be the actual frame-time bottleneck at 5-models-on-screen.
- Persisting container layout (position/size/mode) across rotation/process death.

## Known bugs / limitations

- Only tested against Filament `1.51.0`; if a different Filament/gltfio version is
  substituted, double-check `gltfio`'s `Manipulator`/`asyncBeginLoad` API shape still
  matches, since that surface has shifted slightly across releases.
- No handling yet for extremely small container sizes beyond a fixed 100dp floor —
  below that, the button bar can overlap the model viewport.

## Device(s) tested

> _Fill this in after running the signed APK on your own hardware — the task
> specifically asks for this (spec section 4/5), and the video walkthrough should
> show it running on a real low-end device, not just an emulator._

- [ ] Device model, Android version, RAM:
- [ ] Observed fps with 5 models loaded:
