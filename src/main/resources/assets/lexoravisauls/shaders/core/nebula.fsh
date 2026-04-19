#version 150

uniform float u_Time;
uniform vec2 u_Resolution;
uniform vec3 u_Color;
uniform float u_Alpha;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / u_Resolution.xy;

    // Плавные волны (без сложной математики, которая крашит Intel)
    float wave1 = sin(uv.x * 5.0 + u_Time) * cos(uv.y * 5.0 - u_Time * 0.5);
    float wave2 = sin(uv.x * 10.0 - u_Time * 1.5) * cos(uv.y * 10.0 + u_Time);

    // Смешиваем волны в красивую туманность
    float intensity = (wave1 + wave2) * 0.5 + 0.5;

    // Делаем цвет ярче в местах сгустков
    vec3 glow = u_Color * (0.5 + intensity * 1.5);

    fragColor = vec4(glow, u_Alpha * intensity);
}