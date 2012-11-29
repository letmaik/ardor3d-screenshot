package com.github.neothemachine.ardor3d.screenshot;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import com.google.common.base.Predicate;

public class MeshesLoadedCondition implements Predicate<CanvasState> {
	
	private final Set<File> models;

	public MeshesLoadedCondition(Set<File> models) {
		this.models = new HashSet<File>(models);		
	}
	
	public MeshesLoadedCondition(File model) {
		this.models = new HashSet<File>();
		models.add(model);
	}
	
	@Override
	public boolean apply(CanvasState state) {
		return state.getMeshFiles().containsAll(models);
	}
	
}