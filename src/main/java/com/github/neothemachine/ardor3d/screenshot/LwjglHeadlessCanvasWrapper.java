package com.github.neothemachine.ardor3d.screenshot;

import java.util.concurrent.CountDownLatch;

import com.ardor3d.framework.Canvas;
import com.ardor3d.framework.CanvasRenderer;
import com.ardor3d.framework.DisplaySettings;
import com.ardor3d.framework.Scene;
import com.ardor3d.renderer.Camera;
import com.ardor3d.renderer.RenderContext;
import com.ardor3d.renderer.Renderer;
import com.ardor3d.util.Ardor3dException;

public class LwjglHeadlessCanvasWrapper implements Canvas {

	private final CanvasRenderer canvasRenderer;

	public LwjglHeadlessCanvasWrapper(LwjglHeadlessCanvas canvas) {
		this.canvasRenderer = new LwjglHeadlessCanvasRenderer(canvas);
	}

	@Override
	public void init() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void draw(CountDownLatch latch) {
		throw new UnsupportedOperationException();
	}

	@Override
	public CanvasRenderer getCanvasRenderer() {
		return this.canvasRenderer;
	}

}

class LwjglHeadlessCanvasRenderer implements CanvasRenderer {

	private final LwjglHeadlessCanvas canvas;

	public LwjglHeadlessCanvasRenderer(LwjglHeadlessCanvas canvas) {
		this.canvas = canvas;
	}
	
	@Override
	public Camera getCamera() {
		return this.canvas.getCamera();
	}

	@Override
	public void setCamera(Camera camera) {
		this.canvas.setCamera(camera);
	}

	@Override
	public void init(DisplaySettings settings, boolean doSwap) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean draw() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Renderer getRenderer() {
		return this.canvas.getRenderer();
	}

	@Override
	public Scene getScene() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setScene(Scene scene) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void makeCurrentContext() throws Ardor3dException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void releaseCurrentContext() {
		throw new UnsupportedOperationException();
	}

	@Override
	public RenderContext getRenderContext() {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getFrameClear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setFrameClear(int buffers) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Renderer createRenderer() {
		throw new UnsupportedOperationException();
	}

}