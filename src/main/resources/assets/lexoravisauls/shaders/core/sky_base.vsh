#version 150

in vec3 Position;

out vec3 vRayDir;
out vec2 vUV;

uniform mat4 u_InvProjView;

void main() {
    gl_Position = vec4(Position.xy, 0.9999, 1.0);
    vUV = Position.xy * 0.5 + 0.5;
    vec4 worldPos = u_InvProjView * vec4(Position.xy, 1.0, 1.0);
    vRayDir = worldPos.xyz / worldPos.w;
}