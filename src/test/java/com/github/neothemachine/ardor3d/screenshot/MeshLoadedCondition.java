package com.github.neothemachine.ardor3d.screenshot;

import java.io.File;

import com.ardor3d.scenegraph.Node;
import com.github.neothemachine.ardor3d.screenshot.ScreenshotCanvasPool.Condition;
import com.github.neothemachine.ardor3d.screenshot.ScreenshotCanvasPool.ConditionData;

public class MeshLoadedCondition implements Condition {
	
	public static final class LoadedMesh implements ConditionData {
		private final Node mesh;

		public LoadedMesh(Node mesh) {
			this.mesh = mesh;
		}
		
		public Node getMesh() {
			return mesh;
		}
	}

	private final File mesh;

	public MeshLoadedCondition(File mesh) {
		this.mesh = mesh;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((mesh == null) ? 0 : mesh.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MeshLoadedCondition other = (MeshLoadedCondition) obj;
		if (mesh == null) {
			if (other.mesh != null)
				return false;
		} else if (!mesh.equals(other.mesh))
			return false;
		return true;
	}
	
}