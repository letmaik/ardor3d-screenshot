package com.github.neothemachine.ardor3d.screenshot;

import java.io.File;

import com.google.common.base.Predicate;

public class SingleMeshLoadedCondition implements Predicate<ModelScene> {
	
	private final File model;

	public SingleMeshLoadedCondition(File model) {
		this.model = model;		
	}
	
	@Override
	public boolean apply(ModelScene scene) {
		return scene.getMeshCount() == 1 && scene.isMeshLoaded(model);
	}
	
}