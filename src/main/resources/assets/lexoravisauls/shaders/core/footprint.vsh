#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ProjMat;
uniform mat4 ModelViewMat;

out vec2 texCoord;
out vec4 vertexColor;
out float vDist;
out vec3 vPos;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;
    texCoord = UV0;
    vertexColor = Color;
    vDist = length(Position);
    vPos = Position;
}
