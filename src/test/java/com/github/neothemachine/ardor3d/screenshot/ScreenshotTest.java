package com.github.neothemachine.ardor3d.screenshot;

import static org.testng.Assert.assertEquals;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import javax.imageio.ImageIO;
import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ardor3d.framework.Canvas;
import com.ardor3d.image.util.AWTImageLoader;
import com.ardor3d.math.ColorRGBA;
import com.ardor3d.scenegraph.Node;
import com.github.neothemachine.ardor3d.screenshot.UpdateableCanvas.CanvasUpdate;
import com.github.neothemachine.ardor3d.screenshot.UpdateableCanvas.SceneGraphUpdate;

/**
 * run with -Djava.library.path=target/natives
 * 
 * @author maik
 *
 */
@Guice(modules = {LwjglModule.class})
public class ScreenshotTest {

	private ScreenshotCanvasPool pool;
	
	@Inject
	public ScreenshotTest(ScreenshotCanvasPool pool) {
		this.pool = pool;
		AWTImageLoader.registerLoader();
	}
	
	@AfterClass
	public void dispose() {
		this.pool.disposeAll();
	}
	
	@Test
	public void testEmpty() throws InterruptedException {
		
		int count = 3;
		final CountDownLatch l = new CountDownLatch(count);
		for (int x=0; x < count; ++x) {
			final int y = x;
			new Thread(new Runnable() {
				@Override
				public void run() {
					renderEmpty(new IntDimension(500, 600 + y));
					l.countDown();
				}
			}).start();
		}
		l.await();
	}
	
	@Test
	public void testModel() throws InterruptedException {
		
		int count = 3;
		final CountDownLatch l = new CountDownLatch(count);
		for (int x=0; x < count; ++x) {
			final int y = x;
			new Thread(new Runnable() {
				@Override
				public void run() {
					renderScene(new IntDimension(500, 600 /*+ y*/));
					l.countDown();
				}
			}).start();
		}
		l.await();
	}
	
	private void renderEmpty(IntDimension size) {
		ScreenshotCanvas canvas = pool.getCanvas(size);
		BufferedImage image = canvas.takeShot();
		pool.returnCanvas(canvas);
		
		assertEquals(image.getWidth(), size.getWidth());
		assertEquals(image.getHeight(), size.getHeight());
	}
	
	private void renderScene(IntDimension size) {
		File model = getResource("table/table.dae");
		final ModelScene scene = new ModelScene(model);
		ScreenshotCanvas canvas = pool.getCanvas(size);
		canvas.queueCanvasUpdate(new CanvasUpdate() {
			@Override
			public void update(Canvas canvas) {
				scene.initCanvas(canvas);
			}
		});
		canvas.queueSceneUpdate(new SceneGraphUpdate() {
			@Override
			public void update(Node root) {
				scene.initScene(root);
			}
		});
		BufferedImage image = canvas.takeShot();
		try {
			ImageIO.write(image, "png", new File("test.png"));
		} catch (IOException e) {
		}
		pool.returnCanvas(canvas);
		
		assertEquals(image.getWidth(), size.getWidth());
		assertEquals(image.getHeight(), size.getHeight());
	}
	
	private File getResource(String name) {
		return FileUtils.toFile(getClass().getClassLoader().getResource(name));
	}
}