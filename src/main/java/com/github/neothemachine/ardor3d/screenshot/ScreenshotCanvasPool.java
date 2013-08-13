package com.github.neothemachine.ardor3d.screenshot;

import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.javatuples.Pair;

import com.ardor3d.scenegraph.Node;
import com.ardor3d.util.ContextGarbageCollector;
import com.github.neothemachine.ardor3d.screenshot.UpdateableCanvas.SceneGraphUpdate;
import com.google.common.base.Predicate;
import com.google.inject.BindingAnnotation;

@Singleton
public class ScreenshotCanvasPool {

	static {
		System.setProperty("ardor3d.useMultipleContexts", "true");
	}

	public interface ScreenshotCanvasFactory {
		ScreenshotCanvas create(IntDimension size);
	}

	private final ScreenshotCanvasFactory factory;
	
	// only LwjglAwtCanvas and JoglAwtCanvas support multiple windows/canvases
	@BindingAnnotation
	@Retention(RetentionPolicy.RUNTIME)
	public @interface MaxCanvases {}
	
	private final int maxCanvases;

	private final Map<ScreenshotCanvas, Object> unused = new HashMap<ScreenshotCanvas, Object>();

	private final Set<ScreenshotCanvas> inUse = new HashSet<ScreenshotCanvas>();

	@Inject
	public ScreenshotCanvasPool(ScreenshotCanvasFactory factory, @MaxCanvases int maxCanvases) {
		this.factory = factory;
		this.maxCanvases = maxCanvases;
	}

	/**
	 * Gets a canvas in initial state.
	 * 
	 * @param size
	 * @return
	 */
	public synchronized ScreenshotCanvas getCanvas(final IntDimension size) {

		int unusedCount = this.unused.size();
		int usedCount = this.inUse.size();

		// only one canvas can be used at the same time
		// even if the drivers would allow more than one canvas, it wouldn't be any more
		// efficient due to the many OpenGL context switches
		while (usedCount > 0) {
			try {
				this.wait();
			} catch (InterruptedException e) {
			}
			unusedCount = this.unused.size();
			usedCount = this.inUse.size();
		}
		
		/*
		 * Order:
		 * 1. Canvas of matching size
		 * 2. Resizable canvas with size changed
		 * 3. Dispose a canvas (if pool full) and create a new one with the requested size
		 */

		// 1. Canvas of matching size
		for (ScreenshotCanvas canvas : this.unused.keySet()) {
			if (canvas.getSize().equals(size)) {
				clearSceneGraph(canvas);
				this.inUse.add(canvas);
				this.unused.remove(canvas);
				return canvas;
			}
		}
		
		if (this.unused.size() > 0) {
			// 2. Resizable canvas with size changed
			for (ScreenshotCanvas canvas : this.unused.keySet()) {
				if (canvas instanceof ResizableCanvas) {
					clearSceneGraph(canvas);
					((ResizableCanvas) canvas).setSize(size);
					this.inUse.add(canvas);
					this.unused.remove(canvas);
					return canvas;
				}
			}

			// 3. Dispose a canvas (if pool full) ...
			if (usedCount + unusedCount == this.maxCanvases) {
				ScreenshotCanvas canvas = this.unused.keySet().iterator().next();
				canvas.dispose();
				this.unused.remove(canvas);
			}
		}
		
		// 3. ... and create a new one with the requested size
		final ScreenshotCanvas canvas = this.factory.create(size); 
		this.inUse.add(canvas);
		
		canvas.addUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread t, Throwable e) {
				canvas.dispose();
				inUse.remove(canvas);
				unused.remove(canvas);
				synchronized (ScreenshotCanvasPool.this) {
					ScreenshotCanvasPool.this.notifyAll();
				}
			}
		});
		
		return canvas;	
	}

	
	/**
	 * Returns a canvas where the condition is satisfied, or null otherwise.
	 * 
	 * @param size
	 * @param condition
	 * @return
	 */
	public synchronized <T> Pair<ScreenshotCanvas, T> 
		getCanvasIfMatch(IntDimension size, Class<T> type, Predicate<T> condition) {

		int unusedCount = this.unused.size();
		int usedCount = this.inUse.size();

		// only one canvas can be used at the same time
		// even if the drivers would allow more than one canvas, it wouldn't be any more
		// efficient due to the many OpenGL context switches
		while (usedCount > 0) {
			try {
				this.wait();
			} catch (InterruptedException e) {
			}
			unusedCount = this.unused.size();
			usedCount = this.inUse.size();
		}

		/**
		 * Order:
		 * 1. Canvas with matching size and type and conditions
		 * 2. Resizeable canvas with matching type and conditions
		 */
		
		List<ScreenshotCanvas> sizeMatchCanvases = new LinkedList<ScreenshotCanvas>();
		for (ScreenshotCanvas canvas : this.unused.keySet()) {
			if (canvas.getSize().equals(size)) {
				sizeMatchCanvases.add(canvas);
			}
		}
		
		// 1. Canvas with matching size and type and conditions
		for (ScreenshotCanvas canvas : sizeMatchCanvases) {
			Object state = this.unused.get(canvas);
			if (state != null && type.isAssignableFrom(state.getClass())) {
				@SuppressWarnings("unchecked")
				T typedState = (T) state;
				if (condition.apply(typedState)) {
					this.inUse.add(canvas);
					this.unused.remove(canvas);
					return new Pair<ScreenshotCanvas, T>(canvas, typedState);
				}
			}
		}
			
		// 2. Resizable canvas with size changed and matching type and conditions
		for (ScreenshotCanvas canvas : this.unused.keySet()) {
			if (canvas instanceof ResizableCanvas) {
				Object state = this.unused.get(canvas);
				if (state != null && type.isAssignableFrom(state.getClass())) {
					@SuppressWarnings("unchecked")
					T typedState = (T) state;
					if (condition.apply(typedState)) {
						((ResizableCanvas) canvas).setSize(size);
						this.inUse.add(canvas);
						this.unused.remove(canvas);
						return new Pair<ScreenshotCanvas, T>(canvas, typedState);
					}
				}

			}
		}

		return null;
	}

	public synchronized void returnCanvas(ScreenshotCanvas canvas) {

		if (!this.inUse.contains(canvas)) {
			throw new RuntimeException("Canvas wasn't in use");
		}

		this.inUse.remove(canvas);
		this.unused.put(canvas, null);
		this.notifyAll();
	}

	public synchronized void returnCanvas(ScreenshotCanvas canvas,
			Object newState) {

		if (!this.inUse.contains(canvas)) {
			throw new RuntimeException("Canvas wasn't in use");
		}

		this.inUse.remove(canvas);
		this.unused.put(canvas, newState);
		this.notifyAll();
	}
	
	private static void clearSceneGraph(ScreenshotCanvas canvas) {
		canvas.queueSceneUpdate(new SceneGraphUpdate() {
			@Override
			public void update(Node root) {
				root.detachAllChildren();
			}
		});
	}

	public synchronized void disposeAll() {

		if (this.inUse.size() > 0) {
			throw new RuntimeException("At least one canvas still in use");
		}
		for (ScreenshotCanvas c : this.unused.keySet()) {
			c.dispose();
		}		
		this.unused.clear();
	}

}
