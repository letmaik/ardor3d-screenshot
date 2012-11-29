package com.github.neothemachine.ardor3d.screenshot;

import java.io.File;

import com.google.common.base.Predicate;

public class SingleMeshLoadedCondition implements Predicate<CanvasState> {
	
	private final File model;

	public SingleMeshLoadedCondition(File model) {
		this.model = model;		
	}
	
	@Override
	public boolean apply(CanvasState state) {
		return state.getMeshCount() == 1 && state.isMeshLoaded(model);
	}
	
}