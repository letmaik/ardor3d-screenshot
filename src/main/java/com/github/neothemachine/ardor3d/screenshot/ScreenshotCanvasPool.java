package com.github.neothemachine.ardor3d.screenshot;

import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
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
import com.google.common.collect.Maps;
import com.google.inject.BindingAnnotation;

@Singleton
public class ScreenshotCanvasPool {

	// TODO needed?
//	static {
//		System.setProperty("ardor3d.useMultipleContexts", "true");
//	}

	public interface ScreenshotCanvasFactory {
		ScreenshotCanvas create(IntDimension size);
	}

	// at the moment all conditions are joined with AND
	public interface Condition {
		boolean equals(Object that);
	}
	public interface ConditionData {
	}

	private final ScreenshotCanvasFactory factory;
	
	// only LwjglAwtCanvas and JoglAwtCanvas support multiple windows/canvases
	@BindingAnnotation
	@Retention(RetentionPolicy.RUNTIME)
	public @interface MaxCanvases {}
	
	private final int maxCanvases;

	private final Map<ScreenshotCanvas, Map<Condition, ConditionData>> unused = new HashMap<ScreenshotCanvas, Map<Condition, ConditionData>>();

	private final Set<ScreenshotCanvas> inUse = new HashSet<ScreenshotCanvas>();

	@Inject
	public ScreenshotCanvasPool(ScreenshotCanvasFactory factory, @MaxCanvases int maxCanvases) {
		this.factory = factory;
		this.maxCanvases = maxCanvases;
	}

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
			}
		});
		
		return canvas;		
	}

	public synchronized <T extends Condition> Pair<ScreenshotCanvas, Map<Condition, ConditionData>> getCanvas(
			IntDimension size, Set<T> preferredConditions) {

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
		 * 2. Canvas with matching size
		 * 3. Resizeable canvas with size changed
		 * 4. Dispose a canvas (if pool full) and create a new one with the requested size
		 */
		
		List<ScreenshotCanvas> sizeMatchCanvases = new LinkedList<ScreenshotCanvas>();
		for (ScreenshotCanvas canvas : this.unused.keySet()) {
			if (canvas.getSize().equals(size)) {
				sizeMatchCanvases.add(canvas);
			}
		}
		
		// 1. Canvas with matching size and conditions
		for (ScreenshotCanvas canvas : sizeMatchCanvases) {
			Set<Condition> conditions = this.unused.get(canvas).keySet();
			if (conditions.containsAll(preferredConditions)) {
				Map<Condition, ConditionData> conditionData = this.unused.get(canvas);
				this.inUse.add(canvas);
				this.unused.remove(canvas);
				return new Pair<ScreenshotCanvas, Map<Condition, ConditionData>>(canvas, conditionData);
			}
		}
		
		// 2. Canvas with matching size
		if (sizeMatchCanvases.size() > 0) {
			ScreenshotCanvas canvas = this.unused.keySet().iterator().next();
			clearSceneGraph(canvas);
			this.inUse.add(canvas);
			this.unused.remove(canvas);
			return new Pair<ScreenshotCanvas, Map<Condition, ConditionData>>(
					canvas,
					new HashMap<Condition, ConditionData>()
					);
		}
		
		// 3. Resizable canvas with size changed
		for (ScreenshotCanvas canvas : this.unused.keySet()) {
			if (canvas instanceof ResizableCanvas) {
				clearSceneGraph(canvas);
				((ResizableCanvas) canvas).setSize(size);
				this.inUse.add(canvas);
				this.unused.remove(canvas);
				return new Pair<ScreenshotCanvas, Map<Condition, ConditionData>>(
						canvas,
						new HashMap<Condition, ConditionData>()
						);
			}
		}

		// 4. Dispose a canvas (if pool full) ...
		if (usedCount + unusedCount == this.maxCanvases) {
			ScreenshotCanvas canvas = this.unused.keySet().iterator().next();
			canvas.dispose();
			this.unused.remove(canvas);
		}
						
		// 4. ... and create a new one with the requested size
		final ScreenshotCanvas canvas = this.factory.create(size); 
		this.inUse.add(canvas);
		
		canvas.addUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread t, Throwable e) {
				canvas.dispose();
				inUse.remove(canvas);
				unused.remove(canvas);
			}
		});
		
		return new Pair<ScreenshotCanvas, Map<Condition, ConditionData>>(
				canvas, new HashMap<Condition, ConditionData>());
	}

	public synchronized void returnCanvas(ScreenshotCanvas canvas) {

		if (!this.inUse.contains(canvas)) {
			throw new RuntimeException("Canvas wasn't in use");
		}

		this.inUse.remove(canvas);
		this.unused.put(canvas, new HashMap<Condition, ConditionData>());
		this.notifyAll();
	}

	public synchronized void returnCanvas(ScreenshotCanvas canvas,
			Map<Condition, ConditionData> conditions) {

		if (!this.inUse.contains(canvas)) {
			throw new RuntimeException("Canvas wasn't in use");
		}

		this.inUse.remove(canvas);
		this.unused.put(canvas, new HashMap<Condition, ConditionData>(conditions));
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
