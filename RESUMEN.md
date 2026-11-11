# Resumen

## Estado: terminado

Todo lo pedido está hecho, también las dos funciones opcionales (3MF con cambios de color y marco).

### Hecho
- Núcleo en Java puro (`com.pic2filament.core`): color (sRGB/lineal/Lab D65), simulación de la pila, asignación de alturas, mediana 3×3, optimizador de cambios, k-means en Lab, malla estanca, STL binario, 3MF, ZIP, instrucciones para el laminador y vistas previas (simulación, relieve, alturas y original).
- Servicios: almacén de trabajos con ficheros temporales y limpieza automática, carga segura de imágenes (PNG/JPG/WebP, límite de tamaño y de megapíxeles) y biblioteca de filamentos en YAML editable.
- Web: API REST, página Thymeleaf + Bootstrap 5.3.8 en local (sin CDN), interfaz en español, adaptable a móvil, con tema claro y oscuro, arrastrar, elegir o pegar imagen, vista previa sin recarga, pila de capas, tabla de cambios y descargas (ZIP, STL y 3MF).
- Errores: JSON coherente con mensajes en español para la API y página `error.html` para el navegador.
- Docker multietapa (JRE 25 Alpine, usuario no root, HEALTHCHECK), docker-compose (puerto `APP_PORT`, `restart: unless-stopped`, 1 GB), workflow de GitHub Actions, README, DECISIONES.

## Cómo arrancarlo
```bash
docker compose up -d --build                     # http://localhost:8080
APP_PORT=9090 docker compose up -d --build       # otro puerto
# o en local (necesita JDK 25):
./mvnw spring-boot:run
```

## Qué he comprobado
- `./mvnw verify` → **BUILD SUCCESS**: 62 pruebas unitarias y de integración y 1 de extremo a extremo, todas en verde.
- **App en marcha** (java -jar), con peticiones curl reales sobre la imagen de ejemplo:
  - Subida → 201.
  - Proceso 401×301 con 4 filamentos en unos 100 ms.
  - Las 4 vistas previas → 200. Revisé la simulación y se parece al original: montañas, sol, texto y degradado reconocibles, con la limitación de los colores elegidos.
  - ZIP (STL de 12,2 MB, PNG y TXT), STL y 3MF → 200.
  - Errores: fichero no válido → 415, demasiado grande → 413, trabajo inexistente → 404, ajustes inválidos → 400 con mensajes en español.
- **Interfaz en Chrome sin cabeza**, en escritorio (claro) y móvil de 390 px (oscuro): la subida, la vista previa, la pila, la tabla, «Detectar colores» y las descargas funcionan, sin errores de JavaScript. La captura está en `docs/captura.png`.
- **Docker**: imagen construida (359 MB). Contenedor arrancado con compose: estado **healthy** en unos 6 s, se ejecuta como usuario `app` (no root), límite de 1 GB y reinicio `unless-stopped`. La salud responde UP y una conversión completa da el mismo resultado que en local (unos 175 MB de memoria en uso). Después lo paré y lo borré.
- Resultados del ejemplo en `ejemplo/salida/`: `ejemplo-sintetico.stl`, `-vista-previa.png` y `-cambios.txt`.

## Pendiente o para revisar
- **Imagen de ejemplo**: no había ninguna en `ejemplo/`, así que usé una sintética. Copia tus imágenes a `src/test/resources/ejemplo-*.png|jpg` y ejecuta `./mvnw verify`: se procesarán y sus resultados irán a `ejemplo/salida/`.
- **3MF**: hay que probar en Bambu Studio, OrcaSlicer y PrusaSlicer que se reconocen los cambios integrados.
- **TD de la biblioteca**: son valores aproximados. Mide los tuyos.
- **Workflow de CI**: no se ha ejecutado en GitHub porque no he hecho push.
- No se lee la orientación EXIF de los JPG. Las fotos de móvil giradas pueden salir rotadas.
