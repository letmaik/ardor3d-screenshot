package com.github.neothemachine.ardor3d.screenshot;

/**
 * 
 * @author maik
 *
 */
public final class IntDimension {

	private final int width;
	private final int height;

	public IntDimension(int width, int height) {
		this.width = width;
		this.height = height;		
	}
	
	public int getWidth() {
		return this.width;
	}
	
	public int getHeight() {
		return this.height;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + height;
		result = prime * result + width;
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
		IntDimension other = (IntDimension) obj;
		if (height != other.height)
			return false;
		if (width != other.width)
			return false;
		return true;
	}	
}
