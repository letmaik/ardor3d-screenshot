package com.github.neothemachine.ardor3d.screenshot;

import static org.testng.Assert.assertEquals;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import javax.imageio.ImageIO;
import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.mutable.MutableInt;
import org.javatuples.Pair;
import org.jodah.concurrentunit.Waiter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ardor3d.framework.Canvas;
import com.ardor3d.image.Image;
import com.ardor3d.image.Texture;
import com.ardor3d.image.util.AWTImageLoader;
import com.ardor3d.math.ColorRGBA;
import com.ardor3d.renderer.state.TextureState;
import com.ardor3d.scenegraph.Mesh;
import com.ardor3d.scenegraph.Node;
import com.ardor3d.scenegraph.Spatial;
import com.ardor3d.util.TextureManager;
import com.github.neothemachine.ardor3d.screenshot.MeshLoadedCondition.LoadedMesh;
import com.github.neothemachine.ardor3d.screenshot.ScreenshotCanvasPool.Condition;
import com.github.neothemachine.ardor3d.screenshot.ScreenshotCanvasPool.ConditionData;
import com.github.neothemachine.ardor3d.screenshot.UpdateableCanvas.CanvasUpdate;
import com.github.neothemachine.ardor3d.screenshot.UpdateableCanvas.SceneGraphUpdate;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * run with -Djava.library.path=target/natives
 * 
 * @author maik
 *
 */
@Guice(modules = {LwjglModule.class})
public class ScreenshotTest {

	private final ScreenshotCanvasPool pool;
	
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
		
//		final Waiter waiter = new Waiter();
		
		int count = 10;
		final CountDownLatch l = new CountDownLatch(count);
		for (int x=0; x < count; ++x) {
			final int y = x;
			new Thread(new Runnable() {
				@Override
				public void run() {
					BufferedImage i = renderEmpty(new IntDimension(500, 600 + 2*y));
//					waiter.assertEquals(i.getWidth(), 500);
//					waiter.assertEquals(i.getHeight(), 600 + y);
					assertEquals(i.getWidth(), 500);
					assertEquals(i.getHeight(), 600 + 2*y);
					l.countDown();
				}
			}).start();
		}
		l.await();
	}
	
	@Test
	public void testModel() throws InterruptedException {
		
		int count = 5;
		final CountDownLatch l = new CountDownLatch(count);
		for (int x=0; x < count; ++x) {
			final int y = x;
			new Thread(new Runnable() {
				@Override
				public void run() {
					BufferedImage image = renderScene(new IntDimension(500, 600 + 2*y));
					assertEquals(image.getWidth(), 500);
					assertEquals(image.getHeight(), 600 + 2*y);
					try {
						ImageIO.write(image, "png", new File("test" + y + ".png"));
					} catch (IOException e) {
						e.printStackTrace();
					}
					l.countDown();
				}
			}).start();
		}
		l.await();
	}
	
	/**
	 * Tests model reuse with the use case of rendering the same model with
	 * same resolution but different textures.
	 */
	@Test
	public void testModelReuse() throws InterruptedException, IOException {
		
		int reuseCount = 0;
		final int count = 5;
		for (int x=0; x < count; ++x) {
			File texture = getResource("table/texture" + (x % 3) + ".jpg");
			Pair<Boolean,BufferedImage> result = renderSceneReuse(new IntDimension(500, 600), texture);
			if (result.getValue0()) {
				reuseCount++;
			}
			ImageIO.write(result.getValue1(), "png", new File("test2" + x + ".png"));
		}
		
		assertEquals(reuseCount, count-1);
	}
	
	private BufferedImage renderEmpty(IntDimension size) {
		ScreenshotCanvas canvas = pool.getCanvas(size);
		BufferedImage image = canvas.takeShot();
		pool.returnCanvas(canvas);
		
		return image;
	}
	
	private BufferedImage renderScene(IntDimension size) {
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
		pool.returnCanvas(canvas);
			
		assertEquals(image.getWidth(), size.getWidth());
		assertEquals(image.getHeight(), size.getHeight());
		
		return image;
	}
	
	private Pair<Boolean,BufferedImage> renderSceneReuse(IntDimension size, File texture) {
		File model = getResource("table/table.dae");
		Condition meshLoaded = new MeshLoadedCondition(model);
		Pair<ScreenshotCanvas, Map<Condition, ConditionData>> pair = pool.getCanvas(size, Sets.newHashSet(meshLoaded));
		ScreenshotCanvas canvas = pair.getValue0();
		
		final ModelScene newScene;
		final Node oldMesh;
		
		final Texture uvTexture;

    	try {
			uvTexture = TextureManager.loadFromImage(
					AWTImageLoader.makeArdor3dImage(ImageIO.read(texture), false),
					Texture.MinificationFilter.Trilinear
					);
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}  	
		
		boolean reused = false;
		if (pair.getValue1().containsKey(meshLoaded)) {
			reused = true;
			newScene = null;
			
			// we can reuse the loaded mesh
			MeshLoadedCondition.LoadedMesh data = (LoadedMesh) pair.getValue1().get(meshLoaded);
			oldMesh = data.getMesh();
			
			canvas.queueSceneUpdate(new SceneGraphUpdate() {
				@Override
				public void update(Node root) {
					// exchange texture
					// we have a scene here actually, not a single Mesh object
					Node meshGeometry = (Node) oldMesh.getChild("mesh1-geometry");
					for (Spatial mesh : meshGeometry.getChildren()) {
						TextureState textureState = new TextureState();
				        textureState.setTexture(uvTexture);
						mesh.setRenderState(textureState);
					}
				}
			});
			
		} else {
			oldMesh = null;
			newScene = new ModelScene(model);
			
			canvas.queueCanvasUpdate(new CanvasUpdate() {
				@Override
				public void update(Canvas canvas) {
					newScene.initCanvas(canvas);
				}
			});
			canvas.queueSceneUpdate(new SceneGraphUpdate() {
				@Override
				public void update(Node root) {
					newScene.initScene(root);
					Node scene = newScene.getLoadedMesh();
					Node meshGeometry = (Node) scene.getChild("mesh1-geometry");
					for (Spatial mesh : meshGeometry.getChildren()) {
						TextureState textureState = new TextureState();
				        textureState.setTexture(uvTexture);
						mesh.setRenderState(textureState);
					}
				}
			});
		}

		BufferedImage image = canvas.takeShot();

		Node currentMesh = oldMesh != null ? oldMesh : newScene.getLoadedMesh();
		
		Map<Condition, ConditionData> conditions = new HashMap<Condition, ConditionData>();
		conditions.put(meshLoaded, new MeshLoadedCondition.LoadedMesh(currentMesh));

		pool.returnCanvas(canvas, conditions);
		
		assertEquals(image.getWidth(), size.getWidth());
		assertEquals(image.getHeight(), size.getHeight());
		
		return new Pair<Boolean, BufferedImage>(reused, image);
	}
	
	private File getResource(String name) {
		return FileUtils.toFile(getClass().getClassLoader().getResource(name));
	}
}