package com.github.neothemachine.ardor3d.screenshot;

import java.io.File;
import java.net.MalformedURLException;

import com.ardor3d.util.resource.ResourceLocator;
import com.ardor3d.util.resource.ResourceSource;
import com.ardor3d.util.resource.URLResourceSource;

/**
 * This resource locator is kind of a special locator because it always
 * returns the same resource, no matter which resource name is given.
 * 
 * It is used to directly load a single mesh file without specifying any
 * folder where all meshes reside (which is common for games). This is useful
 * when just meshes are loaded which don't reference any other files like
 * textures etc. (e.g. OpenCTM format).   
 * 
 * @author maik
 *
 */
public class ConstantResourceLocator implements ResourceLocator {

	private final File path;

	public ConstantResourceLocator(File path) {
		this.path = path;		
	}
	
	@Override
	public ResourceSource locateResource(String resourceName) {
		try {
			return new URLResourceSource(path.toURI().toURL());
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

}
