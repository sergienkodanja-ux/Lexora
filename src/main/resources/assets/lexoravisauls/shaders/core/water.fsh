#version 150

uniform vec2 u_Resolution;
uniform float u_Time;
uniform vec3 u_Color;
uniform float u_Alpha;

out vec4 fragColor;

#define TAU 6.28318530718
#define MAX_ITER 5

void main() {
    float time = u_Time * .5 + 23.0;
    vec2 uv = gl_FragCoord.xy / u_Resolution.xy;

    vec2 p = mod(uv * TAU, TAU) - 250.0;
    vec2 i = vec2(p);
    float c = 1.0;
    float inten = .005;

    for (int n = 0; n < MAX_ITER; n++) {
        float t = time * (1.0 - (3.5 / float(n+1)));
        i = p + vec2(cos(t - i.x) + sin(t + i.y), sin(t - i.y) + cos(t + i.x));
        c += 1.0 / length(vec2(p.x / (sin(i.x+t)/inten), p.y / (cos(i.y+t)/inten)));
    }
    c /= float(MAX_ITER);
    c = 1.17 - pow(c, 1.4);

    // Красим в цвет из GUI
    vec3 colour = vec3(pow(abs(c), 8.0));
    colour = clamp(colour + (u_Color * 0.8), 0.0, 1.0);

    fragColor = vec4(colour, u_Alpha * clamp(colour.r + colour.g + colour.b, 0.1, 1.0)); // Прозрачность зависит от яркости
}