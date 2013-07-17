package com.github.neothemachine.ardor3d.screenshot;

import static org.testng.Assert.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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
import com.ardor3d.image.util.awt.AWTImageLoader;
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

	private final ScreenshotCanvasPool pool;
	
	@Inject
	public ScreenshotTest(ScreenshotCanvasPool pool) {
		this.pool = pool;
		AWTImageLoader.registerLoader();
	}
	
	@AfterMethod
	public void dispose() {
		this.pool.disposeAll();
	}
		
	@Test
	public void testEmpty() throws Throwable {
		
		int count = 10;
		final CountDownLatch l = new CountDownLatch(count);
		List<AsynchTester> testers = new LinkedList<AsynchTester>();
		for (int x=0; x < count; ++x) {
			final int y = x;
			AsynchTester tester = new AsynchTester(new Runnable() {
				@Override
				public void run() {
					BufferedImage i = renderEmpty(new IntDimension(500, 600 + 2*y));
					assertEquals(i.getWidth(), 500);
					assertEquals(i.getHeight(), 600 + 2*y);
				}
			}, l);
			testers.add(tester);
			tester.start();
		}
		l.await();
		AsynchTester.rethrow(testers);
	}
	
	@Test
	public void testModel() throws Throwable {
		
		int count = 5;
		final CountDownLatch l = new CountDownLatch(count);
		List<AsynchTester> testers = new LinkedList<AsynchTester>();
		for (int x=0; x < count; ++x) {
			final int y = x;
			AsynchTester tester = new AsynchTester(new Runnable() {
				@Override
				public void run() {
					BufferedImage image = renderScene(new IntDimension(500, 600 + 2*y));
					assertEquals(image.getWidth(), 500);
					assertEquals(image.getHeight(), 600 + 2*y);
					try {
						ImageIO.write(image, "png", new File("test" + y + ".png"));
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			},l);
			testers.add(tester);
			tester.start();
		}
		l.await();
		AsynchTester.rethrow(testers);
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
		
		ScreenshotCanvas canvas = pool.getCanvas(size);
		BufferedImage image = canvas.takeShot();
		pool.returnCanvas(canvas);
		
		return image;
	}
	
	private BufferedImage renderScene(IntDimension size) {
		final File model = getResource("table/table.dae");
		final ModelScene scene = new ModelScene();
		final ScreenshotCanvas canvas = pool.getCanvas(size);

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
				scene.loadMesh(model, root);
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
		
		final Texture uvTexture;

    	try {
			uvTexture = TextureManager.loadFromImage(
					AWTImageLoader.makeArdor3dImage(ImageIO.read(texture), false),
					Texture.MinificationFilter.Trilinear
					);
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		Predicate<ModelScene> meshLoadedCondition = new SingleMeshLoadedCondition(model);
		
		Pair<ScreenshotCanvas, ModelScene> pair = 
				pool.getCanvasIfMatch(size, ModelScene.class, meshLoadedCondition);
		final ScreenshotCanvas canvas;
		final ModelScene scene;
		
		boolean reused;
		
		if (pair == null) {
			reused = false;
			 canvas = pool.getCanvas(size);
			 scene = new ModelScene();
			 
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
					scene.loadMesh(model, root);
					Node meshNode = scene.getMesh(model);
					Node meshGeometry = (Node) meshNode.getChild("mesh1-geometry");
					for (Spatial mesh : meshGeometry.getChildren()) {
						TextureState textureState = new TextureState();
				        textureState.setTexture(uvTexture);
						mesh.setRenderState(textureState);
					}
				}
			});
		} else {
			canvas = pair.getValue0();
			scene = pair.getValue1();
			
			reused = true;
			
			// we can reuse the loaded mesh
			final Node mesh = scene.getMesh(model);
			
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
		}
				
		BufferedImage image = canvas.takeShot();
		
		pool.returnCanvas(canvas, scene);
		
		assertEquals(image.getWidth(), size.getWidth());
		assertEquals(image.getHeight(), size.getHeight());
		
		return new Pair<Boolean, BufferedImage>(reused, image);
	}
	
	private Pair<Boolean,BufferedImage> renderSceneReuseMultiple(IntDimension size, final File model, File texture) {
		
		final Texture uvTexture;

    	try {
			uvTexture = TextureManager.loadFromImage(
					AWTImageLoader.makeArdor3dImage(ImageIO.read(texture), false),
					Texture.MinificationFilter.Trilinear
					);
			
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		Predicate<ModelScene> meshLoaded = new MeshesLoadedCondition(model);	
		Predicate<ModelScene> anyMeshesLoaded = new MeshesLoadedCondition(Collections.<File>emptySet());
		
		/*
		 * 1. if a scene with our mesh exists, use it
		 * 2. otherwise, if any other scene exists, use it and add the model to it
		 * 3. otherwise, use new scene
		 */
						
		boolean reused = false;
		
		final ScreenshotCanvas canvas;
		final ModelScene scene;
		
		Pair<ScreenshotCanvas, ModelScene> pair =
				pool.getCanvasIfMatch(size, ModelScene.class, meshLoaded);
		
		if (pair != null) {
			// 1. if a scene with our mesh exists, use it
			System.out.println("exact match");
			
			canvas = pair.getValue0();
			scene = pair.getValue1();
			
			reused = true;
			
			// we can reuse the loaded mesh
			final Node mesh = scene.getMesh(model);
			
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
					
					for (Entry<File, Node> entry : scene.getMeshes().entrySet()) {
						if (entry.getKey().equals(model)) {
							continue;
						}
						entry.getValue().setTranslation(0, 0, -1000000);
					}
				}
			});
		} else {
			pair = pool.getCanvasIfMatch(size, ModelScene.class, anyMeshesLoaded);
			
			if (pair != null) {
				//2. otherwise, if any other scene exists, use it and add the model to it
				System.out.println("half match");
				
				canvas = pair.getValue0();
				scene = pair.getValue1();
											
				reused = true;
				
				canvas.queueSceneUpdate(new SceneGraphUpdate() {
					@Override
					public void update(Node root) {

						scene.loadMesh(model, root);
						Node newMesh = scene.getMesh(model);
						
						Node meshGeometry = (Node) newMesh.getChild("mesh1-geometry");
						for (Spatial mesh : meshGeometry.getChildren()) {
							TextureState textureState = new TextureState();
					        textureState.setTexture(uvTexture);
							mesh.setRenderState(textureState);
						}
						
						// move all other meshes behind the cam	
						for (Entry<File, Node> entry : scene.getMeshes().entrySet()) {
							if (entry.getKey().equals(model)) {
								continue;
							}
							entry.getValue().setTranslation(0, 0, -1000000);
						}
					}
				});
				
			} else {
				// 3. otherwise, use new scene
				System.out.println("new scene");
				
				canvas = pool.getCanvas(size);
				
				scene = new ModelScene();
				
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
						scene.loadMesh(model, root);
						Node meshNode = scene.getMesh(model);
						Node meshGeometry = (Node) meshNode.getChild("mesh1-geometry");
						for (Spatial mesh : meshGeometry.getChildren()) {
							TextureState textureState = new TextureState();
					        textureState.setTexture(uvTexture);
							mesh.setRenderState(textureState);
						}
					}
				});
			}
		}
		
		BufferedImage image = canvas.takeShot();
				
		pool.returnCanvas(canvas, scene);
		
		assertEquals(image.getWidth(), size.getWidth());
		assertEquals(image.getHeight(), size.getHeight());
		
		return new Pair<Boolean, BufferedImage>(reused, image);
	}
	
	private File getResource(String name) {
		return FileUtils.toFile(getClass().getClassLoader().getResource(name));
	}
}

class AsynchTester {
    private Thread thread;
    private volatile Throwable exc; 

    public AsynchTester(final Runnable runnable, final CountDownLatch latch){
        thread = new Thread(new Runnable(){
            public void run(){
                try {            
                    runnable.run();
                } catch (Throwable e){
                    exc = e;
                } finally {
                	latch.countDown();
                }
            }
        });
    }

    public void start(){
        thread.start();
    }

    public void rethrowErrors() throws Throwable {
        if (exc != null)
            throw exc;
    }
    
    public static void rethrow(List<AsynchTester> testers) throws Throwable {
		for (AsynchTester t : testers) {
			t.rethrowErrors();
		}
    }
}