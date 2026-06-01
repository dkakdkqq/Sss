#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 Size;

out vec2 FragCoord;
out vec4 FragColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    if (gl_VertexID == 0) {
        FragCoord = vec2(0.0, 0.0);
    } else if (gl_VertexID == 1) {
        FragCoord = vec2(0.0, 1.0);
    } else if (gl_VertexID == 2) {
        FragCoord = vec2(1.0, 1.0);
    } else {
        FragCoord = vec2(1.0, 0.0);
    }

    FragColor = Color;
}