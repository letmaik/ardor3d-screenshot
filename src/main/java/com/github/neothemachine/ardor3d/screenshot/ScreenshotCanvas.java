package com.github.neothemachine.ardor3d.screenshot;

import java.awt.image.BufferedImage;

public interface ScreenshotCanvas extends UpdateableCanvas {

	IntDimension getSize();
	
	BufferedImage takeShot();
	
	void dispose();
	
}
