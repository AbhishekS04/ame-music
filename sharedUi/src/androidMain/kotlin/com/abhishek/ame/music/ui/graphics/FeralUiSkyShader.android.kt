package com.abhishek.ame.music.ui.graphics

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

private const val TAG = "FeralUiSkyShader"

private val noiseShader: Shader by lazy {
    val size = 256
    val pixels = IntArray(size * size)
    val random = java.util.Random(1337)
    for (i in pixels.indices) {
        val value = random.nextInt(256)
        pixels[i] = (0xFF shl 24) or (value shl 16) or (value shl 8) or value
    }
    val bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
}

private const val AGSL_SKY_SOURCE = """
uniform float2 u_res;
uniform float u_t;
uniform half3 u_main;
uniform half3 u_low;
uniform half3 u_mid;
uniform half3 u_high;
uniform float u_wind;
uniform float u_warp;
uniform float u_nscale;
uniform float2 u_dirv;
uniform shader u_noise;

float rand2(float2 n) {
    return fract(sin(dot(n, float2(12.9898, 4.1414))) * 43758.5453);
}

float noise2(float2 p) {
    float2 ip = floor(p);
    float2 u = fract(p);
    u = u * u * (float2(3.0, 3.0) - float2(2.0, 2.0) * u);
    float m = mix(mix(rand2(ip), rand2(ip + float2(1.0, 0.0)), u.x),
                  mix(rand2(ip + float2(0.0, 1.0)), rand2(ip + float2(1.0, 1.0)), u.x), u.y);
    return m * m;
}

float2 rot2(float2 p) {
    float c = 0.87758256;
    float s = 0.47942554;
    return float2(c * p.x - s * p.y, s * p.x + c * p.y);
}

float fbm4(float2 x) {
    float v = 0.0;
    float a = 0.5;
    float2 shift = float2(100.0, 100.0);
    for (int i = 0; i < 4; i++) {
        v += a * noise2(x);
        x = rot2(x) * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

float4 permute4(float4 x) {
    return mod((x * 34.0 + float4(1.0, 1.0, 1.0, 1.0)) * x, 289.0);
}

float4 tisqrt(float4 r) {
    return float4(1.7928429, 1.7928429, 1.7928429, 1.7928429) - float4(0.8537347, 0.8537347, 0.8537347, 0.8537347) * r;
}

float3 fade3(float3 t) {
    return t * t * t * (t * (t * 6.0 - float3(15.0, 15.0, 15.0)) + float3(10.0, 10.0, 10.0));
}

float cnoise(float3 P) {
    float3 Pi0 = floor(P);
    float3 Pi1 = Pi0 + float3(1.0, 1.0, 1.0);
    Pi0 = mod(Pi0, 289.0);
    Pi1 = mod(Pi1, 289.0);
    float3 Pf0 = fract(P);
    float3 Pf1 = Pf0 - float3(1.0, 1.0, 1.0);
    float4 ix = float4(Pi0.x, Pi1.x, Pi0.x, Pi1.x);
    float4 iy = float4(Pi0.y, Pi0.y, Pi1.y, Pi1.y);
    float4 iz0 = float4(Pi0.z, Pi0.z, Pi0.z, Pi0.z);
    float4 iz1 = float4(Pi1.z, Pi1.z, Pi1.z, Pi1.z);
    float4 ixy = permute4(permute4(ix) + iy);
    float4 ixy0 = permute4(ixy + iz0);
    float4 ixy1 = permute4(ixy + iz1);
    float4 gx0 = ixy0 / 7.0;
    float4 gy0 = fract(floor(gx0) / 7.0) - float4(0.5, 0.5, 0.5, 0.5);
    gx0 = fract(gx0);
    float4 gz0 = float4(0.5, 0.5, 0.5, 0.5) - abs(gx0) - abs(gy0);
    float4 sz0 = step(gz0, float4(0.0, 0.0, 0.0, 0.0));
    gx0 -= sz0 * (step(float4(0.0, 0.0, 0.0, 0.0), gx0) - float4(0.5, 0.5, 0.5, 0.5));
    gy0 -= sz0 * (step(float4(0.0, 0.0, 0.0, 0.0), gy0) - float4(0.5, 0.5, 0.5, 0.5));
    float4 gx1 = ixy1 / 7.0;
    float4 gy1 = fract(floor(gx1) / 7.0) - float4(0.5, 0.5, 0.5, 0.5);
    gx1 = fract(gx1);
    float4 gz1 = float4(0.5, 0.5, 0.5, 0.5) - abs(gx1) - abs(gy1);
    float4 sz1 = step(gz1, float4(0.0, 0.0, 0.0, 0.0));
    gx1 -= sz1 * (step(float4(0.0, 0.0, 0.0, 0.0), gx1) - float4(0.5, 0.5, 0.5, 0.5));
    gy1 -= sz1 * (step(float4(0.0, 0.0, 0.0, 0.0), gy1) - float4(0.5, 0.5, 0.5, 0.5));
    float3 g000 = float3(gx0.x, gy0.x, gz0.x);
    float3 g100 = float3(gx0.y, gy0.y, gz0.y);
    float3 g010 = float3(gx0.z, gy0.z, gz0.z);
    float3 g110 = float3(gx0.w, gy0.w, gz0.w);
    float3 g001 = float3(gx1.x, gy1.x, gz1.x);
    float3 g101 = float3(gx1.y, gy1.y, gz1.y);
    float3 g011 = float3(gx1.z, gy1.z, gz1.z);
    float3 g111 = float3(gx1.w, gy1.w, gz1.w);
    float4 n0 = tisqrt(float4(dot(g000, g000), dot(g010, g010), dot(g100, g100), dot(g110, g110)));
    g000 *= n0.x; g010 *= n0.y; g100 *= n0.z; g110 *= n0.w;
    float4 n1 = tisqrt(float4(dot(g001, g001), dot(g011, g011), dot(g101, g101), dot(g111, g111)));
    g001 *= n1.x; g011 *= n1.y; g101 *= n1.z; g111 *= n1.w;
    float n000 = dot(g000, Pf0);
    float n100 = dot(g100, float3(Pf1.x, Pf0.y, Pf0.z));
    float n010 = dot(g010, float3(Pf0.x, Pf1.y, Pf0.z));
    float n110 = dot(g110, float3(Pf1.x, Pf1.y, Pf0.z));
    float n001 = dot(g001, float3(Pf0.x, Pf0.y, Pf1.z));
    float n101 = dot(g101, float3(Pf1.x, Pf0.y, Pf1.z));
    float n011 = dot(g011, float3(Pf0.x, Pf1.y, Pf1.z));
    float n111 = dot(g111, Pf1);
    float3 fx = fade3(Pf0);
    float4 nz = mix(float4(n000, n100, n010, n110), float4(n001, n101, n011, n111), fx.z);
    float2 ny = mix(nz.xy, nz.zw, fx.y);
    return 2.2 * mix(ny.x, ny.y, fx.x);
}

half3 burn(half3 base, half3 blend, half op) {
    return max(base + blend - half3(1.0, 1.0, 1.0), half3(0.0, 0.0, 0.0)) * op + base * (half(1.0) - op);
}

half4 main(float2 fragCoord) {
    float2 st = float2(fragCoord.x / u_res.x - 0.5, (u_res.y - fragCoord.y) / u_res.y - 0.5);
    st.x *= u_res.x / u_res.y;
    st = float2(u_dirv.x * st.x - u_dirv.y * st.y, u_dirv.y * st.x + u_dirv.x * st.y);
    float time = u_t * 0.85;
    float2 uv = st * (1.0 / (2.0 * 0.3971)) + float2(0.5, 0.5);
    float noiseX = cnoise(float3(uv * u_nscale + float2(0.0, 74.8572), time * 0.3));
    float noiseY = cnoise(float3(uv * u_nscale + float2(203.91282, 10.0), time * 0.3));
    uv += float2(noiseX * 2.0, noiseY) * u_warp;
    float noiseA = cnoise(float3(uv * 18.0 + float2(344.91282, 0.0), time * 0.3))
                 + cnoise(float3(uv * 39.6 + float2(723.937, 0.0), time * 0.4)) * 0.5;
    uv += float2(noiseA * 0.02, noiseA * 0.02);
    uv.y -= 0.09;

    // Hardware-filtered noise sampling for ethereal filament wisps
    float xf = (sin(time) + 1.0) * 0.5;
    float2 texUv = uv * 2.5;
    float d0 = mix(u_noise.eval(texUv * 256.0).r - 0.5, u_noise.eval(float2(texUv.x, 1.0 - texUv.y) * 256.0).g - 0.5, xf) * 0.014;
    texUv += float2(63.861, 368.937);
    float d1 = mix(u_noise.eval(texUv * 256.0).r - 0.5, u_noise.eval(float2(texUv.x, 1.0 - texUv.y) * 256.0).g - 0.5, xf) * 0.014;
    texUv += float2(453.163, 1649.808);
    float d3 = mix(u_noise.eval(texUv * 256.0).r - 0.5, u_noise.eval(float2(texUv.x, 1.0 - texUv.y) * 256.0).g - 0.5, xf) * 0.014;
    uv += d0;

    float2 stF = uv * u_nscale;
    float qv = fbm4(stF * 0.5 + float2(u_wind * time, u_wind * time));
    float2 q = float2(qv, qv);
    float2 r = float2(fbm4(stF + q + float2(0.3, 9.2) + float2(0.15 * time, 0.15 * time)),
                      fbm4(stF + q + float2(8.3, 0.8) + float2(0.126 * time, 0.126 * time)));
    float fv = fbm4(stF + r - q);
    float full = (fv + 0.6 * fv * fv + 0.7 * fv + 0.5) * 0.5;
    full = pow(full, 0.55) * 0.912;
    float blurR = 1.2673 * 1.5;

    float2 uvA = uv + float2((full - 0.5) * 1.2, (full - 0.5) * 1.2) + float2(0.0, 0.025) + d0;
    float snA = noise2(uvA * 2.0 + float2(0.0, time * 0.5)) * 3.0;
    half lA = half(pow(smoothstep(snA - 1.2 * blurR, snA + 1.2 * blurR, (uvA.y - 0.5) * 5.0 + 0.5), 0.8));

    float2 uvB = uv + float2((full - 0.5) * 0.85, (full - 0.5) * 0.85) + float2(0.0, 0.025) + d1;
    float snB = noise2(uvB * 4.0 + float2(293.0, time)) * 2.8;
    half lB = half(pow(smoothstep(snB - 0.9 * blurR, snB + 0.9 * blurR, (uvB.y - 0.6) * 5.0 + 0.5), 0.9));

    float2 uvC = uv + float2((full - 0.5) * 1.1, (full - 0.5) * 1.1) + d3;
    float snC = noise2(uvC * 6.0 + float2(153.0, time * 1.2)) * 2.6;
    half lC = half(smoothstep(snC - 0.7 * blurR, snC + 0.7 * blurR, (uvC.y - 0.9) * 6.0 + 0.5));

    half3 col = burn(u_main, u_low, half(1.0) - lA);
    col = burn(col, mix(u_main, u_mid, half(1.0) - lB), lA);
    col = mix(col, mix(u_main, u_high, half(1.0) - lC), lA * lB);

    // Subtle tactile film grain matching FeralUI
    col += (u_noise.eval(fragCoord).r - half(0.5)) * half(8.0 / 255.0);

    return half4(clamp(col, half3(0.0, 0.0, 0.0), half3(1.0, 1.0, 1.0)), half(1.0));
}
"""

private const val AGSL_PRISM_SOURCE = """
uniform float2 u_res;
uniform float u_t;

// FeralUI Cherry Pop Palette Stops:
// INK PURPLE:     #521A37
// DUSK CRIMSON:   #9C204C
// PERSIMMON RED:  #E24762
// SOFT CRIMSON:   #F8968E
// PEACH SILK:     #F7D0B5
const half3 C0 = half3(0.3216, 0.1020, 0.2157);
const half3 C1 = half3(0.6118, 0.1255, 0.2980);
const half3 C2 = half3(0.8863, 0.2784, 0.3843);
const half3 C3 = half3(0.9725, 0.5882, 0.5569);
const half3 C4 = half3(0.9686, 0.8157, 0.7098);

half3 sampleCherryPop(float t) {
    float c = clamp(t, 0.0, 1.0);
    if (c < 0.3) {
        float f = clamp((c - 0.1) / 0.2, 0.0, 1.0);
        return mix(C0, C1, half(f));
    } else if (c < 0.5) {
        float f = (c - 0.3) / 0.2;
        return mix(C1, C2, half(f));
    } else if (c < 0.7) {
        float f = (c - 0.5) / 0.2;
        return mix(C2, C3, half(f));
    } else {
        float f = clamp((c - 0.7) / 0.2, 0.0, 1.0);
        return mix(C3, C4, half(f));
    }
}

half4 main(float2 fragCoord) {
    float u = fragCoord.x / u_res.x;
    float v = fragCoord.y / u_res.y;

    float a = 11.0;
    float c = u_t * 0.48;
    float xDist = abs(u - 0.5) * 2.0;

    // Moving cascading slats expanding outward from center
    float slatCoord = xDist * a - c;
    float b = floor(slatCoord);
    float slatFrac = slatCoord - b;

    float C = (b + 1.0 + c) / a;
    if (C <= 0.0) {
        return half4(C0, 1.0);
    }

    // Valley V-notch chevron curve
    float D = clamp((b + c + 0.5) / a, 0.0, 1.0);
    float m = 0.5 - 0.5 * cos(D * 3.14159265);
    float S = 1.0 - m;
    float v_osc = 0.015 * sin(u_t * 0.32);
    float R = 0.28 + S * 0.36 + v_osc;
    float I = 0.025 * sin(b * 1.8) + 0.012 * sin(b * 0.73 + u_t * 0.24);
    float T = 0.74;

    float ramp = (v - R + 0.22) / T;
    float t = clamp(ramp, 0.0, 1.0);
    float smooth_t = t * t * (3.0 - 2.0 * t);
    float H = clamp(0.02 + 0.96 * smooth_t + I, 0.0, 1.0);

    half3 col = sampleCherryPop(H);

    // Slat border separation
    float edgeShadow = clamp(slatFrac / 0.08, 0.0, 1.0) * clamp((1.0 - slatFrac) / 0.08, 0.0, 1.0);
    col *= half(0.92 + 0.08 * edgeShadow);

    return half4(col, 1.0);
}
"""

private class AndroidSkyShader(private val runtimeShader: RuntimeShader) : SkyShader {
    private var lastW = -1f
    private var lastH = -1f

    override val brush: Brush = object : ShaderBrush() {
        override fun createShader(size: Size): Shader {
            val w = if (size.width > 0f) size.width else 1f
            val h = if (size.height > 0f) size.height else 1f
            if (w != lastW || h != lastH) {
                lastW = w
                lastH = h
                runtimeShader.setFloatUniform("u_res", w, h)
            }
            return runtimeShader
        }
    }

    override fun updateTimeAndSize(time: Float, width: Float, height: Float) {
        runtimeShader.setFloatUniform("u_t", time)
        val w = if (width > 0f) width else 1f
        val h = if (height > 0f) height else 1f
        if (w != lastW || h != lastH) {
            lastW = w
            lastH = h
            runtimeShader.setFloatUniform("u_res", w, h)
        }
    }
}

@Composable
actual fun rememberSkyShader(
    tonesHigh: Color,
    tonesMain: Color,
    tonesMid: Color,
    tonesLow: Color,
    scale: Float,
    distortion: Float,
    swirl: Float,
): SkyShader? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null

    return remember(tonesHigh, tonesMain, tonesMid, tonesLow, scale, distortion, swirl) {
        try {
            val shader = RuntimeShader(AGSL_SKY_SOURCE)
            shader.setInputShader("u_noise", noiseShader)
            shader.setFloatUniform("u_main", tonesMain.red, tonesMain.green, tonesMain.blue)
            shader.setFloatUniform("u_low", tonesLow.red, tonesLow.green, tonesLow.blue)
            shader.setFloatUniform("u_mid", tonesMid.red, tonesMid.green, tonesMid.blue)
            shader.setFloatUniform("u_high", tonesHigh.red, tonesHigh.green, tonesHigh.blue)
            shader.setFloatUniform("u_nscale", 0.35f + scale / 100f * 1.15f)
            shader.setFloatUniform("u_warp", distortion / 100f * 0.47f)
            shader.setFloatUniform("u_wind", swirl / 100f * 0.36f)
            shader.setFloatUniform("u_dirv", 1.0f, 0.0f)
            AndroidSkyShader(shader)
        } catch (e: Throwable) {
            Log.e(TAG, "AGSL SkyShader compilation failed", e)
            null
        }
    }
}

@Composable
actual fun rememberPrismShader(): SkyShader? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null

    return remember {
        try {
            val shader = RuntimeShader(AGSL_PRISM_SOURCE)
            AndroidSkyShader(shader)
        } catch (e: Throwable) {
            Log.e(TAG, "AGSL PrismShader compilation failed", e)
            null
        }
    }
}
