package com.github.neothemachine.ardor3d.screenshot;

import com.ardor3d.framework.Canvas;
import com.ardor3d.scenegraph.Node;

public interface UpdateableCanvas {

	public interface SceneGraphUpdate {
		void update(Node root);
	}
	
	public interface CanvasUpdate {
		void update(Canvas canvas);
	}

	void queueSceneUpdate(SceneGraphUpdate update);
	void queueCanvasUpdate(CanvasUpdate update);
	
}
