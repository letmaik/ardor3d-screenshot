package com.github.neothemachine.ardor3d.screenshot;

import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.javatuples.Pair;

import com.ardor3d.framework.Canvas;
import com.ardor3d.scenegraph.Node;
import com.ardor3d.util.ContextGarbageCollector;
import com.github.neothemachine.ardor3d.screenshot.UpdateableCanvas.CanvasUpdate;
import com.github.neothemachine.ardor3d.screenshot.UpdateableCanvas.SceneGraphUpdate;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Maps;
import com.google.inject.BindingAnnotation;

@Singleton
public class ScreenshotCanvasPool<T> {

	// TODO needed?
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

	private final Map<ScreenshotCanvas, T> unused = new HashMap<ScreenshotCanvas, T>();

	private final Set<ScreenshotCanvas> inUse = new HashSet<ScreenshotCanvas>();

	private final T initialState;

	@Inject
	public ScreenshotCanvasPool(ScreenshotCanvasFactory factory, @MaxCanvases int maxCanvases, T initialState) {
		this.factory = factory;
		this.maxCanvases = maxCanvases;
		this.initialState = initialState;
	}

	/**
	 * Gets a canvas in initial state.
	 * 
	 * @param size
	 * @return
	 */
	public synchronized Pair<ScreenshotCanvas,T> getCanvas(final IntDimension size) {

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
				return new Pair<ScreenshotCanvas, T>(canvas, initialState);
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
					return new Pair<ScreenshotCanvas, T>(canvas, initialState);
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
		
		return new Pair<ScreenshotCanvas, T>(canvas, initialState);	
	}

	
	/**
	 * Returns a canvas where the condition is satisfied, or null otherwise.
	 * 
	 * @param size
	 * @param condition
	 * @return
	 */
	public synchronized Pair<ScreenshotCanvas, T> 
		getCanvasIfMatch(IntDimension size, Predicate<T> condition) {

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
		 * 1. Canvas with matching size and conditions
		 * 2. Resizeable canvas with matching conditions
		 */
		
		List<ScreenshotCanvas> sizeMatchCanvases = new LinkedList<ScreenshotCanvas>();
		for (ScreenshotCanvas canvas : this.unused.keySet()) {
			if (canvas.getSize().equals(size)) {
				sizeMatchCanvases.add(canvas);
			}
		}
		
		// 1. Canvas with matching size and conditions
		for (ScreenshotCanvas canvas : sizeMatchCanvases) {
			T state = this.unused.get(canvas);
			if (condition.apply(state)) {
				this.inUse.add(canvas);
				this.unused.remove(canvas);
				return new Pair<ScreenshotCanvas, T>(canvas, state);
			}
		}
			
		// 2. Resizable canvas with size changed and matching conditions
		for (ScreenshotCanvas canvas : this.unused.keySet()) {
			if (canvas instanceof ResizableCanvas) {
				T state = this.unused.get(canvas);
				if (condition.apply(state)) {
					((ResizableCanvas) canvas).setSize(size);
					this.inUse.add(canvas);
					this.unused.remove(canvas);
					return new Pair<ScreenshotCanvas, T>(canvas, state);
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
		this.unused.put(canvas, initialState);
		this.notifyAll();
	}

	public synchronized void returnCanvas(ScreenshotCanvas canvas,
			T newState) {

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
		ContextGarbageCollector.doFinalCleanup(null);
		for (ScreenshotCanvas c : this.unused.keySet()) {
			c.dispose();
		}		
		this.unused.clear();
	}

}
