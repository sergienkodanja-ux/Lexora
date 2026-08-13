#version 150

uniform sampler2D MainSampler;
uniform sampler2D MainDepthSampler;

in vec2 texCoord;

uniform mat4 mvInverse;
uniform mat4 projInverse;
uniform mat4 prevModelView;
uniform mat4 prevProjection;
uniform vec3 cameraPos;
uniform vec3 prevCameraPos;
uniform vec2 view_res;

uniform float BlendFactor;
uniform float inverseSamples;
uniform int motionBlurSamples;
uniform int halfSamples;
uniform int blurAlgorithm;

out vec4 fragColor;

void main() {
    float depth = texture(MainDepthSampler, texCoord).r;
    vec4 currentColor = texture(MainSampler, texCoord);

    // Skip the sky / far plane — nothing to reproject against.
    if (depth >= 1.0) {
        fragColor = currentColor;
        return;
    }

    // Reconstruct clip-space position, then unproject to view space,
    // then to world space via the inverse model-view matrix.
    vec4 clipPos = vec4(texCoord.x * 2.0 - 1.0, texCoord.y * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = projInverse * clipPos;
    viewPos /= viewPos.w;
    vec4 worldPos = mvInverse * viewPos;
    worldPos /= worldPos.w;

    // Account for the camera having moved between frames: worldPos above
    // is relative to the current camera, shift it into the previous
    // frame's camera-relative space before reprojecting.
    vec3 worldOffset = (cameraPos - prevCameraPos);
    vec4 prevRelativePos = vec4(worldPos.xyz + worldOffset, 1.0);

    // Reproject into the previous frame's clip space.
    vec4 prevClip = prevProjection * prevModelView * prevRelativePos;
    prevClip /= prevClip.w;
    vec2 prevTexCoord = prevClip.xy * 0.5 + 0.5;

    vec2 velocity = (texCoord - prevTexCoord) * BlendFactor;

    int samples = max(motionBlurSamples, 1);

    vec4 accumColor = currentColor;
    float validSamples = 1.0;
    vec2 sampleCoord = texCoord;

    for (int i = 1; i < samples; i++) {
        sampleCoord -= velocity * inverseSamples;
        vec2 clampedCoord = clamp(sampleCoord, vec2(0.0), vec2(1.0));
        accumColor += texture(MainSampler, clampedCoord);
        validSamples += 1.0;
    }

    fragColor = accumColor / validSamples;
    fragColor.a = 1.0;
}
