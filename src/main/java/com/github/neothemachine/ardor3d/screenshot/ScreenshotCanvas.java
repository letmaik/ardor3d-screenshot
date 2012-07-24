package com.github.neothemachine.ardor3d.screenshot;

import java.awt.image.BufferedImage;
import java.lang.Thread.UncaughtExceptionHandler;

public interface ScreenshotCanvas extends UpdateableCanvas {

	IntDimension getSize();

	BufferedImage takeShot();

	void dispose();

	void addUncaughtExceptionHandler(UncaughtExceptionHandler eh);

}
