package com.github.neothemachine.ardor3d.screenshot;

import static org.testng.Assert.assertEquals;

import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;

import javax.inject.Inject;

import org.testng.annotations.AfterClass;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ardor3d.framework.Canvas;
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
	}
	
	@AfterClass
	public void dispose() {
		this.pool.disposeAll();
	}
	
	@Test
	public void test() throws InterruptedException {
		
		final CountDownLatch l = new CountDownLatch(5);
		for (int x=0; x<5; ++x) {
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
	
	private void renderEmpty(IntDimension size) {
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
	
}


