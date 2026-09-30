#version 150

in vec2 texCoord;
in vec4 vertexColor;
in float vDist;
in vec3 vPos;

out vec4 fragColor;

uniform sampler2D Sampler0;
uniform float uTime;
uniform float uAlpha;
uniform float uAmbient;

void main() {
    vec2 uv = texCoord;

    // Сэмплируем базовую форму подошвы и протектора ботинка
    float base = texture(Sampler0, uv).a;
    if (base <= 0.005) {
        discard;
    }

    // 1. Физическая 3D-деформация снега (Normal Mapping из градиента глубины вдавливания)
    vec2 texStep = vec2(0.012, 0.008);
    float hL = texture(Sampler0, uv - vec2(texStep.x, 0.0)).a;
    float hR = texture(Sampler0, uv + vec2(texStep.x, 0.0)).a;
    float hD = texture(Sampler0, uv - vec2(0.0, texStep.y)).a;
    float hU = texture(Sampler0, uv + vec2(0.0, texStep.y)).a;

    // Вычисляем вектор нормали вогнутой поверхности следа
    vec3 normal = normalize(vec3((hL - hR) * 2.8, (hD - hU) * 2.8, 1.0));

    // 2. Направленное освещение снежного следа (свет сверху-сбоку)
    vec3 lightDir = normalize(vec3(0.32, 0.58, 0.75));
    float diff = clamp(dot(normal, lightDir), 0.0, 1.0);

    // Блик на спрессованных гранях протектора
    vec3 viewDir = vec3(0.0, 0.0, 1.0);
    vec3 halfVec = normalize(lightDir + viewDir);
    float spec = pow(clamp(dot(normal, halfVec), 0.0, 1.0), 16.0) * 0.35;

    // 3. Реалистичные цвета спрессованного снега
    // В глубине следа снег имеет холодный синевато-морозный оттенок тени (рэлеевское рассеяние в кристаллах льда)
    float amb = clamp(0.40 + uAmbient * 0.60, 0.35, 1.0);
    vec3 coldShadow = vec3(0.44, 0.60, 0.84) * vertexColor.rgb * amb;
    vec3 snowHighlight = vec3(1.04, 1.08, 1.15) * vertexColor.rgb * amb;
    vec3 soleColor = mix(coldShadow, snowHighlight, diff * 0.55 + 0.45 * base) + spec * vec3(0.9, 0.96, 1.0);

    // 4. Искрящиеся кристаллики снега (Micro-facet Snow Glitter)
    float sparkleNoise = sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233)) * 0.85 + uTime * 1.6);
    float sparkle = pow(clamp(sparkleNoise * 0.5 + 0.5, 0.0, 1.0), 22.0) * 0.50 * base;
    soleColor += sparkle * vec3(0.85, 0.95, 1.0);

    // 5. Ветровая эрозия и засыпание снегом со временем (Dissolve)
    // След не просто равномерно тускнеет, а постепенно заметается порошей от краев к центру
    float age = vertexColor.a; // 1.0 = свежий, 0.0 = полностью исчез
    float dissolve = 1.0 - age;
    float grain = fract(sin(dot(uv * 32.0, vec2(17.1, 43.7))) * 31415.9265);
    float erosionMask = smoothstep(dissolve - 0.22, dissolve + 0.08, base + grain * 0.14 * dissolve);
    float finalAlpha = erosionMask * smoothstep(0.0, 0.10, age) * uAlpha;

    // 6. Мягкое растворение на дальней дистанции
    float distFade = smoothstep(44.0, 28.0, vDist);
    finalAlpha *= distFade;

    if (finalAlpha <= 0.005) {
        discard;
    }

    fragColor = vec4(soleColor, finalAlpha);
}
