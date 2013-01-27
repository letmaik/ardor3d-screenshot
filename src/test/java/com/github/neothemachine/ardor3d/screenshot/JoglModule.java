package com.github.neothemachine.ardor3d.screenshot;

import com.github.neothemachine.ardor3d.screenshot.ScreenshotCanvas.Samples;
import com.github.neothemachine.ardor3d.screenshot.ScreenshotCanvasPool.MaxCanvases;
import com.github.neothemachine.ardor3d.screenshot.ScreenshotCanvasPool.ScreenshotCanvasFactory;
import com.google.inject.AbstractModule;
import com.google.inject.assistedinject.FactoryModuleBuilder;

public class JoglModule extends AbstractModule {
	@Override
	protected void configure() {
		install(new FactoryModuleBuilder().implement(ScreenshotCanvas.class,
				JoglNewtScreenshotCanvas.class).build(
				ScreenshotCanvasFactory.class));
		bindConstant().annotatedWith(MaxCanvases.class).to(2);
		bindConstant().annotatedWith(Samples.class).to(4);
	}
}