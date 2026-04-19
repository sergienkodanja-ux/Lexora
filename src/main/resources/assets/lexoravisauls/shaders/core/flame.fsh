#version 150

uniform float u_Time;
uniform vec2 u_Resolution;
uniform vec3 u_Color;
uniform float u_Alpha;

out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / u_Resolution.xy;

    // Создаем движение вверх (эффект огня)
    vec2 p = uv;
    p.y += u_Time * 1.5;

    // Генерируем "языки пламени" по всему объему
    float fire = sin(p.x * 12.0 + sin(p.y * 5.0)) * cos(p.y * 10.0 - u_Time * 3.0);
    fire = abs(fire); // Делаем их яркими везде

    // Добавляем ядро пламени (бело-желтый оттенок)
    vec3 fireColor = mix(u_Color, vec3(1.0, 1.0, 1.0), fire * 0.4);

    fragColor = vec4(fireColor, u_Alpha * (0.3 + fire * 0.7));
}