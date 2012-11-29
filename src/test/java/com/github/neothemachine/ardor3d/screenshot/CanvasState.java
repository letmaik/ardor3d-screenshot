package com.github.neothemachine.ardor3d.screenshot;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.ardor3d.scenegraph.Node;

public class CanvasState {

	private final Map<File, Node> meshesLoaded;

	public CanvasState(Map<File,Node> meshesLoaded) {
		this.meshesLoaded = meshesLoaded;		
	}
	
	public CanvasState() {
		this.meshesLoaded = new HashMap<File,Node>();
	}

	public boolean isMeshLoaded(File mesh) {
		return this.meshesLoaded.containsKey(mesh);
	}
	
	public int getMeshCount() {
		return this.meshesLoaded.size();
	}
	
	public Node getMesh(File mesh) {
		return this.meshesLoaded.get(mesh);
	}
	
	public Set<File> getMeshFiles() {
		return new HashSet<File>(this.meshesLoaded.keySet());
	}
	
	public Map<File, Node> getMeshes() {
		return new HashMap<File,Node>(this.meshesLoaded);
	}
	
}
