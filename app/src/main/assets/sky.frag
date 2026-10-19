precision highp float;
uniform vec2 uRes;
uniform float uTime;
uniform float uDrift;     // integrated wind (slows during holds)
uniform float uMorph;     // integrated shape evolution
uniform float uWind;      // integrated gust phase, shared with the grass blades
uniform float uWalk;      // distance walked; the hill crest scrolls at 0.55x, the near ground at 1x
uniform float uBreath;    // 0 = empty lungs, 1 = full
uniform float uMotion;    // 1 normal, ~0.3 reduced motion
uniform vec3 uSkyTop, uSkyMid, uSkyHor;
uniform vec3 uSh, uMd, uLt, uRim;
uniform vec3 uSunCol, uGrassTop, uGrassBot;
uniform vec2 uSunPos, uMoonPos;   // in uv space
uniform float uSunAmt, uMoonAmt, uStars;
uniform float uCover;             // extra cloud cover for rain, snow and fog
uniform float uDim;               // 0 normal, 1 fully dark (eyes closed)
uniform vec3 uLight;              // normalised direction towards the sun or moon
uniform float uDrop;              // 0..1: the breathing drop of water is shown
uniform float uLake;              // 0..1: the lower part of the picture becomes a still lake

float hash(vec2 p) {
  vec3 p3 = fract(vec3(p.xyx) * 0.1031);
  p3 += dot(p3, p3.yzx + 33.33);
  return fract((p3.x + p3.y) * p3.z);
}

float noise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);
  float a = hash(i);
  float b = hash(i + vec2(1.0, 0.0));
  float c = hash(i + vec2(0.0, 1.0));
  float d = hash(i + vec2(1.0, 1.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
  float v = 0.0;
  float a = 0.5;
  mat2 m = mat2(1.6, 1.2, -1.2, 1.6);
  for (int i = 0; i < 5; i++) {
    v += a * noise(p);
    p = m * p + vec2(3.1, 1.7);
    a *= 0.5;
  }
  return v;
}

float smax(float a, float b, float k) {
  float h = clamp(0.5 + 0.5 * (a - b) / k, 0.0, 1.0);
  return mix(b, a, h) + k * h * (1.0 - h);
}

// Union of round puffs on a jittered grid: the hand-drawn "cauliflower" lobes.
// x: field (> 0 inside a puff, cell units). y: sphere shading towards the light (-1..1).
vec2 puffs(vec2 q, float seed) {
  vec2 i = floor(q);
  vec2 f = fract(q);
  float field = -1.0;
  float wSum = 0.0;
  float sSum = 0.0;
  for (int y = -1; y <= 1; y++) {
    for (int x = -1; x <= 1; x++) {
      vec2 g = vec2(float(x), float(y));
      vec2 id = i + g;
      vec2 o = vec2(hash(id + seed), hash(id + seed + 17.0));
      float r = 0.42 + 0.46 * hash(id + seed + 31.0);
      vec2 off = f - g - o;
      float dd = r - length(off);
      field = smax(field, dd, 0.12);
      // puffs that bulge further towards the viewer dominate the shading; soft weights avoid shard-like seams
      float bulge = dd / r;
      vec2 n2 = off / r;
      vec3 n = vec3(n2, sqrt(max(0.0, 1.0 - dot(n2, n2))));
      float w = exp(14.0 * bulge);
      wSum += w;
      sSum += w * dot(n, uLight);
    }
  }
  return vec2(field, sSum / max(wSum, 1e-4));
}

// Signed density of one cumulus layer: > 0 inside the cloud.
// An envelope gives towers with a flat base; puffs give the rounded lobes and their light;
// smaller puffs add lobes on the lobes. Puffs drift upward slowly (convection).
float cloud(vec2 p, float base, float height, float scale, float seed, float cover, out float mass, out float shade) {
  float x = p.x * scale + seed * 13.7;
  mass = noise(vec2(x * 0.9, seed)) * 0.65 + noise(vec2(x * 2.1, seed + 5.3)) * 0.35;
  mass = smoothstep(0.40 - cover, 0.78 - cover * 0.5, mass);
  float top = base + height * mass;
  float env = (top - p.y) / height;
  vec2 q = vec2(p.x * scale * 3.2 + uMorph * 0.25, (p.y - base) * scale * 3.2 - uMorph * 0.4) + seed * 11.0;
  vec2 big = puffs(q, seed);
  vec2 small = puffs(q * 2.3 + 5.0, seed + 3.0);
  float d = env * 1.2 + big.x * 0.9 - 0.25 + small.x * 0.30 + (fbm(q * 4.0) - 0.5) * 0.08;
  shade = mix(big.y, small.y, 0.22 * smoothstep(-0.05, 0.1, small.x));
  float wob = (noise(vec2(p.x * scale * 5.0 + seed, seed)) - 0.5) * 0.022
            + (noise(vec2(p.x * scale * 19.0 + seed, seed + 2.0)) - 0.5) * 0.008;
  float bottom = (p.y - base + wob) / height * 9.0;
  return min(d, bottom);
}

// Painterly three-tone shading with a warm rim on the lit side.
vec4 layer(vec2 p, float base, float height, float scale, float seed, float cover, float edge, float haze, vec3 skyC) {
  if (p.y < base - 0.03 || p.y > base + height * 1.6 + 0.08) return vec4(0.0);
  float mass;
  float shade;
  float d = cloud(p, base, height, scale, seed, cover, mass, shade);
  if (d < -0.02) return vec4(0.0);

  float vf = clamp((p.y - base) / (height * max(mass, 0.25)), 0.0, 1.0);
  float l = clamp(0.02 + 0.30 * vf + 0.62 * (shade * 0.5 + 0.5) + (uBreath - 0.4) * 0.18, 0.0, 1.0);

  vec3 col = mix(uSh, uMd, smoothstep(0.40, 0.43, l));
  col = mix(col, uLt, smoothstep(0.64, 0.67, l));
  col *= 0.95 + 0.07 * l;

  float rimMask = (1.0 - smoothstep(0.0, edge * 4.0, d)) * smoothstep(0.1, 0.5, shade);
  col = mix(col, uRim, rimMask * (0.7 + 0.3 * uBreath));

  float baseShade = 1.0 - smoothstep(0.04, 0.16, (p.y - base) / height);
  col = mix(col, uSh * 0.97, baseShade * 0.55);

  col = mix(col, skyC, haze);
  return vec4(col, smoothstep(0.0, edge, d));
}

vec3 skyColor(float y) {
  vec3 c = mix(uSkyHor, uSkyMid, smoothstep(0.0, 0.45, y));
  return mix(c, uSkyTop, smoothstep(0.40, 1.0, y));
}

// the meadow is nearly level: a calm line under the distant ridges, no bumps of its own
float hill(float x) {
  return 0.115 + 0.006 * sin(x * 1.6 + 0.8) + 0.003 * sin(x * 3.7 + 2.0);
}

// Gust strength along the meadow; the same formula bends the blades drawn on top.
float gust(float x) {
  float g = 0.5 + 0.5 * sin(x * 3.0 + uWind + 0.6 * sin(x * 1.3 - uWind * 0.3));
  return g * g;
}

// How much the sky is pressed together vertically where it is drawn: by the lake the whole sky above the meadow is
// fitted between the shore and the top of the screen, so the sun and the moon are stretched back by this to stay round.
float gSquash = 1.0;

// Everything behind the drop, at a point p (x scaled by the aspect ratio, y from 0 at the bottom to 1 at the top).
vec3 scene(vec2 p) {
  float asp = uRes.x / uRes.y;
  vec2 uv = vec2(p.x / asp, p.y);
  vec2 sunP = vec2(uSunPos.x * asp, uSunPos.y);
  vec2 moonP = vec2(uMoonPos.x * asp, uMoonPos.y);
  float b = uBreath;

  vec3 skyC = skyColor(uv.y);
  vec3 col = skyC;

  // stars, behind everything
  if (uStars > 0.01) {
    vec2 sg = p * 90.0;
    vec2 id = floor(sg);
    float h = hash(id);
    if (h > 0.972) {
      float k = (h - 0.972) / 0.028;
      vec2 sp = id + vec2(hash(id + 3.1), hash(id + 7.7));
      float tw = 0.55 + 0.45 * sin(uTime * (0.8 + k * 2.5) + h * 40.0);
      float s = smoothstep(0.10 + 0.16 * k, 0.0, length(sg - sp)) * tw * smoothstep(0.12, 0.45, uv.y);
      col += vec3(0.86, 0.90, 1.0) * s * uStars * (0.35 + 0.65 * k) * 1.6;
    }
    // faint milky band
    float band = exp(-pow((p.y - 0.55 - (p.x - asp * 0.5) * 0.35) * 5.0, 2.0));
    col += vec3(0.30, 0.34, 0.50) * band * fbm(p * 6.0) * 0.18 * uStars;
  }

  // moon
  vec2 dvm = p - moonP;
  dvm.y /= gSquash;
  float dm = length(dvm);
  if (uMoonAmt > 0.01) {
    float disc = smoothstep(0.036, 0.033, dm);
    float lit = smoothstep(0.030, 0.037, length(dvm - vec2(0.014, 0.008)));
    vec3 moonCol = vec3(0.98, 0.96, 0.88) * mix(0.30, 1.0, lit) * (0.92 + 0.08 * noise(p * 140.0));
    col = mix(col, moonCol, disc * uMoonAmt);
    col += vec3(0.55, 0.65, 0.90) * (exp(-dm * 8.0) * 0.22 + exp(-dm * 30.0) * 0.20) * uMoonAmt;
  }

  // sun disc and rays
  vec2 dv = p - sunP;
  dv.y /= gSquash;
  float dist = length(dv);
  if (uSunAmt > 0.01) {
    col = mix(col, uSunCol * 0.4 + vec3(0.65), smoothstep(0.032, 0.026, dist) * uSunAmt);
    float ang = atan(dv.y, dv.x);
    float rays = noise(vec2(ang * 5.0, uTime * 0.03)) * noise(vec2(ang * 11.0, 2.0 + uTime * 0.02));
    col += uSunCol * rays * exp(-dist * 1.8) * (0.05 + 0.13 * b) * uSunAmt;
  }

  vec2 c = vec2(asp * 0.5, 0.42);
  vec2 pc = c + (p - c) / (1.0 + 0.035 * b * uMotion);

  // high cirrus streaks
  float band = smoothstep(0.62, 0.76, pc.y) * (1.0 - smoothstep(0.86, 0.98, pc.y));
  if (band > 0.0) {
    float ci = fbm(vec2(pc.x * 1.4 + uDrift * 0.15 + pc.y * 0.8, pc.y * 16.0));
    col = mix(col, mix(uMd, uLt, 0.6), smoothstep(0.56, 0.74, ci) * band * 0.40);
  }

  vec4 L1 = layer(pc + vec2(uDrift * 0.35, 0.0), 0.36, 0.14, 3.0, 1.0, 0.06 + uCover, 0.030, 0.42, skyC);
  col = mix(col, L1.rgb, L1.a);
  vec4 L2 = layer(pc + vec2(uDrift * 0.60, 0.0), 0.25, 0.28, 1.9, 2.0, 0.08 + uCover, 0.018, 0.16, skyC);
  col = mix(col, L2.rgb, L2.a);
  vec4 L3 = layer(pc + vec2(uDrift, 0.0), 0.11, 0.50, 1.2, 3.0, 0.10 + uCover, 0.012, 0.0, skyC);
  col = mix(col, L3.rgb, L3.a);

  // distant ridges, hazed into the sky; they scroll slowly while walking
  // (mist pools at the foot of each ridge)
  vec3 hazeC = skyColor(0.12);
  float x1 = p.x + uWalk * 0.15;
  float r1 = 0.160 + 0.10 * (0.55 * noise(vec2(x1 * 2.6, 4.0)) + 0.30 * noise(vec2(x1 * 6.5, 5.0)) + 0.15 * noise(vec2(x1 * 15.0, 6.0)));
  vec3 ridge1 = mix(hazeC, uSkyMid * 0.6 + uGrassBot * 0.4, 0.42);
  ridge1 = mix(ridge1, hazeC, smoothstep(r1 - 0.012, r1 - 0.075, p.y) * 0.55);
  col = mix(col, ridge1, smoothstep(r1 + 0.0015, r1 - 0.0015, p.y));
  float x2 = p.x + uWalk * 0.3;
  float r2 = 0.135 + 0.07 * (0.55 * noise(vec2(x2 * 3.4, 9.0)) + 0.30 * noise(vec2(x2 * 8.0, 10.0)) + 0.15 * noise(vec2(x2 * 18.0, 11.0)));
  vec3 ridge2 = mix(hazeC, uGrassBot * 0.55 + uGrassTop * 0.45, 0.62);
  ridge2 = mix(ridge2, hazeC, smoothstep(r2 - 0.01, r2 - 0.06, p.y) * 0.45);
  col = mix(col, ridge2, smoothstep(r2 + 0.0015, r2 - 0.0015, p.y));

  // meadow: painted mass with rolling gust highlights; blades are drawn on top
  float cx0 = p.x + uWalk * 0.55;
  float hl = hill(cx0);
  float inG = 1.0 - smoothstep(hl - 0.002, hl + 0.002, p.y);
  if (inG > 0.0) {
    float depth = clamp((hl - p.y) / 0.15, 0.0, 1.0);
    float wx = p.x + uWalk * (0.55 + 0.45 * clamp((hl - p.y) / max(hl, 0.01), 0.0, 1.0));
    vec3 g = mix(uGrassTop, uGrassBot, depth);
    float w = gust(cx0 + (hl - p.y) * 0.8);
    g += uSunCol * 0.13 * smoothstep(0.35, 0.95, w) * (1.0 - depth * 0.6) * (0.25 + 0.75 * uSunAmt);
    // soft, large patches only: fine noise turns into blocks when the sky is scaled up to the screen
    g *= 0.95 + 0.07 * noise(vec2(wx * 9.0, p.y * 5.0));
    // the ground darkens towards the viewer, where the roots of the near grass are
    g = mix(g, uGrassBot * 0.45, smoothstep(0.02, hl, hl - p.y) * 0.75);
    col = mix(col, g, inG);
  }

  float glow = exp(-dist * 2.6) * 0.35 + exp(-dist * 9.0) * 0.45;
  col += uSunCol * glow * (0.50 + 0.80 * b) * uSunAmt;

  return col;
}

// The breath as a drop of water hanging in the sky: it bends what lies behind it,
// catches the light on the side facing the sun and gathers a warm glow on the far side.
vec3 lake(vec2 p);

vec3 drop(vec2 p, vec2 dc, float dr, float d) {
  vec2 q = p - dc;
  float z = sqrt(max(0.0, 1.0 - d * d));
  // like a real drop it acts as a lens: the world below shows upside down, gathered into it (by the lake, the water)
  vec2 src = dc - q * (1.4 + 1.6 * (1.0 - z)) - vec2(0.0, 0.9 * dr);
  vec3 col;
  if (uLake > 0.999) col = lake(src);
  else if (uLake < 0.001) col = scene(src);
  else col = mix(scene(src), lake(src), uLake);
  col = col * vec3(1.03, 1.02, 1.0) + 0.04;
  vec3 lightCol = mix(vec3(1.0, 0.98, 0.95), uRim, 0.25);
  col = mix(col, lightCol, pow(1.0 - z, 2.5) * 0.55);
  vec2 ld = normalize(uLight.xy + vec2(-0.35, 0.6));
  vec2 hq = q / dr - ld * 0.42;
  vec2 hr = vec2(dot(hq, ld), dot(hq, vec2(-ld.y, ld.x)));
  col += lightCol * 0.70 * smoothstep(0.20, 0.0, length(hr * vec2(1.7, 1.0)));
  col += lightCol * 0.12 * smoothstep(0.12, 0.0, length(q / dr + ld * 0.55));
  col += mix(uSunCol, vec3(1.0, 0.88, 0.66), 0.5) * 0.35 * smoothstep(0.70, 0.97, d) * smoothstep(-0.1, -0.9, dot(q / dr, ld));
  return mix(col, uSh * 0.55, smoothstep(0.96, 1.0, d) * 0.35);
}

// The lake of the 50-50 practice: the world sits on the far shore and lies upside down in the water below it.
// The whole sky above the scene's own meadow is fitted between the shore (LAKE_Y) and the top of the screen,
// so the meadow becomes the far bank; the water mirrors it with the same fit.
const float LAKE_Y = 0.42;
const float BANK_Y = 0.12;
const float FIT = (1.0 - BANK_Y) / (1.0 - LAKE_Y);

vec3 lake(vec2 p) {
  gSquash = FIT;
  if (p.y >= LAKE_Y) { vec3 sky = scene(vec2(p.x, BANK_Y + (p.y - LAKE_Y) * FIT)); gSquash = 1.0; return sky; }
  float d = LAKE_Y - p.y;              // how far below the shore
  float near = d / LAKE_Y;             // 0 at the far shore, 1 at the bottom of the screen
  // the water moves: long slow swells, and finer ripples nearer the viewer
  float swell = sin(p.y * 260.0 / (0.25 + near) - uTime * 1.1) * 0.0022 * (0.25 + near);
  float fine = (noise(vec2(p.x * 9.0 + uTime * 0.05, p.y * 70.0 - uTime * 0.35)) - 0.5) * 0.010 * near;
  vec3 refl = scene(vec2(p.x + swell + fine, BANK_Y + d * FIT * (1.0 + 0.03 * sin(p.x * 3.0 + uTime * 0.3))));
  gSquash = 1.0;
  // reflections are a little cooler and darker than the world, and the water deepens towards the viewer
  vec3 deep = mix(uSkyHor, uSh, 0.55) * 0.42 + uGrassBot * 0.08;
  float fresnel = mix(0.88, 0.38, smoothstep(0.0, 1.0, near));
  vec3 col = mix(deep, refl * vec3(0.90, 0.95, 1.0), fresnel);
  // glints where the light catches the little waves
  float glint = smoothstep(0.78, 1.0, noise(vec2(p.x * 4.0 + uTime * 0.06, p.y * 160.0 - uTime * 0.2)));
  col += mix(vec3(1.0), uSunCol, 0.5) * glint * (0.06 + 0.10 * uSunAmt + 0.06 * uMoonAmt) * (1.0 - near * 0.7);
  // a pale line where the water meets the far bank
  col = mix(col, mix(uSkyHor, vec3(1.0), 0.3), smoothstep(0.0035, 0.0, d) * 0.6);
  return col;
}

void main() {
  vec2 uv = gl_FragCoord.xy / uRes;
  float asp = uRes.x / uRes.y;
  vec2 p = vec2(uv.x * asp, uv.y);

  // same place and size as the breathing circle used to have: centred 36% from the top
  vec2 dc = vec2(asp * 0.5, 0.64);
  float dr = min(asp * 0.5, 0.32) * (0.30 + 0.22 * uBreath);
  float d = length(p - dc) / dr;
  float aa = 1.5 / (uRes.y * dr);
  float inside = uDrop * (1.0 - smoothstep(1.0 - aa, 1.0, d));

  vec3 col = vec3(0.0);
  if (inside < 0.999) {
    if (uLake > 0.999) col = lake(p);
    else if (uLake > 0.001) col = mix(scene(p), lake(p), uLake);
    else col = scene(p);
    // a soft shadow around the drop keeps it legible over a white cloud
    col *= 1.0 - uDrop * (0.10 * smoothstep(1.35, 1.0, d) + 0.06 * smoothstep(1.08, 1.0, d));
  }
  if (inside > 0.001) col = mix(col, drop(p, dc, dr, min(d, 1.0)), inside);

  col *= 1.0 - (0.10 + 0.14 * uStars) * pow(length(uv - 0.5) * 1.2, 2.0);
  col += (hash(gl_FragCoord.xy + fract(uTime) * 100.0) - 0.5) * 0.012;
  col *= 1.0 - uDim;
  gl_FragColor = vec4(col, 1.0);
}
