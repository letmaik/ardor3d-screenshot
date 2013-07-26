package com.github.neothemachine.ardor3d.screenshot;

import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import com.ardor3d.framework.Canvas;
import com.ardor3d.framework.DisplaySettings;
import com.ardor3d.framework.Scene;
import com.ardor3d.image.util.awt.AWTImageLoader;
import com.ardor3d.intersection.PickResults;
import com.ardor3d.math.Ray3;
import com.ardor3d.renderer.Renderer;
import com.ardor3d.scenegraph.Node;
import com.ardor3d.util.ContextGarbageCollector;
import com.ardor3d.util.TextureManager;
import com.ardor3d.util.screen.ScreenExporter;

public class MinimalHeadlessTest implements Scene {

	private final Node root = new Node();
	private final ScreenShotBufferExporter screenShotExp = new ScreenShotBufferExporter();

//	static {
//		System.setProperty("ardor3d.useMultipleContexts", "true");
//	}

	@Test
	public void testHeadless() throws IOException, InterruptedException {

		AWTImageLoader.registerLoader();
		
		final DisplaySettings settings1 = new DisplaySettings(400, 400, 24, 1,
				8, 8, 0, 0, false, false);
		final DisplaySettings settings2 = new DisplaySettings(410, 400, 24, 1,
				8, 8, 0, 0, false, false);
		final DisplaySettings settings3 = new DisplaySettings(420, 400, 24, 1,
				8, 8, 0, 0, false, false);

		LwjglHeadlessCanvas[] canvases = {
				new LwjglHeadlessCanvas(settings1, this),
				new LwjglHeadlessCanvas(settings2, this),
				new LwjglHeadlessCanvas(settings3, this) 
				};		

		for (int x = 0; x < 10; x++) {
			
			LwjglHeadlessCanvas canvas = canvases[x % 3];
			Canvas canvasWrapper = new LwjglHeadlessCanvasWrapper(canvas);

			File model = getResource("table/table.dae");
			ModelScene scene = new ModelScene();
			scene.initScene(root);
			scene.loadMesh(model, root);
			scene.initCanvas(canvasWrapper);
			root.updateGeometricState(0);

			canvas.draw();

			ScreenExporter.exportCurrentScreen(canvasWrapper
					.getCanvasRenderer().getRenderer(), screenShotExp);

			ImageIO.write(screenShotExp.getLastImage(), "png", new File("testm"
					+ x + ".png"));

			root.detachAllChildren();
			
			// why do I need this??
			TextureManager.cleanAllTextures(null, null);
			
			
			
			// simulate that we need a new resolution
			if (Math.random() > 0.5) {
				// kill the old canvas
				canvas.cleanup();
				
				// create new canvas
				final DisplaySettings settings = new DisplaySettings(420+x, 400, 24, 1,
						8, 8, 0, 0, false, false);
				canvases[x % 3] = new LwjglHeadlessCanvas(settings, this);
				
				System.out.println("old canvas killed and new canvas spawned");
			}
		}	
		
		
	}

	@Override
	public boolean renderUnto(Renderer renderer) {
		ContextGarbageCollector.doRuntimeCleanup(renderer);
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
