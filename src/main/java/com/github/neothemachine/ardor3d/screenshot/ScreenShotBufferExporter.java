package com.github.neothemachine.ardor3d.screenshot;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import com.ardor3d.image.ImageDataFormat;
import com.ardor3d.util.screen.ScreenExportable;

/**
 * Alternative implementation of the built-in ScreenShotImageExporter which
 * holds the screenshot in a BufferedImage instead of writing it to a file.
 * 
 * The main purpose for creating this class are performance reasons.
 * 
 * @author maik
 * @see com.ardor3d.image.util.ScreenShotImageExporter
 *
 */
public class ScreenShotBufferExporter implements ScreenExportable {

    private BufferedImage lastImage;

    public void export(final ByteBuffer data, final int width, final int height) {
        final BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        int index, r, g, b, a;
        int argb;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                index = 4 * ((height - y - 1) * width + x);
                r = data.get(index + 0);
                g = data.get(index + 1);
                b = data.get(index + 2);

                argb = ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);

                a = data.get(index + 3);
                argb |= (a & 0xFF) << 24;
                
                img.setRGB(x, y, argb);
            }
        }
        
        lastImage = img;
    }

    public ImageDataFormat getFormat() {
    	return ImageDataFormat.RGBA;
    }
    
    public BufferedImage getLastImage() {
    	return lastImage;
    }
}

