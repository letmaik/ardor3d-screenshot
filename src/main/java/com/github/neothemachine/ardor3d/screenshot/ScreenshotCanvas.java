package com.github.neothemachine.ardor3d.screenshot;

import java.awt.image.BufferedImage;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.ardor3d.renderer.pass.BasicPassManager;
import com.google.inject.BindingAnnotation;

public interface ScreenshotCanvas extends UpdateableCanvas {
	
	/**
	 * Annotation for samples argument in canvas implementation.
	 *
	 */
	@BindingAnnotation
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Samples {}

	IntDimension getSize();
	
	BufferedImage takeShot();
	
	/**
	 * Run all pending queue actions without taking a screenshot.
	 */
	void runQueues();
	
	BasicPassManager getPassManager();

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

	/**
	 * Listen for any exception thrown by the canvas.
	 * This is a relay for ScreenshotCanvasPool to free its resources (kill broken canvases).
	 * @param eh
	 */
	void addUncaughtExceptionHandler(UncaughtExceptionHandler eh);

}
