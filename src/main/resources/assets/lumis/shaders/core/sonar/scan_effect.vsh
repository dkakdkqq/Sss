#version 150

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord;

void main() {
    texCoord = UV0;
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
