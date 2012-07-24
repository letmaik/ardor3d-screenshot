package com.github.neothemachine.ardor3d.screenshot;

import org.testng.annotations.Test;

import com.github.neothemachine.ardor3d.screenshot.ScreenshotCanvasPool.ScreenshotCanvasFactory;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;

public class ScreenshotTest implements Module {

	@Test
	public void test() {
		Injector injector = Guice.createInjector();
		
		ScreenshotCanvasPool pool = injector.getInstance(ScreenshotCanvasPool.class);
		
	}

	@Override
	public void configure(Binder binder) {
		binder.bind(ScreenshotCanvasFactory.class).toInstance(new ScreenshotCanvasFactory() {
			@Override
			public ScreenshotCanvas create(IntDimension size) {
				return new LwjglAwtScreenshotCanvas(size);
			}
		});		
	}
	
}
