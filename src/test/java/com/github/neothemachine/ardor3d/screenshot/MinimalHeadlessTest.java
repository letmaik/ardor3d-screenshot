package com.github.neothemachine.ardor3d.screenshot;

import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import com.ardor3d.framework.Canvas;
import com.ardor3d.framework.DisplaySettings;
import com.ardor3d.framework.Scene;
import com.ardor3d.image.util.AWTImageLoader;
import com.ardor3d.intersection.PickResults;
import com.ardor3d.math.Ray3;
import com.ardor3d.renderer.Renderer;
import com.ardor3d.scenegraph.Node;
import com.ardor3d.util.screen.ScreenExporter;

public class MinimalHeadlessTest implements Scene {

    private final Node root = new Node();
    private final ScreenShotBufferExporter screenShotExp = new ScreenShotBufferExporter();

    @Test
    public void testHeadless() throws IOException, InterruptedException {

        AWTImageLoader.registerLoader();

        final DisplaySettings settings = new DisplaySettings(400, 400, 24, 1,
                8, 8, 0, 0, false, false);

        for (int x = 0; x < 5; x++) {

            LwjglHeadlessCanvas canvas = new LwjglHeadlessCanvas(settings, this);
            Canvas canvasWrapper = new LwjglHeadlessCanvasWrapper(canvas);

            File model = getResource("table/table.dae");
            ModelScene scene = new ModelScene(model);
            scene.initScene(root);
            scene.initCanvas(canvasWrapper);
            root.updateGeometricState(0);

            canvas.draw();

            ScreenExporter.exportCurrentScreen(canvasWrapper
                    .getCanvasRenderer().getRenderer(), screenShotExp);

            ImageIO.write(screenShotExp.getLastImage(), "png", new File("testm"
                    + x + ".png"));

            canvas.cleanup();
        }
    }

    @Override
    public boolean renderUnto(Renderer renderer) {
        root.draw(renderer);
        renderer.renderBuckets();
        return true;
    }

    @Override
    public PickResults doPick(Ray3 pickRay) {
        throw new UnsupportedOperationException();
    }

    private File getResource(String name) {
        return FileUtils.toFile(getClass().getClassLoader().getResource(name));
    }

}
