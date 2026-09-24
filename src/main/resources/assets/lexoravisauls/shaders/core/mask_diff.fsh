#version 150

// Маска руки = разница цвета кадра ДО и ПОСЛЕ рендера рук.
// Между снимками рисуются только руки, поэтому изменившиеся пиксели = силуэт руки.
//   Sampler0 — сцена до рук
//   Sampler1 — сцена после рук

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec2 TexCoord;
out vec4 OutColor;

void main() {
    vec4 before = texture(Sampler0, TexCoord);
    vec4 after = texture(Sampler1, TexCoord);

    vec3 colorDiff = abs(after.rgb - before.rgb);
    float maxColorDiff = max(max(colorDiff.r, colorDiff.g), colorDiff.b);
    float isHand = maxColorDiff > 0.01 ? 1.0 : 0.0;

    float topDistance = 1.0 - TexCoord.y;
    isHand *= smoothstep(0.012, 0.075, topDistance);

    OutColor = vec4(isHand, isHand, isHand, isHand);
}
