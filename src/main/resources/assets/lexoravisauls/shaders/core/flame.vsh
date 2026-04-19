#version 150

in vec3 Position;

uniform mat4 u_ModelViewMat;
uniform mat4 u_ProjMat;

void main() {
    gl_Position = u_ProjMat * u_ModelViewMat * vec4(Position, 1.0);
}