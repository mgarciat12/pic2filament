# Decisiones tomadas durante el desarrollo

Decisiones tomadas sin supervisión, con el motivo de cada una.

## Versiones y entorno
- **Spring Boot 4.1.2**: es la última versión estable publicada en Maven Central (4.2.0 solo está en RC). Lo comprobé en `maven-metadata.xml`.
- **Java 25**: es la LTS más reciente y Spring Boot 4.1 la admite. En este Mac el `JAVA_HOME` por defecto es Java 22, así que compilé con el JDK 25 de Homebrew (`/opt/homebrew/opt/openjdk`). Si `./mvnw` falla con «release version 25 not supported», hay que apuntar `JAVA_HOME` a un JDK 25.
- **Bootstrap 5.3.8** (WebJar): es la última versión publicada como WebJar. Se sirve en local con `webjars-locator-lite`, y las URLs incluyen la versión para invalidar la caché.
- **Maven Wrapper 3.3.4** con Maven 3.9.14 (`only-script`).
- **Lectura de WebP**: ImageIO no lee WebP, así que añadí TwelveMonkeys `imageio-webp` 3.15.3 (Java puro).

## Convenciones
- El encargo dejaba sin rellenar el nombre del repositorio de referencia («[NOMBRE DE OTRO REPO MÍO]»). Encontré `~/Documents/Proyectos/pixedpub` (Spring Boot 4.1.2, mvnw, Docker) y **copié sus convenciones**: estructura del pom, Dockerfile multietapa con capas extraídas y `JarLauncher`, JRE Alpine, healthcheck con `wget`, compose con `restart: unless-stopped`, `.gitattributes` y `.dockerignore`.
- pixedpub no tiene workflow de GitHub Actions, así que usé el workflow por defecto pedido: `mvn verify`, construcción de la imagen Docker y comprobación de salud del contenedor.

## Imagen de ejemplo
- **No existía la carpeta `ejemplo/`**. Generé una imagen sintética, `ejemplo/ejemplo-sintetico.png` (640×480, con cielo degradado, sol, montañas, suelo, texto y una barra de grises), y la copié a `src/test/resources`.
- La prueba de extremo a extremo (`EndToEndIT`) procesa **todas** las imágenes `ejemplo*.png|jpg|webp` de `src/test/resources`. Si añades tus imágenes ahí, se procesarán y sus resultados aparecerán en `ejemplo/salida/`.
- `ejemplo/salida/*.stl` está en `.gitignore` porque pesa unos 12 MB. La vista previa y el `.txt` sí se versionan.

## Algoritmo
- **Rejilla**: cada píxel de la rejilla es un vértice de la malla. Tiene `round(ancho/detalle)+1` columnas (401 con los valores por defecto), así que la pieza mide exactamente el ancho pedido. Si un lado supera 600, se reduce el detalle y se mantiene el ancho.
- **Rango de alturas**: cada píxel va de `capasBase` a `capasTotales` capas. Z = primera_capa + (capas−1)·altura_capa.
- **Optimizador**:
  - Las capas de inicio de los filamentos 2..F se buscan en el rango [2, N], estrictamente crecientes. Se permite que un filamento empiece dentro de la base.
  - Los colores se agrupan en cubos de 4 bits por canal sRGB, ponderados por su frecuencia.
  - La fase gruesa limita la búsqueda a unas 4000 combinaciones y ajusta el paso según las capas.
  - Le sigue un refinado por coordenadas con paso 1.
  - Siempre se evalúa también el reparto uniforme y se parte del mejor de los dos, así que el resultado nunca es peor que el uniforme.
- **Contraste y brillo**: L' = (L−50)·contraste + 50 + brillo, recortado a [0,100].
- **Fondo de la malla**: se triangula en abanico desde el centro con los mismos vértices del perímetro. Dos triángulos grandes dejarían vértices en T, y la malla no sería estanca.
- **Remuestreo**: al reducir se promedia el área en RGB lineal y al ampliar se usa interpolación bilineal.
- **Imágenes grandes**: al subirlas se reducen a 2400 px de lado mayor (4 × la rejilla máxima), porque más resolución no aporta nada. Antes de decodificar se rechazan las que superan 60 megapíxeles.

## Estado y seguridad
- **Sin sesión HTTP**: cada trabajo se identifica con un UUID aleatorio de 128 bits, que actúa como «capacidad». Así la API es sencilla de usar con curl. La imagen se guarda en `TEMP_DIR/<id>/imagen.png`. La imagen y el resultado se mantienen en memoria con `SoftReference`; si la memoria se libera, se recargan del disco o se recalculan.
- **Limpieza**: una tarea cada 5 minutos borra los trabajos sin uso durante `JOB_TTL` (2 h). Hay un máximo de `MAX_JOBS` (200) trabajos y, si se supera, se borran los más antiguos. Al arrancar se borran los restos de ejecuciones anteriores.
- **Validación**: el tamaño del fichero se comprueba también en el servicio, no solo en Tomcat. Así el 413 funciona igual en MockMvc y en el servidor real. `server.tomcat.max-swallow-size=100MB` hace que el navegador reciba el JSON de error en lugar de un corte de conexión.
- **Concurrencia**: un semáforo limita las conversiones simultáneas al número de CPU para acotar la memoria.

## Funciones opcionales (hechas)
- **3MF con cambios de color**: incluye `Metadata/Prusa_Slicer_custom_gcode_per_print_z.xml` (tipo 0, `M600`) y `Metadata/custom_gcode_per_layer.xml` (Bambu/Orca) con `print_z/top_z` = Z de la capa donde empieza el nuevo filamento. **No lo he podido probar en un laminador real**: el formato se basa en lo que esos laminadores escriben en sus proyectos. Por eso el `.txt` sigue siendo la referencia.
- **Marco**: es una banda a la altura máxima, de `marcoMm` de ancho. La pieza crece por los cuatro lados.

## Interfaz
- Cada cambio de ajuste vuelve a procesar tras 350 ms sin cambios. Si llegan respuestas fuera de orden, se descartan.
- La tabla de cambios es un fragmento Thymeleaf (`/trabajos/{id}/tabla-cambios`). El resto se pinta con JSON.
- Filamentos por defecto: negro, gris oscuro, gris claro y blanco. Son neutros y funcionan con cualquier imagen.
- «Detectar colores» toma el TD del filamento más parecido de la biblioteca y avisa de que hay que ajustarlo.
- El tema claro u oscuro sigue al sistema y recuerda la elección en `localStorage`.
