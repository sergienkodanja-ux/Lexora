#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 screenPos;

void main() {
    vec4 pos    = ProjMat * ModelViewMat * vec4(Position, 1.0);
    gl_Position = pos;
    // Передаём NDC координаты во фрагментный шейдер
    screenPos   = pos.xy;
}
