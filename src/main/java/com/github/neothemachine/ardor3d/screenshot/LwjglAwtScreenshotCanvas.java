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

public class LwjglAwtScreenshotCanvas implements ScreenshotCanvas, Updater, Scene, Runnable {
	private static final Logger log = LoggerFactory.getLogger(LwjglAwtScreenshotCanvas.class);	
	
	private final IntDimension size;
	
	private final Collection<UncaughtExceptionHandler> uncaughtExceptionHandlers =
			new LinkedList<UncaughtExceptionHandler>();
	
	private Throwable lastUncaughtException = null;
	
    private final FrameHandler _frameHandler = new FrameHandler(new Timer());
    private final ScreenShotBufferExporter _screenShotExp = new ScreenShotBufferExporter();

    private LwjglAwtCanvas canvas;
    private JFrame frame;
    private final Node root = new Node();
    
    private boolean isInitDone = false;
    private final Object initDoneMonitor = new Object();
    
    private boolean isExitRequested = false;
    private boolean isExitDone = false;
    private final Object exitDoneMonitor = new Object();
    
    private boolean isShotRequested = false;
    private final Object shotRequestedMonitor = new Object();
    private final Object shotFinishedMonitor = new Object();    

	public LwjglAwtScreenshotCanvas(IntDimension size) {
		this.size = size;
    	start(size, 0); // TODO
    	synchronized (initDoneMonitor) {
    		while (!isInitDone) {
				try {
					initDoneMonitor.wait();
				} catch (InterruptedException e) {}
    		}
		}
	}
	
	@Override
	public void addUncaughtExceptionHandler(UncaughtExceptionHandler eh) {
		this.uncaughtExceptionHandlers.add(eh);
	}
	
	@Override
	public IntDimension getSize() {
		return size;
	}
	
    public void init() {
    	// TODO should probably be empty and handled externally
    	
//        final ZBufferState buf = new ZBufferState();
//        buf.setEnabled(true);
//        buf.setFunction(ZBufferState.TestFunction.LessThanOrEqualTo);
//        root.setRenderState(buf);
//
//        // probably doesn't matter as it's just 1 object
//        root.getSceneHints().setRenderBucketType(RenderBucketType.Opaque);
        
        isInitDone = true;
        synchronized (initDoneMonitor) {
        	this.initDoneMonitor.notify();	
		}
    }
    
    public void update(final ReadOnlyTimer timer) {
        if (Constants.stats) {
            StatCollector.update();
        }
        
        GameTaskQueueManager.getManager(this).getQueue(GameTaskQueue.UPDATE)
        	.execute();
    }
    
    public boolean renderUnto(final Renderer renderer) {    	
    	GameTaskQueueManager.getManager(this).getQueue(GameTaskQueue.RENDER)
                .execute(renderer);
        
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
        
        Thread renderThread = new Thread(this);
        renderThread.setUncaughtExceptionHandler(new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread t, Throwable e) {
				log.error(e.getMessage(), e);
				lastUncaughtException = e;
				// wake up takeShot() on error
				synchronized (shotFinishedMonitor) {
					shotFinishedMonitor.notifyAll();
				}
				for (UncaughtExceptionHandler eh : uncaughtExceptionHandlers) {
					eh.uncaughtException(t, e);
				}
				doDispose();
			}
		});
        renderThread.start();
    }
    
	@Override
	public void run() {
		_frameHandler.init();
        while (!isExitRequested) {
            synchronized (shotRequestedMonitor) {
            	while (!isShotRequested && !isExitRequested) {
            		try {
            			// this should be a new monitor for the invariant 
            			// !isShotRequested && !isExitRequested
            			// but anyway.. we just notifyAll shotRequestedMonitor in exit()
            			shotRequestedMonitor.wait(); 
					} catch (InterruptedException e) {}
            	}
			}
            // only works after the 2nd frame
            isShotRequested = false;
            _frameHandler.updateFrame();
            isShotRequested = true;
            _frameHandler.updateFrame();
        }
        this.doDispose();
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
    public BufferedImage takeShot() {
    	synchronized (shotRequestedMonitor) {
        	if (isShotRequested) {
        		throw new IllegalStateException("Another screenshot is already in progress");
        	}
    		isShotRequested = true;
			shotRequestedMonitor.notifyAll();
    	}
		synchronized (shotFinishedMonitor) {
        	while (isShotRequested) {
	    		try {
	    			shotFinishedMonitor.wait();
	    		} catch (InterruptedException e) {
	    		}
	    		if (lastUncaughtException != null) {
	    			throw new Ardor3DRenderException(lastUncaughtException);
	    		}
        	}
		}
		
    	return _screenShotExp.getLastImage();
    }

	@Override
	public void dispose() {
    	if (lastUncaughtException != null) {
    		return;
    	}
        isExitRequested = true;
    	synchronized (exitDoneMonitor) {
    		synchronized (shotRequestedMonitor) {
    			shotRequestedMonitor.notifyAll(); // this is a hack, see run()
			}
    		
    		while (!isExitDone) {
				try {
					exitDoneMonitor.wait();
				} catch (InterruptedException e) {}
    		}
		}
	}
	
	private void doDispose() {
		try {
			final CanvasRenderer canvasRenderer = canvas.getCanvasRenderer();
	        // grab the graphics context so cleanup will work out.
//			canvas.makeCurrent();
			// TODO cleanup necessary? how without context?
//	        ContextGarbageCollector.doFinalCleanup(canvasRenderer.getRenderer());
	        frame.dispose();
//	        canvas.releaseContext();
	        isExitDone = true;
	        synchronized (exitDoneMonitor) {
	        	this.exitDoneMonitor.notify();	
			}
		} catch (Exception e) {
			log.error("Error disposing canvas resources", e);
		}
	}
	
	@Override
	public PickResults doPick(Ray3 pickRay) {
		throw new UnsupportedOperationException();
	}

}
