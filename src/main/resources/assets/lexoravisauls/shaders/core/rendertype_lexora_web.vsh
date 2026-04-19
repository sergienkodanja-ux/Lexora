#version 150

in vec3 Position;

uniform mat4 u_LexoraModelView;
uniform mat4 u_LexoraProj;

out vec3 localPos;

void main() {
    gl_Position = u_LexoraProj * u_LexoraModelView * vec4(Position, 1.0);
    localPos = Position; // 🔥 Вот тут был фикс! Теперь координаты работают правильно
}