package de.olafklischat.volkit.model;

import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.io.DicomInputStream;

import com.sun.opengl.util.BufferUtil;

import de.olafklischat.volkit.view.SharedContextData;
import de.sofd.viskit.model.RawImage;

public class VolumeDataSet {

    protected int xCount, yCount, zCount;
    protected List<Buffer> xyPixelPlaneBuffers = new ArrayList<Buffer>();  // invariant: depth == xyPixelPlaneBuffers.size()
    protected float xSpacingInMm, ySpacingInMm, zSpacingInMm;
    protected int pixelFormat, pixelType;

    public static class TextureRef {
        protected int texId;
        protected float preScale;
        protected float preOffset;

        public TextureRef() {
            
        }
        
        public TextureRef(int texId, float preScale, float preOffset) {
            this.texId = texId;
            this.preScale = preScale;
            this.preOffset = preOffset;
        }

        public int getTexId() {
            return texId;
        }

        /**
         * preScale/preOffset: linear transformation to be applied to texel
         * values by the shader to normalize to [0..1] range. preScale * (texel
         * value) + preOffset must transform all texel values to that range
         */
        public float getPreScale() {
            return preScale;
        }
        
        public float getPreOffset() {
            return preOffset;
        }
    }

    /**
     * Each Buffer in xyPixelPlaneBuffers consists of one
     * element of type getPixelType(), containing the luminance value
     */
    public static final int PIXEL_FORMAT_LUMINANCE = 1;

    /**
     * Each Buffer in xyPixelPlaneBuffers consists consists of three
     * elements of type getPixelType(), containing the R, G and B values
     */
    public static final int PIXEL_FORMAT_RGB = 2;

    /**
     * stored in bytes. buffers instanceof ByteBuffer
     */
    public static final int PIXEL_TYPE_UNSIGNED_BYTE = 1;

    /**
     * stored in shorts. buffers instanceof ShortBuffer
     */
    public static final int PIXEL_TYPE_SIGNED_12BIT = 2;

    /**
     * stored in shorts. buffers instanceof ShortBuffer
     */
    public static final int PIXEL_TYPE_UNSIGNED_12BIT = 3;

    /**
     * stored in shorts. buffers instanceof ShortBuffer
     */
    public static final int PIXEL_TYPE_SIGNED_16BIT = 4;

    /**
     * stored in shorts. buffers instanceof ShortBuffer
     */
    public static final int PIXEL_TYPE_UNSIGNED_16BIT = 5;

    protected VolumeDataSet() {
    }
    
    public static VolumeDataSet readFromDirectory(File dir) throws IOException {  // TODO move I/O into separate class
        VolumeDataSet result = new VolumeDataSet();
        File[] files = dir.listFiles();
        Arrays.sort(files);
        boolean metadataRead = false;
        boolean zSpacingRead = false;
        float firstSliceLocation = 0;
        result.zCount = files.length;
        int readCount = 0;
        for (File f : files) {
            if (!f.getName().toLowerCase().endsWith(".dcm")) {
                continue;
            }

            DicomObject dobj;
            DicomInputStream din = new DicomInputStream(f);
            try {
                dobj = din.readDicomObject();
            } finally {
                din.close();
            }

            if (!metadataRead) {
                int bitsAllocated = dobj.getInt(Tag.BitsAllocated);
                if (bitsAllocated <= 0) {
                    return null;
                }
                int bitsStored = dobj.getInt(Tag.BitsStored);
                if (bitsStored <= 0) {
                    return null;
                }
                boolean isSigned = (1 == dobj.getInt(Tag.PixelRepresentation));
                // TODO: fail if compressed
                // TODO: support for RGB? (at least don't misinterpret it as luminance)
                // TODO: account for endianness (Tag.HighBit)
                // TODO: maybe use static multidimensional tables instead of nested switch statements
                switch (bitsAllocated) {
                    case 8:
                        throw new IOException("8-bit DICOM images not supported for now");
                    case 16:
                        result.pixelFormat = RawImage.PIXEL_FORMAT_LUMINANCE;
                        switch (bitsStored) {
                            case 12:
                                result.pixelType = (isSigned ? PIXEL_TYPE_SIGNED_12BIT : PIXEL_TYPE_UNSIGNED_12BIT);
                                break;
                            case 16:
                                result.pixelType = (isSigned ? PIXEL_TYPE_SIGNED_16BIT : PIXEL_TYPE_UNSIGNED_16BIT);
                                break;
                            default:
                                throw new IOException("unsupported DICOM stored bit count: " + bitsStored);
                        }
                        break;
                    default:
                        throw new IOException("unsupported DICOM allocated bit count: " + bitsAllocated);
                }
                result.xCount = dobj.getInt(Tag.Columns);
                result.yCount = dobj.getInt(Tag.Rows);

                float[] rowCol;
                if (dobj.contains(Tag.PixelSpacing)) {
                    rowCol = dobj.getFloats(Tag.PixelSpacing);
                    if ((rowCol.length != 2) || (rowCol[0] <= 0) || (rowCol[1] <= 0)) {
                        throw new RuntimeException("Illegal PixelSpacing tag in DICOM metadata (2 positive real numbers expected)");
                    }
                } else if (dobj.contains(Tag.ImagerPixelSpacing)) {
                    rowCol = dobj.getFloats(Tag.ImagerPixelSpacing);
                    if ((rowCol.length != 2) || (rowCol[0] <= 0) || (rowCol[1] <= 0)) {
                        throw new RuntimeException("Illegal ImagerPixelSpacing tag in DICOM metadata (2 positive real numbers expected)");
                    }
                } else {
                    throw new IOException("DICOM metadata contained neither a PixelSpacing nor an ImagerPixelSpacing tag");
                }
                result.xSpacingInMm = rowCol[1];
                result.ySpacingInMm = rowCol[0];
                firstSliceLocation = dobj.getFloat(Tag.SliceLocation);

                metadataRead = true;
            } else if (!zSpacingRead) {
                result.zSpacingInMm = dobj.getFloat(Tag.SliceLocation) - firstSliceLocation;
                zSpacingRead = true;
            }
            Buffer b = BufferUtil.newShortBuffer(dobj.getShorts(Tag.PixelData)); // type of buffer may later depend on image metadata
            result.xyPixelPlaneBuffers.add(b);
            System.out.println("read " + readCount + "/" + result.zCount + " (" + (100 * readCount/result.zCount) + "%)");
            readCount++;
        }
        return result;
    }

    public int getXCount() {
        return xCount;
    }
    
    public int getYCount() {
        return yCount;
    }
    
    public int getZCount() {
        return zCount;
    }
    
    public float getXSpacingInMm() {
        return xSpacingInMm;
    }
    
    public float getYSpacingInMm() {
        return ySpacingInMm;
    }
    
    public float getZSpacingInMm() {
        return zSpacingInMm;
    }
    
    public float getWidthInMm() {
        return xSpacingInMm * xCount;
    }
    
    public float getHeightInMm() {
        return ySpacingInMm * yCount;
    }
    
    public float getDepthInMm() {
        return zSpacingInMm * zCount;
    }
    

    public TextureRef bindTexture(int texUnit, GL gl1, SharedContextData scd) {
        GL2 gl = gl1.getGL2();
        final String sharedTexIdKey = "VolumeDataSetTex" + hashCode();
        TextureRef result = (TextureRef) scd.getAttribute(sharedTexIdKey);
        if (result == null) {
            result = new TextureRef();
            int[] tmp = new int[1];
            gl.glGenTextures(1, tmp, 0);
            result.texId = tmp[0];
            scd.setAttribute(sharedTexIdKey, result);

            gl.glBindTexture(GL2.GL_TEXTURE_3D, result.getTexId());

            int glInternalFormat, glPixelFormat, glPixelType;

            // TODO: store the GL IDs in the VolumeDataSet directly
            if (pixelFormat == PIXEL_FORMAT_LUMINANCE && pixelType == PIXEL_TYPE_SIGNED_16BIT) {
                glPixelFormat = GL.GL_LUMINANCE;
                glPixelType = GL.GL_SHORT;
                glInternalFormat = GL2.GL_LUMINANCE16F;
                result.preScale = 0.5F;
                result.preOffset = 0.5F;
            } else if (pixelFormat == PIXEL_FORMAT_LUMINANCE && pixelType == PIXEL_TYPE_UNSIGNED_16BIT) {
                glPixelFormat = GL.GL_LUMINANCE;
                glPixelType = GL.GL_UNSIGNED_SHORT;
                glInternalFormat = GL2.GL_LUMINANCE16F; // GL_*_SNORM result in GL_INVALID_ENUM and all-white texels on tack (GeForce 8600 GT/nvidia 190.42)
                result.preScale = 1.0F;
                result.preOffset = 0.0F;
            } else if (pixelFormat == PIXEL_FORMAT_LUMINANCE && pixelType == PIXEL_TYPE_UNSIGNED_12BIT) {
                glPixelFormat = GL.GL_LUMINANCE;
                glPixelType = GL.GL_UNSIGNED_SHORT;
                glInternalFormat = GL2.GL_LUMINANCE16; // NOT GL_LUMINANCE12 b/c pixelType is 16-bit and we'd thus lose precision
                result.preScale = (float) (1<<16) / (1<<12);
                result.preOffset = 0.0F;
            } else {
                throw new RuntimeException("this DICOM image format is not supported for now");
            }

            gl.glTexImage3D(GL2.GL_TEXTURE_3D,    //target
                            0,                    //level
                            glInternalFormat,     //internalFormat
                            xCount,               //width
                            yCount,               //height
                            zCount,               //depth
                            0,                    //border
                            glPixelFormat,        //format
                            glPixelType,          //type
                            null);                //data

            for (int z = 0; z < zCount; z++) {
                Buffer planeBuffer = xyPixelPlaneBuffers.get(z);
                gl.glTexSubImage3D(GL2.GL_TEXTURE_3D, //target
                                   0,  //level
                                   0,  //xoffset
                                   0,  //yoffset
                                   z,  //zoffset
                                   xCount,
                                   yCount,
                                   1,
                                   glPixelFormat,
                                   glPixelType,
                                   planeBuffer);
            }
            gl.glTexParameteri(GL2.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL2.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL2.GL_TEXTURE_3D, GL2.GL_TEXTURE_WRAP_S, GL2.GL_CLAMP );
            gl.glTexParameteri(GL2.GL_TEXTURE_3D, GL2.GL_TEXTURE_WRAP_T, GL2.GL_CLAMP );
            gl.glTexParameteri(GL2.GL_TEXTURE_3D, GL2.GL_TEXTURE_WRAP_R, GL2.GL_CLAMP );
        }
        gl.glEnable(GL2.GL_TEXTURE_3D);
        gl.glActiveTexture(texUnit);
        gl.glBindTexture(GL2.GL_TEXTURE_3D, result.getTexId());
        return result;
    }
    
    
}
