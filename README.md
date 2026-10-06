# Mira Music (Fabric 1.21.1)

Reproductor de música de YouTube para Minecraft. **Solo lo escucha el jugador que lo usa** (el audio se reproduce en su propia computadora).
Funciona junto al mod "Sala de Espera Navideña" (`esperajuegos`), pero también sirve solo.

## Comandos (necesitan ser operador)
- `/mira music @a on`  → activa Mira Music
- `/mira music @a off` → lo desactiva (y detiene la música)

## Cómo se usa
- **En la sala de espera:** aparece un cuadrado rojo con audífonos junto al título CHAT. Click → se abre el menú grande. Click otra vez en el cuadrado (o Esc) → vuelve a los juegos.
- **En el juego:** aparece un cuadrado rojo con audífonos abajo a la derecha. Pulsa **M** (se puede cambiar en Controles → Mira Music) para liberar el cursor y haz click en el cuadrado: se abre el mini menú. El **botón azul** lo cierra. El mini menú se queda visible mientras juegas; pulsa **M** de nuevo para volver a usarlo con el mouse.
- **Menú:** caja de arriba = pega un link de YouTube y pulsa **Enter**. Cuadro grande = miniatura y título. Flechas: canción anterior / pausa-play / siguiente. Barra verde = volumen (click, arrastrar o rueda del mouse).
- Cada link que pones se agrega a la lista y se reproduce; al terminar pasa a la siguiente.

## Herramientas externas
Para sacar el audio se usan **yt-dlp**, **ffmpeg** y **deno** (yt-dlp lo exige para YouTube). Se buscan en `config/miramusic/` y en el PATH.
- **Windows:** si faltan, el menú muestra el botón **"Instalar (1 click)"** y las descarga solas en `config/miramusic/` (ffmpeg pesa ~150 MB).
- **Linux / Mac:** instálalas con tu gestor de paquetes (`yt-dlp`, `ffmpeg`, `deno`).
- Si YouTube cambia algo y deja de funcionar, el mod intenta actualizar yt-dlp solo (si está en `config/miramusic/`). Los registros están en `config/miramusic/ytdlp.log` y `ffmpeg.log`.

## Requisitos
Minecraft 1.21.1, Fabric Loader 0.16+, Fabric API. Instalar el mod en el servidor **y** en los clientes.

## Obtener el .jar con GitHub
1. Sube este proyecto a un repositorio (rama `main`).
2. Pestaña **Actions** → "Build mod" → al terminar, en **Artifacts** descarga `miramusic-jar`.
3. (Opcional) Crea un tag `v1.0.0` y el .jar aparece en **Releases**.
