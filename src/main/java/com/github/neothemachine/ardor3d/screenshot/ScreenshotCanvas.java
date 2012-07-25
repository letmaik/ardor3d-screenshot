package com.github.neothemachine.ardor3d.screenshot;

import java.awt.image.BufferedImage;
import java.lang.Thread.UncaughtExceptionHandler;

public interface ScreenshotCanvas extends UpdateableCanvas {

	IntDimension getSize();

	BufferedImage takeShot();

	/**
	 * Destroy canvas resources.
	 * 
	 * If the canvas isn't managed by a ScreenshotCanvasPool where
	 * it is disposed by calling disposeAll() on the pool, then before
	 * calling dispose(), you must call 
	 * com.ardor3d.util.ContextGarbageCollector#doFinalCleanup
	 * manually. Otherwise, the pool calls this method.
	 * 
	 * The canvas implementation of dispose() must render one last
	 * empty frame before destroying itself. See 
	 * {@link com.ardor3d.util.ContextGarbageCollector#doFinalCleanup} for
	 * an explanation.
	 */
	void dispose();

	void addUncaughtExceptionHandler(UncaughtExceptionHandler eh);

}
