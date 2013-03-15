package com.github.neothemachine.ardor3d.screenshot;

import static org.testng.Assert.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CountDownLatch;

import javax.imageio.ImageIO;
import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.javatuples.Pair;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import com.ardor3d.framework.Canvas;
import com.ardor3d.image.Texture;
import com.ardor3d.image.util.AWTImageLoader;
import com.ardor3d.renderer.state.TextureState;
import com.ardor3d.scenegraph.Node;
import com.ardor3d.scenegraph.Spatial;
import com.ardor3d.util.TextureManager;
import com.github.neothemachine.ardor3d.screenshot.UpdateableCanvas.CanvasUpdate;
import com.github.neothemachine.ardor3d.screenshot.UpdateableCanvas.SceneGraphUpdate;
import com.google.common.base.Predicate;

/**
 * run with -Djava.library.path=target/natives
 * 
 * @author maik
 *
 */
//@Guice(modules = {LwjglModule.class})
@Guice(modules = {JoglModule.class})
public class ScreenshotTest {

	private final ScreenshotCanvasPool<CanvasState> pool;
	
	@Inject
	public ScreenshotTest(ScreenshotCanvasPool<CanvasState> pool) {
		this.pool = pool;
		AWTImageLoader.registerLoader();
	}
	
	@AfterMethod
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
					try {
						BufferedImage i = renderEmpty(new IntDimension(500, 600 + 2*y));
						assertEquals(i.getWidth(), 500);
						assertEquals(i.getHeight(), 600 + 2*y);
					} catch (Exception e) {
						e.printStackTrace();
						fail(e.getMessage());
					} finally {
						l.countDown();
					}
//					waiter.assertEquals(i.getWidth(), 500);
//					waiter.assertEquals(i.getHeight(), 600 + y);
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
					try {
						BufferedImage image = renderScene(new IntDimension(500, 600 + 2*y));
						assertEquals(image.getWidth(), 500);
						assertEquals(image.getHeight(), 600 + 2*y);
						ImageIO.write(image, "png", new File("test" + y + ".png"));
					} catch (Exception e) {
						e.printStackTrace();
						fail(e.getMessage());
					} finally {
						l.countDown();
					}
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
	
	
	@Test
	public void testMultipleModelReuse() throws InterruptedException, IOException {
		
		File model1 = getResource("lamp/lamp.dae");
		File model2 = getResource("table/table.dae");
		
		// FIXME lamp not visible
		
		int reuseCount = 0;
		final int count = 5;
		for (int x=0; x < count; ++x) {
			File texture = x < 3 ? getResource("lamp/texture" + (x % 3) + ".jpg") : 
				getResource("table/texture" + (x % 3) + ".jpg");
			File model = x < 3 ? model1 : model2;
			Pair<Boolean,BufferedImage> result = renderSceneReuseMultiple(
					new IntDimension(500, 600), model, texture);
			if (result.getValue0()) {
				reuseCount++;
			}
			ImageIO.write(result.getValue1(), "png", new File("test3" + x + ".png"));
		}
		
		assertEquals(reuseCount, count-1);
	}
	
	private BufferedImage renderEmpty(IntDimension size) {
		
		ScreenshotCanvas canvas = pool.getCanvas(size).getValue0();
		BufferedImage image = canvas.takeShot();
		pool.returnCanvas(canvas);
		
		return image;
	}
	
	private BufferedImage renderScene(IntDimension size) {
		File model = getResource("table/table.dae");
		final ModelScene scene = new ModelScene(model);
		ScreenshotCanvas canvas = pool.getCanvas(size).getValue0();

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
		final File model = getResource("table/table.dae");

		Predicate<CanvasState> meshLoadedCondition = new SingleMeshLoadedCondition(model);
		
		Pair<ScreenshotCanvas, CanvasState> pair = 
				pool.getCanvasIfMatch(size, meshLoadedCondition);
		if (pair == null) {
			pair = pool.getCanvas(size);
		}
		
		ScreenshotCanvas canvas = pair.getValue0();
		CanvasState state = pair.getValue1();
		
		final ModelScene newScene;
		
		final Texture uvTexture;

    	try {
			uvTexture = TextureManager.loadFromImage(
					AWTImageLoader.makeArdor3dImage(ImageIO.read(texture), false),
					Texture.MinificationFilter.Trilinear
					);
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}  	
		
		boolean reused;
		if (meshLoadedCondition.apply(state)) {
			reused = true;
			newScene = null;
			
			// we can reuse the loaded mesh
			final Node mesh = state.getMesh(model);
			
			canvas.queueSceneUpdate(new SceneGraphUpdate() {
				@Override
				public void update(Node root) {
					// exchange texture
					// we have a scene here actually, not a single Mesh object
					Node meshGeometry = (Node) mesh.getChild("mesh1-geometry");
					for (Spatial mesh : meshGeometry.getChildren()) {
						TextureState textureState = new TextureState();
				        textureState.setTexture(uvTexture);
						mesh.setRenderState(textureState);
					}
				}
			});
			
		} else {
			reused = false;
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
	
		CanvasState newState;
		if (newScene != null) {
			Map<File,Node> meshesLoaded = new HashMap<File, Node>();
			meshesLoaded.put(model, newScene.getLoadedMesh());
			newState = new CanvasState(meshesLoaded);
		} else {
			newState = state;
		}
		
		pool.returnCanvas(canvas, newState);
		
		assertEquals(image.getWidth(), size.getWidth());
		assertEquals(image.getHeight(), size.getHeight());
		
		return new Pair<Boolean, BufferedImage>(reused, image);
	}
	
	private Pair<Boolean,BufferedImage> renderSceneReuseMultiple(IntDimension size, final File model, File texture) {
		Predicate<CanvasState> meshLoaded = new MeshesLoadedCondition(model);

		/*
		 * 1. if a scene with our mesh exists, use it
		 * 2. otherwise, if any other scene exists, use it and add the model to it
		 * 3. otherwise, use new scene
		 */
		
		Predicate<CanvasState> anyMeshesLoaded = new MeshesLoadedCondition(Collections.<File>emptySet());
		
		boolean freshCanvas = false;
		
		Pair<ScreenshotCanvas, CanvasState> pair =
				pool.getCanvasIfMatch(size, meshLoaded);
		if (pair != null) {
			System.out.println("exact match");
		}
		if (pair == null) {
			pair = pool.getCanvasIfMatch(size, anyMeshesLoaded);
		}
		if (pair == null) {
			freshCanvas = true;
			pair = pool.getCanvas(size);
		}
		
		ScreenshotCanvas canvas = pair.getValue0();
		final CanvasState state = pair.getValue1();
		
		final ModelScene newScene;
		final Node newMesh;
		
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
		// 1. if a scene with our mesh exists, use it
		if (meshLoaded.apply(state)) {
			reused = true;
			newScene = null;
			newMesh = null;
			
			// we can reuse the loaded mesh
			final Node mesh = state.getMesh(model);
			
			canvas.queueSceneUpdate(new SceneGraphUpdate() {
				@Override
				public void update(Node root) {
					// exchange texture
					// we have a scene here actually, not a single Mesh object
					Node meshGeometry = (Node) mesh.getChild("mesh1-geometry");
					for (Spatial mesh : meshGeometry.getChildren()) {
						TextureState textureState = new TextureState();
				        textureState.setTexture(uvTexture);
						mesh.setRenderState(textureState);
					}
					
					// move the mesh we want to the center and all others behind the cam
//					mesh.setTranslation(-1, -1, 0);
					mesh.setTranslation(0, 0, 0);
					
					for (Entry<File, Node> entry : state.getMeshes().entrySet()) {
						if (entry.getKey().equals(model)) {
							continue;
						}
						entry.getValue().setTranslation(0, 0, -1000000);
					}
				}
			});
		
		//2. otherwise, if any other scene exists, use it and add the model to it
		} else if (!freshCanvas) {
			
			System.out.println("half match");
			
			reused = true;
			newScene = null;
			
			try {
				newMesh = ModelScene.loadMesh(model);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			
			canvas.queueSceneUpdate(new SceneGraphUpdate() {
				@Override
				public void update(Node root) {

					root.attachChild(newMesh);					
					newMesh.setTranslation(-1, -1, 0);
					
					Node meshGeometry = (Node) newMesh.getChild("mesh1-geometry");
					for (Spatial mesh : meshGeometry.getChildren()) {
						TextureState textureState = new TextureState();
				        textureState.setTexture(uvTexture);
						mesh.setRenderState(textureState);
					}
					
					// move all other meshes behind the cam	
					for (Entry<File, Node> entry : state.getMeshes().entrySet()) {
						if (entry.getKey().equals(model)) {
							continue;
						}
						entry.getValue().setTranslation(0, 0, -1000000);
					}
				}
			});
			
		// 3. otherwise, use new scene
		} else {
			
			System.out.println("new scene");
			
			newScene = new ModelScene(model);
			newMesh = null;
			
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

		Map<File,Node> newMeshes = state.getMeshes();
		
		if (newScene != null) {
			newMeshes.put(model, newScene.getLoadedMesh());
		} else if (newMesh != null) {
			newMeshes.put(model, newMesh);
		}
		
		CanvasState newState = new CanvasState(newMeshes);
		
		pool.returnCanvas(canvas, newState);
		
		assertEquals(image.getWidth(), size.getWidth());
		assertEquals(image.getHeight(), size.getHeight());
		
		return new Pair<Boolean, BufferedImage>(reused, image);
	}
	
	private File getResource(String name) {
		return FileUtils.toFile(getClass().getClassLoader().getResource(name));
	}
}