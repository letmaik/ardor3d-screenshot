package com.github.neothemachine.ardor3d.screenshot;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import com.ardor3d.extension.model.collada.jdom.ColladaImporter;
import com.ardor3d.framework.Canvas;
import com.ardor3d.light.PointLight;
import com.ardor3d.math.ColorRGBA;
import com.ardor3d.math.Vector3;
import com.ardor3d.renderer.Camera;
import com.ardor3d.renderer.state.LightState;
import com.ardor3d.renderer.state.ZBufferState;
import com.ardor3d.scenegraph.Node;
import com.ardor3d.util.resource.SimpleResourceLocator;

public class ModelScene {
	
	private File model;

	public ModelScene(File model) {
		this.model = model;
	}

	public void initCanvas(Canvas canvas) {
		Camera cam = canvas.getCanvasRenderer().getCamera();
		cam.setLocation(0, 0, 5);
	}

	public void initScene(Node root) {
		
        final ZBufferState buf = new ZBufferState();
        buf.setEnabled(true);
        buf.setFunction(ZBufferState.TestFunction.LessThanOrEqualTo);
        root.setRenderState(buf);

        Node mesh;
		try {
			mesh = loadMesh(this.model);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		mesh.setTranslation(-1, -1, 0);
        
        final PointLight light = new PointLight();
        light.setLocation(new Vector3(0, 100, 100));
        light.setEnabled(true);
        
        final LightState lightState = new LightState();
        lightState.setGlobalAmbient(ColorRGBA.WHITE);
        lightState.setEnabled(true);
        lightState.attach(light);
        root.setRenderState(lightState);
        
        root.attachChild(mesh);
	}

	private static Node loadMesh(File model) throws IOException {
		URL modelUrlDir;
		try {
			modelUrlDir = new URL("file:" + model.getParent());
		} catch (MalformedURLException e) {
			throw new RuntimeException(e);
		}
        SimpleResourceLocator modelLocator;
		try {
			modelLocator = new SimpleResourceLocator(modelUrlDir);
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
        
        ColladaImporter importer = new ColladaImporter();
        importer.setModelLocator(modelLocator);
        importer.setTextureLocator(modelLocator);
		
		return importer.load(model.getName()).getScene();
	}
}
