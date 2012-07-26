package com.github.neothemachine.ardor3d.screenshot;

import com.github.neothemachine.ardor3d.screenshot.ScreenshotCanvasPool.MaxCanvases;
import com.github.neothemachine.ardor3d.screenshot.ScreenshotCanvasPool.ScreenshotCanvasFactory;
import com.google.inject.Binder;
import com.google.inject.Module;

public class LwjglModule implements Module {
	@Override
	public void configure(Binder binder) {
		binder.bind(ScreenshotCanvasFactory.class).toInstance(new ScreenshotCanvasFactory() {
			@Override
			public ScreenshotCanvas create(IntDimension size) {
				return new LwjglAwtScreenshotCanvas(size);
			}
		});
		binder.bindConstant().annotatedWith(MaxCanvases.class).to(3);
	}
}