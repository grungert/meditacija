package com.grungert.nebo.grass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.filament.ColorGrading
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.ToneMapper
import io.github.sceneview.FrameRatePolicy
import io.github.sceneview.RenderQuality
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            if (intent.getBooleanExtra("minimal", false)) Minimal() else Meadow(intent.getIntExtra("blades", 36000), intent.getStringExtra("only"), intent.getBooleanExtra("vulkan", true))
        }
    }
}

private class Scene3d(val grass: Mesh, val ground: Mesh, val sky: Mesh,
                      val grassMi: MaterialInstance, val groundMi: MaterialInstance, val skyMi: MaterialInstance)

@Composable
fun Meadow(blades: Int = 36000, only: String? = null, vulkan: Boolean = false) {
    val engine = if (vulkan) rememberEngine(engineCreator = {
        com.google.android.filament.Engine.Builder().backend(com.google.android.filament.Engine.Backend.VULKAN).build()
    }) else rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val view = rememberView(engine)
    var wind by remember { mutableFloatStateOf(0.55f) }
    var scene by remember { mutableStateOf<Scene3d?>(null) }
    var tuned by remember { mutableStateOf(false) }
    val frames = remember { intArrayOf(0) }
    val start = remember { System.nanoTime() }

    val camera = rememberCameraNode(engine) {
        position = Position(0f, View3d.EYE_Y, View3d.EYE_Z)
        lookAt(Position(0f, View3d.LOOK_Y, View3d.LOOK_Z))
        // unlit materials are scaled by the physical camera's exposure (sunny 16 makes them nearly black)
        setExposure(1f)
    }

    LaunchedEffect(engine) {
        // compiling the three small materials takes a moment, so it happens off the main thread;
        // everything Filament itself touches is created back on the main thread
        val m = withContext(Dispatchers.Default) { compileMaterials(vulkan) }
        fun inst(buf: java.nio.ByteBuffer) = materialLoader.createInstance(materialLoader.createMaterial(buf))
        val s = 80f
        scene = Scene3d(
            grass = buildGrass(engine, count = blades),
            ground = buildQuad(engine, listOf(floatArrayOf(-s, 0f, 8f), floatArrayOf(s, 0f, 8f), floatArrayOf(s, 0f, -120f), floatArrayOf(-s, 0f, -120f))),
            sky = buildQuad(engine, listOf(floatArrayOf(-600f, -80f, -300f), floatArrayOf(600f, -80f, -300f), floatArrayOf(600f, 500f, -300f), floatArrayOf(-600f, 500f, -300f))),
            grassMi = inst(m.grass), groundMi = inst(m.ground), skyMi = inst(m.sky),
        )
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFB9CDD6))) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            materialLoader = materialLoader,
            view = view,
            surfaceType = io.github.sceneview.SurfaceType.TextureSurface,
            frameRatePolicy = FrameRatePolicy.Continuous(),
            renderQuality = RenderQuality.Default,
            autoCenterContent = false,
            cameraNode = camera,
            cameraManipulator = null,
            mainLightNode = null,
            fillLightNode = null,
            onFrame = { now ->
                if (!tuned) {
                    // painted colours go straight to the screen: no filmic curve, no bloom or ambient occlusion
                    view.colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.Linear()).build(engine)
                    view.bloomOptions = view.bloomOptions.apply { enabled = false }
                    view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply { enabled = false }
                    tuned = true
                }
                val t = (now - start) / 1e9f
                if (++frames[0] % 60 == 1) android.util.Log.i("NeboGrass", "frame ${frames[0]} t=$t scene=${scene != null}")
                scene?.let { sc ->
                    for (mi in listOf(sc.grassMi, sc.groundMi, sc.skyMi)) {
                        mi.setParameter("time", t)
                        mi.setParameter("wind", wind)
                    }
                }
            },
        ) {
            scene?.let { sc ->
                if (only == null || only == "sky") MeshNode(RenderableManager.PrimitiveType.TRIANGLES, sc.sky.vb, sc.sky.ib, sc.sky.box, sc.skyMi)
                if (only == null || only == "ground") MeshNode(RenderableManager.PrimitiveType.TRIANGLES, sc.ground.vb, sc.ground.ib, sc.ground.box, sc.groundMi)
                if (only == null || only == "grass") MeshNode(RenderableManager.PrimitiveType.TRIANGLES, sc.grass.vb, sc.grass.ib, sc.grass.box, sc.grassMi)
                if (only == "plain") MeshNode(RenderableManager.PrimitiveType.TRIANGLES, sc.ground.vb, sc.ground.ib, sc.ground.box, remember { materialLoader.createColorInstance(Color.Green) })
            }
        }

        if (scene == null) Text("Sadim travu…", color = Color.White, fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))

        Column(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp).fillMaxWidth()
                .clip(RoundedCornerShape(20.dp)).background(Color(0x99101C3A)).padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("Vjetar ${(wind * 100).toInt()}%", color = Color.White, fontSize = 14.sp)
            Slider(wind, { wind = it })
        }
    }
}

@Composable
fun Minimal() {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    SceneView(Modifier.fillMaxSize(), engine = engine, materialLoader = materialLoader,
        onFrame = { android.util.Log.i("NeboGrass", "minimal frame") }) {
        CubeNode(materialInstance = remember { materialLoader.createColorInstance(Color.Red) })
    }
}
