package com.github.neothemachine.ardor3d.screenshot;

import java.awt.image.BufferedImage;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

import com.ardor3d.framework.Canvas;
import com.ardor3d.math.ColorRGBA;
import com.ardor3d.renderer.Camera;
import com.ardor3d.renderer.Camera.ProjectionMode;
import com.ardor3d.scenegraph.Node;
import com.github.neothemachine.ardor3d.screenshot.ScreenshotCanvasPool.MaxCanvases;
import com.github.neothemachine.ardor3d.screenshot.ScreenshotCanvasPool.ScreenshotCanvasFactory;
import com.github.neothemachine.ardor3d.screenshot.UpdateableCanvas.CanvasUpdate;
import com.github.neothemachine.ardor3d.screenshot.UpdateableCanvas.SceneGraphUpdate;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;

/**
 * run with -Djava.library.path=target/natives
 * 
 * @author maik
 *
 */
public class ScreenshotTest implements Module {

	private final Injector injector = Guice.createInjector(this);
	
	@Test
	public void test() {
		ScreenshotCanvasPool pool = injector.getInstance(ScreenshotCanvasPool.class);
		
		IntDimension size = new IntDimension(600, 500);
		ScreenshotCanvas canvas = pool.getCanvas(size);
		canvas.queueCanvasUpdate(new CanvasUpdate() {
			@Override
			public void update(Canvas canvas) {
				canvas.getCanvasRenderer().getRenderer().setBackgroundColor(new ColorRGBA(0, 0, 0, 0));
			}
		});
		canvas.queueSceneUpdate(new SceneGraphUpdate() {
			@Override
			public void update(Node root) {
				
			}
		});
		BufferedImage image = canvas.takeShot();
		pool.returnCanvas(canvas);
		
		assertEquals(image.getWidth(), size.getWidth());
		assertEquals(image.getHeight(), size.getHeight());
	}

	@Override
	public void configure(Binder binder) {
		binder.bind(ScreenshotCanvasFactory.class).toInstance(new ScreenshotCanvasFactory() {
			@Override
			public ScreenshotCanvas create(IntDimension size) {
				return new LwjglAwtScreenshotCanvas(size);
			}
		});
		binder.bindConstant().annotatedWith(MaxCanvases.class).to(3);
	}
	
}
