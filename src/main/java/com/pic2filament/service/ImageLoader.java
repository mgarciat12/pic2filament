package com.pic2filament.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import com.pic2filament.config.Pic2filamentProperties;
import com.pic2filament.core.image.RgbImage;

/**
 * Decodifica imágenes PNG, JPEG y WebP comprobando el contenido real (no la extensión), el tamaño
 * del fichero y las dimensiones antes de decodificar.
 */
@Component
public class ImageLoader {

    /** Formatos admitidos (nombres de formato de ImageIO). */
    static final Set<String> FORMATS = Set.of("png", "jpeg", "webp");

    /** Lado mayor máximo que se conserva en memoria: más resolución no aporta nada a la rejilla. */
    private final int keepSide;
    private final long maxBytes;
    private final long maxPixels;

    public ImageLoader(Pic2filamentProperties props) {
        this.keepSide = Math.max(1200, props.maxGrid() * 4);
        this.maxBytes = props.maxUploadSize().toBytes();
        this.maxPixels = props.maxImageMegapixels() * 1_000_000L;
    }

    public RgbImage load(byte[] data) {
        if (data == null || data.length == 0) {
            throw Pic2filamentException.invalid("No se ha recibido ninguna imagen.");
        }
        if (data.length > maxBytes) {
            throw tooLarge(maxBytes);
        }
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw unsupported();
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (format.equals("jpg")) {
                    format = "jpeg";
                }
                if (!FORMATS.contains(format)) {
                    throw unsupported();
                }
                reader.setInput(in, true, true);
                long w = reader.getWidth(0), h = reader.getHeight(0);
                if (w * h > maxPixels) {
                    throw Pic2filamentException.invalid(String.format(Locale.ROOT,
                            "La imagen es demasiado grande (%d × %d píxeles). El máximo son %d megapíxeles.", w, h,
                            maxPixels / 1_000_000));
                }
                BufferedImage img = reader.read(0);
                RgbImage rgb = RgbImage.fromBufferedImage(img);
                return shrink(rgb);
            } finally {
                reader.dispose();
            }
        } catch (Pic2filamentException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new Pic2filamentException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "No se ha podido leer la imagen: el fichero está dañado o no es PNG, JPG ni WebP.", e);
        }
    }

    private RgbImage shrink(RgbImage img) {
        int side = Math.max(img.width(), img.height());
        if (side <= keepSide) {
            return img;
        }
        double f = (double) keepSide / side;
        return img.resize(Math.max(1, (int) Math.round(img.width() * f)), Math.max(1, (int) Math.round(img.height() * f)));
    }

    static Pic2filamentException unsupported() {
        return new Pic2filamentException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Formato no admitido. Sube una imagen PNG, JPG o WebP.");
    }

    public static Pic2filamentException tooLarge(long maxBytes) {
        return new Pic2filamentException(HttpStatus.CONTENT_TOO_LARGE,
                "La imagen supera el tamaño máximo permitido (" + human(maxBytes) + ").");
    }

    public static String human(long bytes) {
        DataSize s = DataSize.ofBytes(bytes);
        if (s.toMegabytes() >= 1) {
            return s.toMegabytes() + " MB";
        }
        return s.toKilobytes() + " KB";
    }
}
