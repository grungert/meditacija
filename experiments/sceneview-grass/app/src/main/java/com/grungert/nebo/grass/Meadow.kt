package com.grungert.nebo.grass

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.VertexBuffer
import com.google.android.filament.VertexBuffer.AttributeType
import com.google.android.filament.VertexBuffer.VertexAttribute
import com.google.android.filament.filamat.MaterialBuilder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random

/** Where the camera stands and looks: knee height at the edge of the meadow. */
object View3d {
    const val EYE_Y = 1.0f
    const val EYE_Z = 3.0f
    const val LOOK_Y = 0.5f
    const val LOOK_Z = -12f
}

// ---------------------------------------------------------------- materials (compiled on the device)

private const val NOISE = """
float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vnoise(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1.0, 0.0)), f.x), mix(h21(i + vec2(0.0, 1.0)), h21(i + vec2(1.0, 1.0)), f.x), f.y);
}
float fbm(vec2 p) { float s = 0.0; float a = 0.5; for (int i = 0; i < 5; i++) { s += a * vnoise(p); p = p * 2.03 + vec2(1.7, 9.2); a *= 0.5; } return s; }
const vec3 HAZE = vec3(0.70, 0.79, 0.80);
"""

/**
 * One blade per eight-to-ten vertices, built straight up; the vertex shader bends it.
 * custom0 = (root x, root z, height, t along the blade), custom1 = (lean, random, unused, flex).
 * Gusts travel across the field as a wave; where a gust bends the blades, their tips catch the light (silver).
 */
private const val GRASS_VERTEX = """
void materialVertex(inout MaterialVertexInputs material) {
    vec4 c0 = getCustom0();
    vec4 c1 = getCustom1();
    float t = c0.w;
    float h = c0.z;
    vec2 root = c0.xy;
    float time = materialParams.time;
    float wind = materialParams.wind;
    float g = 0.5 + 0.5 * sin(dot(root, vec2(0.55, -0.25)) - time * 1.6 + 1.2 * sin(root.x * 0.21 + root.y * 0.17 + time * 0.5));
    g = g * g;
    float flutter = 0.07 * sin(time * 2.6 + c1.y * 40.0) + 0.04 * sin(time * 6.3 + root.x * 3.0) * t;
    float bend = c1.x + (wind * (0.25 + 1.0 * g) + flutter) * c1.w;
    vec2 dir = normalize(vec2(1.0, -0.35));
    material.worldPosition.xz += dir * h * sin(bend) * t * t;
    material.worldPosition.y += h * t * (cos(bend * t) - 1.0);
    material.grass = vec4(t, g * wind, c1.y, 0.0);
}
"""

private const val GRASS_FRAGMENT = NOISE + """
void material(inout MaterialInputs material) {
    prepareMaterial(material);
    float t = variable_grass.x;
    float g = variable_grass.y;
    float r = variable_grass.z;
    vec3 base = mix(vec3(0.12, 0.26, 0.17), vec3(0.26, 0.44, 0.26), r);
    vec3 tip = mix(vec3(0.55, 0.70, 0.38), vec3(0.80, 0.78, 0.46), r * r);
    vec3 c = mix(base, tip, smoothstep(0.05, 1.0, t));
    c = mix(c, vec3(0.88, 0.90, 0.76), clamp(g * 1.4, 0.0, 1.0) * smoothstep(0.35, 1.0, t) * 0.55);
    c *= 0.7 + 0.3 * smoothstep(0.0, 0.45, t);
    float dist = length(getWorldPosition() - getWorldCameraPosition());
    c = mix(c, HAZE, 1.0 - exp(-dist * 0.045));
    material.baseColor = vec4(pow(c, vec3(2.2)), 1.0);
}
"""

private const val GROUND_FRAGMENT = NOISE + """
void material(inout MaterialInputs material) {
    prepareMaterial(material);
    vec3 wp = getUserWorldPosition();
    float n = fbm(wp.xz * 0.35);
    vec3 c = mix(vec3(0.09, 0.19, 0.12), vec3(0.20, 0.33, 0.19), n) * (0.9 + 0.2 * vnoise(wp.xz * 3.0));
    float dist = length(getWorldPosition() - getWorldCameraPosition());
    c = mix(c, HAZE, 1.0 - exp(-dist * 0.045));
    material.baseColor = vec4(pow(c, vec3(2.2)), 1.0);
}
"""

/** A painted sky on a far wall: gradient, two-tone cumulus on a plane above, hazy ridges along the horizon. */
private const val SKY_FRAGMENT = NOISE + """
void material(inout MaterialInputs material) {
    prepareMaterial(material);
    vec3 dir = normalize(getWorldPosition() - getWorldCameraPosition());
    float y = dir.y;
    vec3 hor = vec3(0.86, 0.90, 0.90);
    vec3 c = mix(hor, vec3(0.62, 0.77, 0.92), smoothstep(0.0, 0.18, y));
    c = mix(c, vec3(0.32, 0.53, 0.84), smoothstep(0.15, 0.6, y));
    if (y > 0.02) {
        vec2 cp = dir.xz / (y + 0.08) * 0.9 + vec2(materialParams.time * 0.012, 0.0);
        float cl = fbm(cp);
        float sh = fbm(cp + vec2(0.06, -0.09));
        float m = smoothstep(0.52, 0.58, cl) * smoothstep(0.02, 0.12, y);
        float l = clamp((cl - sh) * 5.0 + 0.55, 0.0, 1.0);
        vec3 cc = mix(vec3(0.74, 0.80, 0.91), vec3(1.0, 0.985, 0.95), smoothstep(0.38, 0.46, l));
        cc = mix(mix(cc, hor, 0.6), cc, smoothstep(0.02, 0.3, y));
        c = mix(c, cc, m);
    }
    float ang = atan(dir.x, -dir.z);
    float r1 = 0.035 + 0.03 * fbm(vec2(ang * 3.0, 1.0));
    float r2 = 0.012 + 0.02 * fbm(vec2(ang * 5.0, 7.0));
    c = mix(c, mix(hor, vec3(0.50, 0.62, 0.64), 0.45), smoothstep(r1 + 0.002, r1 - 0.002, y));
    c = mix(c, mix(hor, vec3(0.38, 0.53, 0.45), 0.55), smoothstep(r2 + 0.002, r2 - 0.002, y));
    material.baseColor = vec4(pow(c, vec3(2.2)), 1.0);
}
"""

private fun compile(name: String, fragment: String, vertex: String? = null, grass: Boolean = false, api: MaterialBuilder.TargetApi): ByteBuffer {
    val b = MaterialBuilder()
        .name(name)
        .shading(MaterialBuilder.Shading.UNLIT)
        .doubleSided(true)
        .uniformParameter(MaterialBuilder.UniformType.FLOAT, "time")
        .uniformParameter(MaterialBuilder.UniformType.FLOAT, "wind")
        .platform(MaterialBuilder.Platform.MOBILE)
        .targetApi(api)
        .optimization(MaterialBuilder.Optimization.PERFORMANCE)
        .material(fragment)
    if (grass) {
        b.require(MaterialBuilder.VertexAttribute.CUSTOM0)
            .require(MaterialBuilder.VertexAttribute.CUSTOM1)
            .variable(MaterialBuilder.Variable.CUSTOM0, "grass")
    }
    if (vertex != null) b.materialVertex(vertex)
    val pkg = b.build()
    check(pkg.isValid) { "material $name failed to compile" }
    return pkg.buffer
}

class Materials(val grass: ByteBuffer, val ground: ByteBuffer, val sky: ByteBuffer)

fun compileMaterials(vulkan: Boolean): Materials {
    val api = if (vulkan) MaterialBuilder.TargetApi.VULKAN else MaterialBuilder.TargetApi.OPENGL
    MaterialBuilder.init()
    try {
        return Materials(
            compile("grass", GRASS_FRAGMENT, GRASS_VERTEX, grass = true, api = api),
            compile("ground", GROUND_FRAGMENT, api = api),
            compile("sky", SKY_FRAGMENT, api = api),
        )
    } finally {
        MaterialBuilder.shutdown()
    }
}

// ---------------------------------------------------------------- meshes

class Mesh(val vb: VertexBuffer, val ib: IndexBuffer, val box: Box)

private fun floats(n: Int): ByteBuffer = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder())

/**
 * Blades scattered over the part of the meadow the camera sees, with a log distribution in distance,
 * so they are about equally dense on screen near and far.
 */
fun buildGrass(engine: Engine, count: Int = 36000, segments: Int = 4, aspect: Float = 0.45f): Mesh {
    val rnd = Random(7)
    val vPerBlade = (segments + 1) * 2
    val vCount = count * vPerBlade
    val pos = floats(vCount * 3)
    val c0 = floats(vCount * 4)
    val c1 = floats(vCount * 4)
    val idx = ByteBuffer.allocateDirect(count * segments * 6 * 4).order(ByteOrder.nativeOrder())
    val halfFov = tan(Math.toRadians(22.5).toFloat()) * aspect * 1.25f
    val dMin = 0.5f
    val dMax = 70f
    for (i in 0 until count) {
        val d = dMin * (dMax / dMin).pow(rnd.nextFloat())
        val z = View3d.EYE_Z - d
        val x = (rnd.nextFloat() * 2 - 1) * (d * halfFov + 0.6f)
        val h = 0.30f + 0.50f * rnd.nextFloat()
        val w = (0.010f + 0.012f * rnd.nextFloat()) * (1 + d * 0.03f)
        val face = (rnd.nextFloat() - 0.5f) * 1.2f
        val ax = cos(face)
        val az = sin(face)
        val lean = -0.15f + 0.4f * rnd.nextFloat()
        val r = rnd.nextFloat()
        val flex = 0.6f + 0.8f * rnd.nextFloat()
        val base = i * vPerBlade
        for (k in 0..segments) {
            val t = k / segments.toFloat()
            val half = w * 0.5f * (1 - t).pow(0.8f)
            for (side in 0..1) {
                val s = if (side == 0) -half else half
                pos.putFloat(x + ax * s); pos.putFloat(h * t); pos.putFloat(z + az * s)
                c0.putFloat(x); c0.putFloat(z); c0.putFloat(h); c0.putFloat(t)
                c1.putFloat(lean); c1.putFloat(r); c1.putFloat(0f); c1.putFloat(flex)
            }
        }
        for (k in 0 until segments) {
            val a = base + k * 2
            idx.putInt(a); idx.putInt(a + 1); idx.putInt(a + 2)
            idx.putInt(a + 1); idx.putInt(a + 3); idx.putInt(a + 2)
        }
    }
    pos.flip(); c0.flip(); c1.flip(); idx.flip()
    val vb = VertexBuffer.Builder()
        .bufferCount(3)
        .vertexCount(vCount)
        .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0, 12)
        .attribute(VertexAttribute.CUSTOM0, 1, AttributeType.FLOAT4, 0, 16)
        .attribute(VertexAttribute.CUSTOM1, 2, AttributeType.FLOAT4, 0, 16)
        .build(engine)
    vb.setBufferAt(engine, 0, pos)
    vb.setBufferAt(engine, 1, c0)
    vb.setBufferAt(engine, 2, c1)
    val ib = IndexBuffer.Builder().indexCount(count * segments * 6).bufferType(IndexBuffer.Builder.IndexType.UINT).build(engine)
    ib.setBuffer(engine, idx)
    return Mesh(vb, ib, Box(0f, 0.5f, View3d.EYE_Z - dMax / 2, 30f, 1.5f, dMax / 2 + 2))
}

/** A flat quad, given by its four corners (counter-clockwise seen from the camera). */
fun buildQuad(engine: Engine, corners: List<FloatArray>): Mesh {
    val pos = floats(12)
    corners.forEach { pos.putFloat(it[0]); pos.putFloat(it[1]); pos.putFloat(it[2]) }
    pos.flip()
    val idx = ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder())
    shortArrayOf(0, 1, 2, 0, 2, 3).forEach { idx.putShort(it) }
    idx.flip()
    val vb = VertexBuffer.Builder().bufferCount(1).vertexCount(4)
        .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0, 12).build(engine)
    vb.setBufferAt(engine, 0, pos)
    val ib = IndexBuffer.Builder().indexCount(6).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine)
    ib.setBuffer(engine, idx)
    val xs = corners.map { it[0] }; val ys = corners.map { it[1] }; val zs = corners.map { it[2] }
    val box = Box((xs.min() + xs.max()) / 2, (ys.min() + ys.max()) / 2, (zs.min() + zs.max()) / 2,
        (xs.max() - xs.min()) / 2 + 0.01f, (ys.max() - ys.min()) / 2 + 0.01f, (zs.max() - zs.min()) / 2 + 0.01f)
    return Mesh(vb, ib, box)
}
