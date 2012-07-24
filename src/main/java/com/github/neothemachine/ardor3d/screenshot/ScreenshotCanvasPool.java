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

import com.ardor3d.scenegraph.Node;
import com.github.neothemachine.ardor3d.screenshot.UpdateableCanvas.SceneGraphUpdate;
import com.google.inject.BindingAnnotation;

@Singleton
public class ScreenshotCanvasPool {

	static {
		System.setProperty("ardor3d.useMultipleContexts", "true");
	}

	public interface ScreenshotCanvasFactory {
		ScreenshotCanvas create(IntDimension size);
	}

	// at the moment all conditions are joined with AND
	public interface Condition {
		boolean equals(Object that);
	}

	private final ScreenshotCanvasFactory factory;
	
	// only LwjglAwtCanvas and JoglAwtCanvas support multiple windows/canvases
	@BindingAnnotation
	@Retention(RetentionPolicy.RUNTIME)
	public @interface MaxCanvases {}
	
	private final int maxCanvases;

	private final Map<ScreenshotCanvas, Set<Condition>> unused = new HashMap<ScreenshotCanvas, Set<Condition>>();

	private final Set<ScreenshotCanvas> inUse = new HashSet<ScreenshotCanvas>();

	@Inject
	public ScreenshotCanvasPool(ScreenshotCanvasFactory factory, @MaxCanvases int maxCanvases) {
		this.factory = factory;
		this.maxCanvases = maxCanvases;
	}

	public synchronized ScreenshotCanvas getCanvas(IntDimension size) {

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

		List<ScreenshotCanvas> sizeMatchCanvases = new LinkedList<ScreenshotCanvas>();
		for (ScreenshotCanvas canvas : this.unused.keySet()) {
			if (canvas.getSize().equals(size)) {
				sizeMatchCanvases.add(canvas);
			}
		}
		
		if (sizeMatchCanvases.size() > 0) {
			ScreenshotCanvas canvas = sizeMatchCanvases.get(0);
			this.inUse.add(canvas);
			this.unused.remove(canvas);
			return canvas;
		}
		
		if (unusedCount + usedCount == maxCanvases) {
			ScreenshotCanvas c = this.unused.keySet().iterator().next();
			c.dispose();
			this.unused.remove(c);
		}
		
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

	public synchronized Pair<ScreenshotCanvas, Set<Condition>> getCanvas(
			IntDimension size, Set<Condition> preferredConditions) {

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

		List<ScreenshotCanvas> sizeMatchCanvases = new LinkedList<ScreenshotCanvas>();
		for (ScreenshotCanvas canvas : this.unused.keySet()) {
			if (canvas.getSize().equals(size)) {
				sizeMatchCanvases.add(canvas);
			}
		}
		
		List<ScreenshotCanvas> conditionMatchCanvases = new LinkedList<ScreenshotCanvas>();
		for (ScreenshotCanvas canvas : sizeMatchCanvases) {
			Set<Condition> conditions = this.unused.get(canvas);
			if (conditions.containsAll(preferredConditions)) {
				conditionMatchCanvases.add(canvas);
			}
		}
		
		if (conditionMatchCanvases.size() > 0) {
			ScreenshotCanvas canvas = conditionMatchCanvases.get(0);
			Set<Condition> conditions = this.unused.get(canvas);
			this.inUse.add(canvas);
			this.unused.remove(canvas);
			return new Pair<ScreenshotCanvas, Set<Condition>>(canvas, conditions);
		}
		
		if (sizeMatchCanvases.size() > 0) {
			ScreenshotCanvas canvas = sizeMatchCanvases.get(0);
			clearSceneGraph(canvas);
			this.inUse.add(canvas);
			this.unused.remove(canvas);
			return new Pair<ScreenshotCanvas, Set<Condition>>(canvas, new HashSet<Condition>());
		}
		
		if (unusedCount + usedCount == maxCanvases) {
			ScreenshotCanvas c = this.unused.keySet().iterator().next();
			c.dispose();
			this.unused.remove(c);
		}
		
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
		
		return new Pair<ScreenshotCanvas, Set<Condition>>(canvas, new HashSet<Condition>());
	}

	public synchronized void returnCanvas(ScreenshotCanvas canvas) {

		if (!this.inUse.contains(canvas)) {
			throw new RuntimeException("Canvas wasn't in use");
		}

		clearSceneGraph(canvas);

		this.inUse.remove(canvas);
		this.unused.put(canvas, new HashSet<Condition>());
		this.notifyAll();
	}

	public synchronized void returnCanvas(ScreenshotCanvas canvas,
			Set<Condition> conditions) {

		if (!this.inUse.contains(canvas)) {
			throw new RuntimeException("Canvas wasn't in use");
		}

		this.inUse.remove(canvas);
		this.unused.put(canvas, new HashSet<Condition>(conditions));
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
