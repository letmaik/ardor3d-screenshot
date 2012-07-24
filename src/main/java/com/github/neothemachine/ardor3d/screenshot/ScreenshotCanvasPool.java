package com.github.neothemachine.ardor3d.screenshot;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.inject.Singleton;

import org.javatuples.Pair;

@Singleton
public class ScreenshotCanvasPool {
	
	public interface ScreenshotCanvasFactory {
		ScreenshotCanvas create(IntDimension size);
	}

	public interface Condition {
		boolean equals(Object that);
	}
	

	private final ScreenshotCanvasFactory factory;
	private final List<Pair<ScreenshotCanvas,Set<Condition>>> inUse = new LinkedList<Pair<ScreenshotCanvas,Set<Condition>>>();
	private final List<Pair<ScreenshotCanvas,Set<Condition>>> unused = new LinkedList<Pair<ScreenshotCanvas,Set<Condition>>>();
	
	public ScreenshotCanvasPool(ScreenshotCanvasFactory factory) {
		this.factory = factory;
	}
	
	public synchronized ScreenshotCanvas getCanvas(IntDimension size) {
		
		
		
	}
	
	public synchronized Pair<ScreenshotCanvas,Set<Condition>> getCanvas(IntDimension size, Set<Condition> conditions) {
		return null;
		
	}
	
	public synchronized void returnCanvas(ScreenshotCanvas canvas) {
		
	}
	
	public synchronized void returnCanvas(ScreenshotCanvas canvas, Set<Condition> conditions) {
		
	}
	
	public synchronized void disposeAll() {
		
	}
	
}
