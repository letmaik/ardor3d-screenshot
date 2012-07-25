package com.github.neothemachine.ardor3d.screenshot;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Collection;
import java.util.LinkedList;
import java.util.concurrent.Callable;

import javax.swing.JFrame;

import org.lwjgl.LWJGLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ardor3d.annotation.MainThread;
import com.ardor3d.framework.CanvasRenderer;
import com.ardor3d.framework.DisplaySettings;
import com.ardor3d.framework.FrameHandler;
import com.ardor3d.framework.Scene;
import com.ardor3d.framework.Updater;
import com.ardor3d.framework.lwjgl.LwjglAwtCanvas;
import com.ardor3d.framework.lwjgl.LwjglCanvasRenderer;
import com.ardor3d.intersection.PickResults;
import com.ardor3d.math.Ray3;
import com.ardor3d.renderer.Renderer;
import com.ardor3d.renderer.TextureRendererFactory;
import com.ardor3d.renderer.lwjgl.LwjglTextureRendererProvider;
import com.ardor3d.renderer.queue.RenderBucketType;
import com.ardor3d.renderer.state.ZBufferState;
import com.ardor3d.scenegraph.Node;
import com.ardor3d.util.Constants;
import com.ardor3d.util.ContextGarbageCollector;
import com.ardor3d.util.GameTaskQueue;
import com.ardor3d.util.GameTaskQueueManager;
import com.ardor3d.util.ReadOnlyTimer;
import com.ardor3d.util.Timer;
import com.ardor3d.util.screen.ScreenExporter;
import com.ardor3d.util.stat.StatCollector;

public class LwjglAwtScreenshotCanvasSingleThreaded implements ScreenshotCanvas, Updater, Scene {
	private static final Logger log = LoggerFactory.getLogger(LwjglAwtScreenshotCanvasSingleThreaded.class);	
	
	private final IntDimension size;
		
    private final FrameHandler _frameHandler = new FrameHandler(new Timer());
    private final ScreenShotBufferExporter _screenShotExp = new ScreenShotBufferExporter();

    private LwjglAwtCanvas canvas;
    private JFrame frame;
    private final Node root = new Node();
    
    private boolean isShotRequested = false;
    private final Object shotFinishedMonitor = new Object();    

	public LwjglAwtScreenshotCanvasSingleThreaded(IntDimension size) {
		this.size = size;
    	start(size, 0); // TODO
	}
	
	@Override
	public void addUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
		// not needed here as we don't have a render thread
		// TODO what happens if there's an Exception in the AWTEventQueue Thread? (renderUnto())?
	}
	
	@Override
	public IntDimension getSize() {
		return size;
	}
	
	/**
	 * init isn't used at this point because threading is broken
	 * scene init happens later in user code by queueing
	 * @see http://ardor3d.com/forums/viewtopic.php?f=13&t=1020
	 */
    public void init() {
    }
    
    public void update(final ReadOnlyTimer timer) {
        if (Constants.stats) {
            StatCollector.update();
        }
        
        GameTaskQueueManager.getManager(this).getQueue(GameTaskQueue.UPDATE)
        	.execute();
    }
    
    // called in AWT-EventQueue thread
    public boolean renderUnto(final Renderer renderer) {    	
    	GameTaskQueueManager.getManager(this).getQueue(GameTaskQueue.RENDER)
                .execute(renderer);
    	
    	// necessary because internal ardor3d code relies on this queue
    	// it happens after our own queue so that dispose() works correctly
    	// see http://ardor3d.com/forums/viewtopic.php?f=13&t=1020&p=16253#p16253
    	GameTaskQueueManager.getManager(canvas.getCanvasRenderer().getRenderContext()).
    		getQueue(GameTaskQueue.RENDER).execute(renderer);
        
        // Clean up card garbage such as textures, vbos, etc.
        ContextGarbageCollector.doRuntimeCleanup(renderer);

    	root.draw(renderer);

        if (isShotRequested) {
            // force any waiting scene elements to be rendered.
            renderer.renderBuckets();
            ScreenExporter.exportCurrentScreen(canvas.getCanvasRenderer().getRenderer(), _screenShotExp);
            synchronized (shotFinishedMonitor) {
            	isShotRequested = false;
            	this.shotFinishedMonitor.notifyAll();
			}
        }
        return true;
    }
	
    private void start(IntDimension size, int aaSamples) {

        final DisplaySettings settings = new DisplaySettings(size.getWidth(), size.getHeight(),
        		24, 1, 8, 8, 0, aaSamples, false, false);


        final LwjglCanvasRenderer canvasRenderer = new LwjglCanvasRenderer(this);
        try {
			canvas = new LwjglAwtCanvas(settings, canvasRenderer);
		} catch (LWJGLException e1) {
			throw new RuntimeException(e1);
		}
        frame = new JFrame("OpenGL Canvas");
        frame.add(canvas);
        canvas.setSize(new Dimension(size.getWidth(), size.getHeight()));
        canvas.setVisible(true);
        
        frame.pack();
        frame.setVisible(true);     
        
        
        TextureRendererFactory.INSTANCE.setProvider(new LwjglTextureRendererProvider());


        _frameHandler.addUpdater(this);
        _frameHandler.addCanvas(canvas);
        
		_frameHandler.init();
    }

	@Override
	public void queueSceneUpdate(final SceneGraphUpdate update) {
		GameTaskQueueManager.getManager(this).update(new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				update.update(root);
				root.updateGeometricState(0);
				return null;
			}
		});
	}

	@Override
	public void queueCanvasUpdate(final CanvasUpdate update) {
		GameTaskQueueManager.getManager(this).render(new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				update.update(canvas);
				return null;
			}			
		});
	}

    /**
     * 
     * @return
     * @throws Ardor3DRenderException on unexpected errors
     */
    public synchronized BufferedImage takeShot() {

        // only works after the 2nd frame
        _frameHandler.updateFrame();
        isShotRequested = true;
        _frameHandler.updateFrame();
    	
		synchronized (shotFinishedMonitor) {
        	while (isShotRequested) {
	    		try {
	    			shotFinishedMonitor.wait();
	    		} catch (InterruptedException e) {
	    		}
        	}
		}
		
    	return _screenShotExp.getLastImage();
    }

	@Override
	public void dispose() {
		try {
			// render one last empty frame, as required (see dispose() javadoc)
			this.queueSceneUpdate(new SceneGraphUpdate() {
				@Override
				public void update(Node root) {
					root.detachAllChildren();
				}
			});
			_frameHandler.updateFrame();
	        frame.dispose();

		} catch (Exception e) {
			log.error("Error disposing canvas resources", e);
		}
	}
	
	@Override
	public PickResults doPick(Ray3 pickRay) {
		throw new UnsupportedOperationException();
	}

}
