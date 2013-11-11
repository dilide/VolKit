package de.olafklischat.volkit.controller;

import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import de.olafklischat.volkit.view.VolumeViewer;
import de.sofd.viskit.image3D.jogl.util.LinAlg;

public class VolumeCameraController {
    
    // TODO: make these parameterizable
    private static final int MOUSE_BUTTON = MouseEvent.BUTTON1;
    private static final int MOUSE_MASK = MouseEvent.BUTTON1_MASK;

    protected final VolumeViewer controlledViewer;

    public VolumeCameraController(VolumeViewer controlledViewer) {
        this.controlledViewer = controlledViewer;
        controlledViewer.addCanvasMouseListener(mouseHandler);
        controlledViewer.addCanvasMouseMotionListener(mouseHandler);
        controlledViewer.addCanvasMouseWheelListener(mouseHandler);
    }
    
    protected MouseAdapter mouseHandler = new MouseAdapter() {
        Point lastPos = null;
        
        @Override
        public void mousePressed(java.awt.event.MouseEvent e) {
            if ((e.getButton() == MOUSE_BUTTON || (e.getModifiers() & MOUSE_MASK) != 0)) {
                lastPos = new Point(e.getPoint());
            } else {
                lastPos = null;
            }
        }
        
        private void printPt(float[] p) {
            System.out.println(""+p[0] + " " + p[1] + " " + p[2]);
        }
        
        @Override
        public void mouseDragged(java.awt.event.MouseEvent e) {
            if (lastPos != null && (e.getButton() == MOUSE_BUTTON || (e.getModifiers() & MOUSE_MASK) != 0)) {
                Point pos = e.getPoint();
                float roty = ((float)pos.x - lastPos.x) / 400 * 180;
                float rotx = ((float)pos.y - lastPos.y) / 400 * 180;
                float[] deltaTransform = new float[16];
                LinAlg.fillIdentity(deltaTransform);
                LinAlg.fillRotation(deltaTransform, rotx, 1, 0, 0, deltaTransform);
                LinAlg.fillRotation(deltaTransform, roty, 0, 1, 0, deltaTransform);
                float[] w2e = controlledViewer.getWorldToEyeTransform();
                //LinAlg.fillMultiplication(viewerDeltaTransform, w2e, w2e);  //rotate around camera
                LinAlg.fillMultiplication(w2e, deltaTransform, w2e);  //rotate around world origin -- doesn't work very well
                controlledViewer.setWorldToEyeTransform(w2e);
                controlledViewer.refresh();

                lastPos = new Point(e.getPoint());
                e.consume();
            } else {
                lastPos = null;
            }
        }
        
        public void mouseReleased(MouseEvent e) {
            lastPos = null;
        }
    };
}
